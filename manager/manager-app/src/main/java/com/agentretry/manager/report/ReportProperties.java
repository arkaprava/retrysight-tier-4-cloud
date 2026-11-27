/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "manager.reports")
public class ReportProperties {

    private List<Integer> enabledPhases = new ArrayList<>(List.of(1, 2, 3, 4));
    private List<ReportFormat> enabledFormats = new ArrayList<>(List.of(ReportFormat.CSV, ReportFormat.JSON, ReportFormat.PDF));
    private List<ReportDataset> enabledDatasets = new ArrayList<>(List.of(
            ReportDataset.EXECUTIVE_SUMMARY,
            ReportDataset.TASK_LIST,
            ReportDataset.TASK_DETAIL,
            ReportDataset.COLLECTOR_ACTIVITY,
            ReportDataset.RETRY_WASTE,
            ReportDataset.RETRY_BY_MODEL,
            ReportDataset.TOKEN_COST
    ));
    private int maxDateRangeDays = 90;

    public List<Integer> getEnabledPhases() {
        return enabledPhases;
    }

    public void setEnabledPhases(List<Integer> enabledPhases) {
        this.enabledPhases = enabledPhases;
    }

    public List<ReportFormat> getEnabledFormats() {
        return enabledFormats;
    }

    public void setEnabledFormats(List<ReportFormat> enabledFormats) {
        this.enabledFormats = enabledFormats;
    }

    public List<ReportDataset> getEnabledDatasets() {
        return enabledDatasets;
    }

    public void setEnabledDatasets(List<ReportDataset> enabledDatasets) {
        this.enabledDatasets = enabledDatasets;
    }

    public int getMaxDateRangeDays() {
        return maxDateRangeDays;
    }

    public void setMaxDateRangeDays(int maxDateRangeDays) {
        this.maxDateRangeDays = maxDateRangeDays;
    }
}
