package com.lab.backend.billing;

import com.lab.backend.common.NotFoundException;
import com.lab.backend.patient.Patient;
import com.lab.backend.patient.PatientRepository;
import com.lab.backend.report.BillPdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService service;
    private final BillPdfService billPdf;
    private final PatientRepository patients;

    /**
     * {@code amountPaid} is the amount collected now: null means pay the full
     * total, a smaller value records a partial payment (deposit) leaving a balance.
     */
    public record CreateInvoiceRequest(@NotNull Long patientId,
                                       @NotEmpty List<Long> testIds,
                                       @PositiveOrZero BigDecimal discount,
                                       @NotNull @Pattern(regexp = "CASH|CARD") String paymentMethod,
                                       @PositiveOrZero BigDecimal amountPaid) {}

    @PostMapping
    public BillingService.InvoiceDetail create(@Valid @RequestBody CreateInvoiceRequest req,
                                               HttpServletRequest http) {
        return service.createInvoice(req, http.getRemoteAddr());
    }

    /** Take a further payment on an invoice with an outstanding balance. */
    public record PaymentRequest(@NotNull @PositiveOrZero BigDecimal amount,
                                 @Pattern(regexp = "CASH|CARD") String paymentMethod) {}

    @PostMapping("/{id}/payments")
    public BillingService.InvoiceDetail addPayment(@PathVariable Long id,
                                                   @Valid @RequestBody PaymentRequest req,
                                                   HttpServletRequest http) {
        return service.addPayment(id, req, http.getRemoteAddr());
    }

    @GetMapping("/{id}")
    public BillingService.InvoiceDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<Invoice> forPatient(@RequestParam Long patientId) {
        return service.forPatient(patientId);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        BillingService.InvoiceDetail detail = service.get(id);
        Patient patient = patients.findById(detail.invoice().getPatientId())
                .orElseThrow(() -> new NotFoundException("Patient not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "inline; filename=" + detail.invoice().getInvoiceNo() + ".pdf")
                .body(billPdf.render(detail, patient));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingService.InvoiceDetail voidInvoice(@PathVariable Long id, HttpServletRequest http) {
        return service.voidInvoice(id, http.getRemoteAddr());
    }
}
