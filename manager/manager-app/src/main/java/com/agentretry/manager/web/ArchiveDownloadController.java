/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.web;

import com.agentretry.manager.security.SecadminGuard;
import com.agentretry.manager.service.ArchiveRestoreService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/archive")
public class ArchiveDownloadController {

    private final ArchiveRestoreService archiveRestoreService;

    public ArchiveDownloadController(ArchiveRestoreService archiveRestoreService) {
        this.archiveRestoreService = archiveRestoreService;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(Authentication authentication, @RequestParam String path) throws Exception {
        SecadminGuard.requireSecadmin(authentication);
        byte[] body = archiveRestoreService.downloadCold(path);
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
