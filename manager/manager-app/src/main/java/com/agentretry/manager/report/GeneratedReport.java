/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import org.springframework.http.MediaType;

public record GeneratedReport(
        byte[] content,
        String filename,
        MediaType mediaType
) {
}
