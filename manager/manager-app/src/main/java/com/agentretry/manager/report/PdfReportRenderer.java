/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Component
public class PdfReportRenderer {

    public byte[] render(String title, List<String> headers, List<List<String>> rows) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            document.add(new Paragraph(title != null ? title : "Report", titleFont));
            document.add(new Paragraph(" "));
            if (headers == null || headers.isEmpty()) {
                document.close();
                return out.toByteArray();
            }
            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header != null ? header : "", headerFont));
                cell.setBackgroundColor(new Color(230, 230, 230));
                table.addCell(cell);
            }
            if (rows != null) {
                for (List<String> row : rows) {
                    for (int i = 0; i < headers.size(); i++) {
                        String value = i < row.size() && row.get(i) != null ? row.get(i) : "";
                        table.addCell(new Phrase(value, cellFont));
                    }
                }
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render PDF report", ex);
        }
    }
}
