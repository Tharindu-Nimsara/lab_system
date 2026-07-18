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

    @PostMapping("/{invoiceId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public ReportService.ReportStatus finalize(@PathVariable Long invoiceId, HttpServletRequest http) {
        return service.finalize(invoiceId, http.getRemoteAddr());
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
