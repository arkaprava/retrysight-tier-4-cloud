/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.report.dto.ReportGenerateRequest;
import com.agentretry.manager.report.dto.ReportOptionsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportCapabilityService capabilityService;
    private final ReportService reportService;
    private final FeatureGateService featureGateService;

    public ReportController(
            ReportCapabilityService capabilityService,
            ReportService reportService,
            FeatureGateService featureGateService
    ) {
        this.capabilityService = capabilityService;
        this.reportService = reportService;
        this.featureGateService = featureGateService;
    }

    @GetMapping("/options")
    public ReportOptionsResponse options() {
        requireReportsEnabled();
        return capabilityService.buildOptions();
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@Valid @RequestBody ReportGenerateRequest request) {
        requireReportsEnabled();
        capabilityService.validate(request);
        GeneratedReport report = reportService.generate(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.filename() + "\"")
                .contentType(report.mediaType())
                .body(report.content());
    }

    private void requireReportsEnabled() {
        if (!featureGateService.isReportsEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reports are not enabled for this installation");
        }
    }
}
