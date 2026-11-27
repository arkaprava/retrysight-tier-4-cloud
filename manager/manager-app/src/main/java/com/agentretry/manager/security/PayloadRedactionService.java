/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.PlatformProperties;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PayloadRedactionService {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._-]{8,}");
    private static final Pattern API_KEY = Pattern.compile("(?i)(api[_-]?key|x-api-key|sk-[A-Za-z0-9]{8,})[^\\s]{0,40}");
    private static final Pattern AWS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}");

    private final PlatformProperties platformProperties;

    public PayloadRedactionService(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (platformProperties.getPrivacy().isStoreFullPrompts()) {
            return value;
        }
        String redacted = value;
        redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        redacted = BEARER.matcher(redacted).replaceAll("$1[REDACTED_TOKEN]");
        redacted = API_KEY.matcher(redacted).replaceAll("[REDACTED_SECRET]");
        redacted = AWS_KEY.matcher(redacted).replaceAll("[REDACTED_AWS_KEY]");
        return redacted;
    }
}
