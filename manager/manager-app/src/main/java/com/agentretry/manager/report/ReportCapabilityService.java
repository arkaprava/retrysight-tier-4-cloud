/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.report.dto.ReportOptionsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportCapabilityService {

    private static final List<DatasetDefinition> DATASET_DEFINITIONS = List.of(
            new DatasetDefinition(
                    ReportDataset.EXECUTIVE_SUMMARY,
                    "Executive Summary",
                    "Dashboard snapshot with task counts, retry metrics, and token costs",
                    1,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.JSON),
                    Set.of("from", "to"),
                    Set.of(),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.TASK_LIST,
                    "Task List",
                    "Export filtered tasks with status, retries, and collector info",
                    1,
                    EnumSet.of(ReportFormat.CSV),
                    Set.of("from", "to"),
                    Set.of("sourceTool", "agentId"),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.TASK_DETAIL,
                    "Task Detail",
                    "Full task timeline with events and token usage for a single task",
                    1,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.JSON),
                    Set.of("taskId"),
                    Set.of("from", "to"),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.COLLECTOR_ACTIVITY,
                    "Collector Activity",
                    "Per-collector heartbeat and task activity summary",
                    2,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.PDF),
                    Set.of("from", "to"),
                    Set.of("agentId"),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.RETRY_WASTE,
                    "Retry Waste",
                    "Token cost attributed to retry attempts over time",
                    2,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.JSON),
                    Set.of("from", "to"),
                    Set.of("sourceTool"),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.RETRY_BY_MODEL,
                    "Retry by Model",
                    "Retry counts, costs, and waste ratios grouped by underlying LLM model",
                    2,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.JSON),
                    Set.of("from", "to"),
                    Set.of("sourceTool"),
                    true
            ),
            new DatasetDefinition(
                    ReportDataset.TOKEN_COST,
                    "Token Cost Analysis",
                    "Custom column picker and cost comparisons across tools",
                    4,
                    EnumSet.of(ReportFormat.CSV, ReportFormat.JSON),
                    Set.of("from", "to"),
                    Set.of("sourceTool", "agentId"),
                    true
            )
    );

    private static final List<ReportOptionsResponse.PhaseOption> PHASE_METADATA = List.of(
            new ReportOptionsResponse.PhaseOption(1, "Phase 1", "CSV/JSON exports — tasks, detail, dashboard snapshot", true),
            new ReportOptionsResponse.PhaseOption(2, "Phase 2", "PDF executive reports, task detail PDFs, report history", true),
            new ReportOptionsResponse.PhaseOption(3, "Phase 3", "Scheduled reports and email delivery", true),
            new ReportOptionsResponse.PhaseOption(4, "Phase 4", "Custom column picker, comparisons, integrations", true),
            new ReportOptionsResponse.PhaseOption(5, "Phase 5", "Cost budgets and alerts", true),
            new ReportOptionsResponse.PhaseOption(6, "Phase 6", "Billing reconciliation and enterprise features", true)
    );

    private final ReportProperties properties;

    public ReportCapabilityService(ReportProperties properties) {
        this.properties = properties;
    }

    public ReportOptionsResponse buildOptions() {
        List<ReportOptionsResponse.PhaseOption> phases = PHASE_METADATA.stream()
                .map(phase -> new ReportOptionsResponse.PhaseOption(
                        phase.phase(),
                        phase.label(),
                        phase.description(),
                        properties.getEnabledPhases().contains(phase.phase())
                ))
                .toList();

        List<ReportOptionsResponse.DatasetOption> datasets = new ArrayList<>();
        for (DatasetDefinition definition : DATASET_DEFINITIONS) {
            if (!properties.getEnabledDatasets().contains(definition.id())) {
                continue;
            }

            boolean phaseEnabled = properties.getEnabledPhases().contains(definition.phase());
            boolean available = definition.implemented() && phaseEnabled;
            Integer requiresPhase = available ? null : definition.phase();

            List<String> formats = definition.supportedFormats().stream()
                    .filter(format -> isFormatVisible(format, definition.phase()))
                    .map(Enum::name)
                    .sorted()
                    .toList();

            datasets.add(new ReportOptionsResponse.DatasetOption(
                    definition.id().name(),
                    definition.label(),
                    definition.description(),
                    definition.phase(),
                    available,
                    requiresPhase,
                    formats,
                    definition.requiredFilters().stream().sorted().toList(),
                    definition.optionalFilters().stream().sorted().toList()
            ));
        }

        List<ReportOptionsResponse.FormatOption> formats = List.of(
                formatOption(ReportFormat.CSV, 1, "CSV", "Comma-separated values spreadsheet"),
                formatOption(ReportFormat.JSON, 1, "JSON", "Structured JSON for integrations"),
                formatOption(ReportFormat.PDF, 2, "PDF", "Formatted executive PDF report")
        );

        return new ReportOptionsResponse(
                properties.getEnabledPhases().stream().sorted().toList(),
                properties.getMaxDateRangeDays(),
                phases,
                datasets,
                formats
        );
    }

    public DatasetDefinition requireDataset(ReportDataset dataset) {
        return DATASET_DEFINITIONS.stream()
                .filter(definition -> definition.id() == dataset)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown dataset: " + dataset));
    }

    public void validate(ReportGenerateRequest request) {
        DatasetDefinition definition = requireDataset(request.dataset());

        if (!properties.getEnabledDatasets().contains(request.dataset())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dataset is disabled by administrator: " + request.dataset());
        }

        if (!properties.getEnabledPhases().contains(definition.phase())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Requires phase " + definition.phase() + " which is disabled by administrator"
            );
        }

        if (!definition.implemented()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    definition.label() + " is planned for phase " + definition.phase() + " and is not yet available"
            );
        }

        if (!properties.getEnabledFormats().contains(request.format())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Format is disabled by administrator: " + request.format());
        }

        if (!definition.supportedFormats().contains(request.format())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    request.format() + " is not supported for dataset " + request.dataset()
            );
        }

        int formatPhase = formatPhase(request.format());
        if (!properties.getEnabledPhases().contains(formatPhase)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    request.format() + " requires phase " + formatPhase + " which is disabled by administrator"
            );
        }

        validateFilters(definition, request);
        validateDateRange(definition, request);
    }

    private void validateFilters(DatasetDefinition definition, ReportGenerateRequest request) {
        if (definition.requiredFilters().contains("taskId")) {
            UUID taskId = request.filters() != null ? request.filters().taskId() : null;
            if (taskId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId filter is required for " + request.dataset());
            }
        }

        if (definition.requiredFilters().contains("from") && request.from() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from date is required for " + request.dataset());
        }

        if (definition.requiredFilters().contains("to") && request.to() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to date is required for " + request.dataset());
        }
    }

    private void validateDateRange(DatasetDefinition definition, ReportGenerateRequest request) {
        if (!definition.requiredFilters().contains("from") && request.from() == null && request.to() == null) {
            return;
        }

        Instant from = request.from() != null ? request.from() : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = request.to() != null ? request.to() : Instant.now();

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from date must be before to date");
        }

        long days = ChronoUnit.DAYS.between(from.truncatedTo(ChronoUnit.DAYS), to.truncatedTo(ChronoUnit.DAYS));
        if (days > properties.getMaxDateRangeDays()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date range exceeds maximum of " + properties.getMaxDateRangeDays() + " days"
            );
        }
    }

    private ReportOptionsResponse.FormatOption formatOption(
            ReportFormat format,
            int phase,
            String label,
            String description
    ) {
        boolean phaseEnabled = properties.getEnabledPhases().contains(phase);
        boolean formatEnabled = properties.getEnabledFormats().contains(format);
        boolean available = phaseEnabled && formatEnabled;
        Integer requiresPhase = available ? null : phase;
        return new ReportOptionsResponse.FormatOption(format.name(), label, description, phase, available, requiresPhase);
    }

    private boolean isFormatVisible(ReportFormat format, int datasetPhase) {
        int requiredPhase = Math.max(formatPhase(format), datasetPhase);
        return properties.getEnabledPhases().contains(requiredPhase)
                && properties.getEnabledFormats().contains(format);
    }

    private int formatPhase(ReportFormat format) {
        return switch (format) {
            case CSV, JSON -> 1;
            case PDF -> 2;
        };
    }
}
