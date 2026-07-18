package com.lab.backend.report;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    private final EmailService email;

    @PostMapping("/{invoiceId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public ReportService.ReportStatus finalize(@PathVariable Long invoiceId, HttpServletRequest http) {
        return service.finalize(invoiceId, http.getRemoteAddr());
    }

    /** Email the finalized report to the patient (consent-gated in the service). */
    @PostMapping("/{invoiceId}/email")
    public ReportService.DeliveryStatus emailReport(@PathVariable Long invoiceId, HttpServletRequest http) {
        return service.emailReport(invoiceId, http.getRemoteAddr());
    }

    public record MailConfig(boolean emailEnabled) {}

    /** Whether email delivery is usable in this environment (drives the UI button). */
    @GetMapping("/config")
    public MailConfig config() {
        return new MailConfig(email.isEnabled());
    }

    @GetMapping("/{invoiceId}")
    public ReportService.ReportStatus status(@PathVariable Long invoiceId) {
        return service.status(invoiceId);
    }

    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long invoiceId, HttpServletRequest http) {
        byte[] bytes = service.pdfBytes(invoiceId, http.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=report-" + invoiceId + ".pdf")
                .body(bytes);
    }
}
