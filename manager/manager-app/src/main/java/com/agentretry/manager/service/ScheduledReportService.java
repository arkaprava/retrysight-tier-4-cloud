/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.ScheduledReportEntity;
import com.agentretry.manager.graphql.ScheduledReport;
import com.agentretry.manager.report.ReportDataset;
import com.agentretry.manager.report.ReportFormat;
import com.agentretry.manager.report.ReportService;
import com.agentretry.manager.report.dto.ReportFilters;
import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.repository.ScheduledReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduledReportService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledReportService.class);

    private final ScheduledReportRepository repository;
    private final ReportService reportService;

    public ScheduledReportService(ScheduledReportRepository repository, ReportService reportService) {
        this.repository = repository;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public List<ScheduledReport> list() {
        return repository.findAll().stream().map(this::toGraphql).toList();
    }

    @Transactional
    public ScheduledReport create(String name, String dataset, String format, String cronExpression, String emailTo) {
        try {
            ReportDataset.valueOf(dataset);
            ReportFormat.valueOf(format);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataset or format");
        }
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression");
        }
        Instant now = Instant.now();
        ScheduledReportEntity entity = new ScheduledReportEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name.trim());
        entity.setDataset(dataset);
        entity.setFormat(format);
        entity.setCronExpression(cronExpression);
        entity.setEmailTo(emailTo);
        entity.setActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        return toGraphql(entity);
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled report not found");
        }
        repository.deleteById(id);
        return true;
    }

    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void runDueReports() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        for (ScheduledReportEntity entity : repository.findByActiveTrue()) {
            try {
                CronExpression cron = CronExpression.parse(entity.getCronExpression());
                Instant last = entity.getLastRunAt() != null
                        ? entity.getLastRunAt()
                        : Instant.now().minus(1, ChronoUnit.DAYS);
                ZonedDateTime next = cron.next(last.atZone(ZoneOffset.UTC));
                if (next == null || next.isAfter(now)) {
                    continue;
                }
                ReportGenerateRequest request = new ReportGenerateRequest(
                        ReportDataset.valueOf(entity.getDataset()),
                        ReportFormat.valueOf(entity.getFormat()),
                        Instant.now().minus(7, ChronoUnit.DAYS),
                        Instant.now(),
                        new ReportFilters(null, null, null),
                        null
                );
                var generated = reportService.generate(request);
                log.info("Scheduled report {} generated {} bytes ({})",
                        entity.getName(), generated.content().length, entity.getEmailTo());
                entity.setLastRunAt(Instant.now());
                entity.setUpdatedAt(Instant.now());
                repository.save(entity);
            } catch (Exception ex) {
                log.warn("Scheduled report {} failed: {}", entity.getId(), ex.getMessage());
            }
        }
    }

    private ScheduledReport toGraphql(ScheduledReportEntity entity) {
        return new ScheduledReport(
                entity.getId(),
                entity.getName(),
                entity.getDataset(),
                entity.getFormat(),
                entity.getCronExpression(),
                entity.getEmailTo(),
                entity.isActive(),
                entity.getLastRunAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
