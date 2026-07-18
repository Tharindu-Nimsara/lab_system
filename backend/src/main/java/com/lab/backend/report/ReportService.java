package com.lab.backend.report;

import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.billing.Invoice;
import com.lab.backend.billing.InvoiceItem;
import com.lab.backend.billing.InvoiceItemRepository;
import com.lab.backend.billing.InvoiceRepository;
import com.lab.backend.billing.LabOrder;
import com.lab.backend.billing.OrderRepository;
import com.lab.backend.billing.OrderStatus;
import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.catalog.TestTemplateRepository;
import com.lab.backend.common.Json;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import com.lab.backend.patient.Patient;
import com.lab.backend.patient.PatientRepository;
import com.lab.backend.results.Result;
import com.lab.backend.results.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final OrderRepository orders;
    private final LabTestRepository tests;
    private final TestTemplateRepository templates;
    private final ResultRepository results;
    private final PatientRepository patients;
    private final ReportRepository reports;
    private final ReportPdfService pdf;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    @Value("${app.storage.reports-dir}")
    private String reportsDir;

    public record ReportStatus(Long invoiceId, boolean finalized, OffsetDateTime finalizedAt) {}

    @Transactional
    public ReportStatus finalize(Long invoiceId, String ip) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));
        if (!"PAID".equals(invoice.getStatus())) {
            throw new IllegalStateException("Cannot finalize a report for a void invoice");
        }
        Patient patient = patients.findById(invoice.getPatientId())
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        List<InvoiceItem> invItems = items.findByInvoiceId(invoiceId);
        List<ReportPdfService.TestBlock> blocks = new ArrayList<>();
        for (InvoiceItem item : invItems) {
            LabOrder order = orders.findByInvoiceItemIdIn(List.of(item.getId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Order missing for item " + item.getId()));
            if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.VERIFIED) {
                throw new IllegalStateException(
                        "All results must be entered before finalizing (order " + order.getId()
                                + " is " + order.getStatus() + ")");
            }
            Result result = results.findByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Result missing for order " + order.getId()));
            LabTest test = tests.findById(item.getTestId())
                    .orElseThrow(() -> new NotFoundException("Test missing"));
            JsonNode fields = templates.findById(test.getTemplateId())
                    .map(t -> Json.parse(t.getFields()))
                    .orElseThrow(() -> new NotFoundException("Template missing"));

            blocks.add(new ReportPdfService.TestBlock(
                    test.getName(), test.getCode(),
                    order.getStatus() == OrderStatus.VERIFIED,
                    rows(fields, Json.parse(result.getResultValues()), Json.parse(result.getFlags()))));
        }
        if (blocks.isEmpty()) {
            throw new IllegalStateException("Invoice has no tests to report");
        }

        byte[] bytes = pdf.render(invoice, patient, blocks);
        Path path = Path.of(reportsDir, "report-" + invoice.getInvoiceNo() + ".pdf");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store report PDF", e);
        }

        Report report = reports.findByInvoiceId(invoiceId).orElseGet(Report::new);
        report.setInvoiceId(invoiceId);
        report.setPatientId(patient.getId());
        report.setPdfPath(path.toString());
        report.setFinalizedAt(OffsetDateTime.now());
        reports.save(report);

        audit.record(currentUser.require().getId(), "FINALIZE", "Report", invoiceId, null, ip);
        return new ReportStatus(invoiceId, true, report.getFinalizedAt());
    }

    private List<ReportPdfService.ResultRow> rows(JsonNode fields, JsonNode values, JsonNode flags) {
        List<ReportPdfService.ResultRow> out = new ArrayList<>();
        if (fields == null || !fields.isArray()) {
            return out;
        }
        for (JsonNode field : fields) {
            if (!field.hasNonNull("key")) {
                continue;
            }
            String key = field.get("key").asString();
            JsonNode value = values == null ? null : values.get(key);
            String refRange = referenceRange(field);
            String flag = flags != null && flags.hasNonNull(key) ? flags.get(key).asString() : null;
            out.add(new ReportPdfService.ResultRow(
                    field.hasNonNull("label") ? field.get("label").asString() : key,
                    value == null ? null : value.asString(),
                    field.hasNonNull("unit") ? field.get("unit").asString() : null,
                    refRange, flag));
        }
        return out;
    }

    private String referenceRange(JsonNode field) {
        boolean hasLow = field.hasNonNull("refLow");
        boolean hasHigh = field.hasNonNull("refHigh");
        if (!hasLow && !hasHigh) {
            return null;
        }
        return (hasLow ? field.get("refLow").asString() : "") + " – "
                + (hasHigh ? field.get("refHigh").asString() : "");
    }

    public ReportStatus status(Long invoiceId) {
        return reports.findByInvoiceId(invoiceId)
                .map(r -> new ReportStatus(invoiceId, r.getFinalizedAt() != null, r.getFinalizedAt()))
                .orElse(new ReportStatus(invoiceId, false, null));
    }

    public byte[] pdfBytes(Long invoiceId, String ip) {
        Report report = reports.findByInvoiceId(invoiceId)
                .filter(r -> r.getPdfPath() != null)
                .orElseThrow(() -> new NotFoundException("Report not finalized for invoice " + invoiceId));
        try {
            byte[] bytes = Files.readAllBytes(Path.of(report.getPdfPath()));
            audit.record(currentUser.require().getId(), "VIEW", "Report", invoiceId, null, ip);
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read report PDF", e);
        }
    }
}
