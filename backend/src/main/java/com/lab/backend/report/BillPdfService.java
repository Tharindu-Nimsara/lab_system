package com.lab.backend.report;

import com.lab.backend.billing.BillingService;
import com.lab.backend.billing.Invoice;
import com.lab.backend.patient.Patient;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.lab.backend.report.PdfSupport.*;

/**
 * Dual-copy bill: patient copy plus a lab worksheet copy with blank result
 * lines per test (per implementation plan §5.1).
 */
@Service
@RequiredArgsConstructor
public class BillPdfService {

    private final LabInfo lab;

    /** Which copies to include when rendering a bill. */
    public enum Copy { PATIENT, WORKSHEET, BOTH }

    /** Backwards-compatible entry point — renders both copies. */
    public byte[] render(BillingService.InvoiceDetail detail, Patient patient) {
        return render(detail, patient, Copy.BOTH);
    }

    /**
     * Render the requested copies. Each requested copy starts on its own page so
     * reception can print the patient bill and the lab worksheet separately.
     */
    public byte[] render(BillingService.InvoiceDetail detail, Patient patient, Copy which) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 24, 24, 20, 20);
        PdfWriter.getInstance(doc, out);
        doc.open();
        boolean first = true;
        if (which == Copy.PATIENT || which == Copy.BOTH) {
            copy(doc, detail, patient, "INVOICE — PATIENT COPY", false);
            first = false;
        }
        if (which == Copy.WORKSHEET || which == Copy.BOTH) {
            if (!first) doc.newPage();
            copy(doc, detail, patient, "LAB WORKSHEET COPY", true);
        }
        doc.close();
        return out.toByteArray();
    }

    private void copy(Document doc, BillingService.InvoiceDetail detail, Patient patient,
                      String title, boolean worksheet) {
        Invoice inv = detail.invoice();
        letterhead(doc, lab, title);

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.addCell(borderless("Invoice: " + inv.getInvoiceNo()));
        meta.addCell(borderless("Date: " + (inv.getCreatedAt() == null ? "" :
                inv.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))));
        meta.addCell(borderless("Patient: " + patient.getName() + " (" + patient.getPatientNo() + ")"));
        meta.addCell(borderless("Phone: " + patient.getPhone()));
        meta.setSpacingAfter(8);
        doc.add(meta);

        boolean anyOutsourced = detail.items().stream()
                .anyMatch(BillingService.InvoiceItemDetail::outsourced);

        PdfPTable table = new PdfPTable(worksheet ? new float[]{2, 4, 6} : new float[]{2, 5, 3, 3});
        table.setWidthPercentage(100);
        table.addCell(headerCell("Code"));
        table.addCell(headerCell("Test"));
        if (!worksheet) {
            table.addCell(headerCell("Lab"));
        }
        table.addCell(headerCell(worksheet ? "Result (handwritten)" : "Price"));
        for (BillingService.InvoiceItemDetail item : detail.items()) {
            table.addCell(cell(item.testCode(), BODY));
            table.addCell(cell(item.testName(), BODY));
            if (!worksheet) {
                // Outsourced labs are marked with * (see footnote) so they stand out.
                String labText = item.labName() == null ? "—"
                        : item.labName() + (item.outsourced() ? " *" : "");
                table.addCell(cell(labText, item.outsourced() ? BODY_BOLD : BODY));
            }
            table.addCell(cell(worksheet ? " " : item.priceAtSale().toPlainString(), BODY));
        }
        table.setSpacingAfter(4);
        doc.add(table);

        if (!worksheet && anyOutsourced) {
            Paragraph note = new Paragraph(
                    "* Outsourced test — performed by an external partner lab.", SMALL);
            note.setSpacingAfter(6);
            doc.add(note);
        }

        if (!worksheet) {
            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(45);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.addCell(borderless("Subtotal"));
            totals.addCell(borderlessRight(inv.getSubtotal().toPlainString()));
            totals.addCell(borderless("Discount"));
            totals.addCell(borderlessRight(inv.getDiscount().toPlainString()));
            totals.addCell(borderless("Total"));
            totals.addCell(borderlessRight(inv.getTotal().toPlainString()));
            totals.addCell(borderless("Paid (" + inv.getPaymentMethod() + ")"));
            totals.addCell(borderlessRight(inv.getAmountPaid().toPlainString()));
            if (inv.getBalance().signum() > 0) {
                totals.addCell(borderless("Balance due"));
                totals.addCell(borderlessRight(inv.getBalance().toPlainString()));
            }
            doc.add(totals);
        }

        Paragraph footer = new Paragraph(
                worksheet ? "Sample collected by: ______________    Entered by: ______________"
                          : "Thank you. Please retain this bill to collect your report.",
                SMALL);
        footer.setSpacingBefore(12);
        doc.add(footer);
    }

    private com.lowagie.text.pdf.PdfPCell borderless(String text) {
        var c = cell(text, BODY);
        c.setBorder(0);
        return c;
    }

    private com.lowagie.text.pdf.PdfPCell borderlessRight(String text) {
        var c = borderless(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }
}
