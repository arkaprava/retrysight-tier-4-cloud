/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

public enum TaskEventType {
    EDIT,
    TEST_FAIL,
    DIFF_REJECTED,
    DIFF_ACCEPTED,
    SUBAGENT,
    TOKEN_USAGE,
    SESSION_START,
    SESSION_END,
    COMPACTION
}
