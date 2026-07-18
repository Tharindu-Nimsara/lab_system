package com.lab.backend.billing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    public record CreateInvoiceRequest(@NotNull Long patientId,
                                       @NotEmpty List<Long> testIds,
                                       @PositiveOrZero BigDecimal discount,
                                       @NotNull @Pattern(regexp = "CASH|CARD") String paymentMethod) {}

    @PostMapping
    public BillingService.InvoiceDetail create(@Valid @RequestBody CreateInvoiceRequest req,
                                               HttpServletRequest http) {
        return service.createInvoice(req, http.getRemoteAddr());
    }

    @GetMapping("/{id}")
    public BillingService.InvoiceDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<Invoice> forPatient(@RequestParam Long patientId) {
        return service.forPatient(patientId);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingService.InvoiceDetail voidInvoice(@PathVariable Long id, HttpServletRequest http) {
        return service.voidInvoice(id, http.getRemoteAddr());
    }
}
