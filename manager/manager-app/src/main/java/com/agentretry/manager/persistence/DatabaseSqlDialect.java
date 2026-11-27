/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.persistence;

import org.springframework.stereotype.Component;

/**
 * Portable SQL fragments for analytics native queries.
 * Prefer ANSI constructs so enterprises can point the manager at their own RDBMS
 * (PostgreSQL, MySQL, SQL Server, Oracle, H2) without Postgres-only functions.
 */
@Component
public class DatabaseSqlDialect {

    /** Rollup date column (renamed from reserved {@code day} for cross-DB safety). */
    public static final String ROLLUP_DAY = "rollup_day";

    /**
     * Truncate a timestamp expression to a calendar date.
     * Uses {@code CAST(... AS DATE)} — portable on PostgreSQL, H2, MySQL, SQL Server, Oracle.
     */
    public String castToDate(String timestampExpression) {
        return "CAST(" + timestampExpression + " AS DATE)";
    }

    public String rollupDayColumn() {
        return ROLLUP_DAY;
    }
}
