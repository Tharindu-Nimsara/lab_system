package com.lab.backend.report;

import com.lab.backend.billing.Invoice;
import com.lab.backend.patient.Patient;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.lab.backend.report.PdfSupport.*;

/** Lab report: letterhead, patient block, results with reference ranges and flags. */
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private final LabInfo lab;

    public record ResultRow(String label, String value, String unit, String refRange, String flag) {}

    public record TestBlock(String testName, String testCode, boolean verified, List<ResultRow> rows) {}

    public byte[] render(Invoice invoice, Patient patient, List<TestBlock> blocks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();
        letterhead(doc, lab, "LABORATORY REPORT");

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.addCell(cell("Patient: " + patient.getName() + " (" + patient.getPatientNo() + ")", BODY));
        meta.addCell(cell("Invoice: " + invoice.getInvoiceNo(), BODY));
        meta.addCell(cell(demographics(patient), BODY));
        meta.addCell(cell("Billed: " + (invoice.getCreatedAt() == null ? "—" :
                invoice.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))), BODY));
        meta.setSpacingAfter(10);
        doc.add(meta);

        for (TestBlock block : blocks) {
            Paragraph heading = new Paragraph(
                    block.testName() + " (" + block.testCode() + ")"
                            + (block.verified() ? "" : " — UNVERIFIED"), H2);
            heading.setSpacingBefore(6);
            heading.setSpacingAfter(4);
            doc.add(heading);

            PdfPTable table = new PdfPTable(new float[]{4, 2, 2, 3, 1});
            table.setWidthPercentage(100);
            table.addCell(headerCell("Investigation"));
            table.addCell(headerCell("Result"));
            table.addCell(headerCell("Unit"));
            table.addCell(headerCell("Reference Range"));
            table.addCell(headerCell("Flag"));
            for (ResultRow row : block.rows()) {
                table.addCell(cell(row.label(), BODY));
                table.addCell(cell(row.value(), row.flag() == null ? BODY : BODY_BOLD));
                table.addCell(cell(row.unit(), BODY));
                table.addCell(cell(row.refRange(), BODY));
                table.addCell(cell(row.flag() == null ? "" : row.flag(),
                        row.flag() == null ? BODY : FLAG));
            }
            doc.add(table);
        }

        Paragraph sig = new Paragraph(
                "\n\n________________________\nMedical Laboratory Technologist", BODY);
        sig.setSpacingBefore(24);
        doc.add(sig);
        doc.close();
        return out.toByteArray();
    }

    private String demographics(Patient p) {
        StringBuilder sb = new StringBuilder();
        if (p.getGender() != null) sb.append(p.getGender());
        if (p.getDob() != null) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append("DOB ").append(p.getDob());
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }
}
