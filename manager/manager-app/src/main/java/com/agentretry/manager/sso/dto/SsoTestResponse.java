/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso.dto;

public record SsoTestResponse(
        boolean success,
        String message
) {
}
