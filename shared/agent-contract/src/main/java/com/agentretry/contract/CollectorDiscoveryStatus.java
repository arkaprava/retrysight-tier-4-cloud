/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

/** Filesystem discovery outcome for a configured collector type. */
public enum CollectorDiscoveryStatus {
    FOUND,
    STALE,
    MISSING,
    SKIPPED,
    CONFIGURED
}
