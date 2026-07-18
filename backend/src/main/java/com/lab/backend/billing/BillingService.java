package com.lab.backend.billing;

import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import com.lab.backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final OrderRepository orders;
    private final LabTestRepository tests;
    private final PatientRepository patients;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public record InvoiceItemDetail(Long itemId, Long testId, String testCode, String testName,
                                    BigDecimal priceAtSale, Long orderId, OrderStatus orderStatus) {}

    public record InvoiceDetail(Invoice invoice, List<InvoiceItemDetail> items) {}

    /** The heart of the system: invoice + items + orders in one all-or-nothing transaction. */
    @Transactional
    public InvoiceDetail createInvoice(BillingController.CreateInvoiceRequest req, String ip) {
        AppUser user = currentUser.require();

        if (!patients.existsById(req.patientId())) {
            throw new NotFoundException("Patient not found: " + req.patientId());
        }

        List<LabTest> selected = tests.findAllById(req.testIds());
        if (selected.size() != req.testIds().stream().distinct().count()) {
            throw new NotFoundException("One or more tests not found");
        }

        BigDecimal subtotal = selected.stream()
                .map(LabTest::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = req.discount() == null ? BigDecimal.ZERO : req.discount();
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed subtotal");
        }

        Invoice inv = new Invoice();
        inv.setInvoiceNo(nextInvoiceNo());
        inv.setBranchId(user.getBranchId());
        inv.setPatientId(req.patientId());
        inv.setCreatedBy(user.getId());
        inv.setSubtotal(subtotal);
        inv.setDiscount(discount);
        inv.setTotal(subtotal.subtract(discount));
        inv.setPaymentMethod(req.paymentMethod());
        inv = invoices.save(inv);

        for (LabTest t : selected) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoiceId(inv.getId());
            item.setTestId(t.getId());
            item.setPriceAtSale(t.getPrice());
            item = items.save(item);

            LabOrder order = new LabOrder();
            order.setInvoiceItemId(item.getId());
            orders.save(order);
        }

        audit.record(user.getId(), "CREATE", "Invoice", inv.getId(), null, ip);
        return detail(inv);
    }

    @Transactional
    public InvoiceDetail voidInvoice(Long id, String ip) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
        if ("VOID".equals(inv.getStatus())) {
            throw new IllegalStateException("Invoice is already void");
        }
        inv.setStatus("VOID");
        inv = invoices.save(inv);
        audit.record(currentUser.require().getId(), "VOID", "Invoice", inv.getId(), null, ip);
        return detail(inv);
    }

    public InvoiceDetail get(Long id) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
        return detail(inv);
    }

    public List<Invoice> forPatient(Long patientId) {
        return invoices.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private InvoiceDetail detail(Invoice inv) {
        List<InvoiceItem> invItems = items.findByInvoiceId(inv.getId());
        Map<Long, LabTest> testById = tests.findAllById(
                        invItems.stream().map(InvoiceItem::getTestId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(LabTest::getId, Function.identity()));
        Map<Long, LabOrder> orderByItemId = orders.findByInvoiceItemIdIn(
                        invItems.stream().map(InvoiceItem::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(LabOrder::getInvoiceItemId, Function.identity()));

        List<InvoiceItemDetail> details = invItems.stream().map(item -> {
            LabTest t = testById.get(item.getTestId());
            LabOrder o = orderByItemId.get(item.getId());
            return new InvoiceItemDetail(item.getId(), item.getTestId(),
                    t != null ? t.getCode() : null, t != null ? t.getName() : null,
                    item.getPriceAtSale(),
                    o != null ? o.getId() : null, o != null ? o.getStatus() : null);
        }).toList();
        return new InvoiceDetail(inv, details);
    }

    private String nextInvoiceNo() {
        return "INV-%s-%04d".formatted(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                invoices.nextInvoiceNo());
    }
}
