package com.lab.backend.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;

import java.awt.Color;

/**
 * Shared fonts and helpers for the OpenPDF-based documents. This is the
 * placeholder print layer; swap for JasperReports templates once real bill and
 * report samples are collected from the lab (implementation plan §12.3).
 */
final class PdfSupport {

    static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);
    static final Font FLAG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.RED);

    private PdfSupport() {
    }

    static void letterhead(Document doc, LabInfo lab, String docTitle) {
        Paragraph name = new Paragraph(lab.getName(), TITLE);
        name.setAlignment(Element.ALIGN_CENTER);
        doc.add(name);
        Paragraph addr = new Paragraph(lab.getAddress() + " · Tel: " + lab.getPhone(), SMALL);
        addr.setAlignment(Element.ALIGN_CENTER);
        addr.setSpacingAfter(6);
        doc.add(addr);
        Paragraph title = new Paragraph(docTitle, H2);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        doc.add(title);
    }

    static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "—" : text, font));
        cell.setPadding(4);
        return cell;
    }

    static PdfPCell headerCell(String text) {
        PdfPCell cell = cell(text, BODY_BOLD);
        cell.setBackgroundColor(new Color(235, 235, 235));
        return cell;
    }
}
