/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public final class CsvReportRenderer {

    private CsvReportRenderer() {
    }

    public static byte[] render(List<String> headers, List<List<String>> rows) {
        StringWriter writer = new StringWriter();
        writeRow(writer, headers);
        for (List<String> row : rows) {
            writeRow(writer, row);
        }
        return writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeRow(StringWriter writer, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(i)));
        }
        writer.write('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
