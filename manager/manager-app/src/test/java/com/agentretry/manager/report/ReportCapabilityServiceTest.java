/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.report.dto.ReportFilters;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCapabilityServiceTest {

    @Test
    void buildOptionsMarksPhaseOneDatasetsAvailable() {
        ReportCapabilityService service = new ReportCapabilityService(defaultProperties());

        var options = service.buildOptions();

        var executive = options.datasets().stream()
                .filter(dataset -> "EXECUTIVE_SUMMARY".equals(dataset.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(executive.available());
        assertEquals(List.of("CSV", "JSON"), executive.formats());
    }

    @Test
    void buildOptionsMarksCollectorActivityAvailableWhenPhaseEnabled() {
        ReportCapabilityService service = new ReportCapabilityService(defaultProperties());

        var options = service.buildOptions();

        var collectorActivity = options.datasets().stream()
                .filter(dataset -> "COLLECTOR_ACTIVITY".equals(dataset.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(collectorActivity.available());
        assertNull(collectorActivity.requiresPhase());
    }

    @Test
    void validateRejectsDisabledPhaseDataset() {
        ReportProperties properties = defaultProperties();
        properties.setEnabledPhases(List.of(1));
        ReportCapabilityService service = new ReportCapabilityService(properties);

        ReportGenerateRequest request = new ReportGenerateRequest(
                ReportDataset.COLLECTOR_ACTIVITY,
                ReportFormat.CSV,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now(),
                null,
                2
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.validate(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void validateRequiresTaskIdForTaskDetail() {
        ReportCapabilityService service = new ReportCapabilityService(defaultProperties());

        ReportGenerateRequest request = new ReportGenerateRequest(
                ReportDataset.TASK_DETAIL,
                ReportFormat.JSON,
                null,
                null,
                new ReportFilters(null, null, null),
                1
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.validate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateRejectsDateRangeBeyondLimit() {
        ReportProperties properties = defaultProperties();
        properties.setMaxDateRangeDays(7);
        ReportCapabilityService service = new ReportCapabilityService(properties);

        ReportGenerateRequest request = new ReportGenerateRequest(
                ReportDataset.EXECUTIVE_SUMMARY,
                ReportFormat.JSON,
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now(),
                null,
                1
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.validate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private ReportProperties defaultProperties() {
        ReportProperties properties = new ReportProperties();
        properties.setEnabledPhases(List.of(1, 2, 3, 4));
        properties.setEnabledFormats(List.of(ReportFormat.CSV, ReportFormat.JSON, ReportFormat.PDF));
        properties.setEnabledDatasets(List.of(
                ReportDataset.EXECUTIVE_SUMMARY,
                ReportDataset.TASK_LIST,
                ReportDataset.TASK_DETAIL,
                ReportDataset.COLLECTOR_ACTIVITY,
                ReportDataset.RETRY_WASTE,
                ReportDataset.RETRY_BY_MODEL,
                ReportDataset.TOKEN_COST
        ));
        properties.setMaxDateRangeDays(90);
        return properties;
    }
}
