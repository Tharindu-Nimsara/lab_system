package com.lab.backend.billing;

import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.catalog.Lab;
import com.lab.backend.catalog.LabRepository;
import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.catalog.TestLabPrice;
import com.lab.backend.catalog.TestLabPriceRepository;
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
    private final LabRepository labs;
    private final TestLabPriceRepository labPrices;
    private final PatientRepository patients;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public record InvoiceItemDetail(Long itemId, Long testId, String testCode, String testName,
                                    Long labId, String labName, boolean outsourced,
                                    BigDecimal priceAtSale, Long orderId, OrderStatus orderStatus) {}

    public record InvoiceDetail(Invoice invoice, List<InvoiceItemDetail> items) {}

    /** The heart of the system: invoice + items + orders in one all-or-nothing transaction. */
    @Transactional
    public InvoiceDetail createInvoice(BillingController.CreateInvoiceRequest req, String ip) {
        AppUser user = currentUser.require();

        if (!patients.existsById(req.patientId())) {
            throw new NotFoundException("Patient not found: " + req.patientId());
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new IllegalArgumentException("At least one test is required");
        }

        // Resolve each line to (test, lab, price-at-that-lab) up front so we can
        // total before saving anything.
        record Priced(Long testId, Long labId, BigDecimal price) {}
        List<Priced> priced = req.lines().stream().map(line -> {
            if (!tests.existsById(line.testId())) {
                throw new NotFoundException("Test not found: " + line.testId());
            }
            if (!labs.existsById(line.labId())) {
                throw new NotFoundException("Lab not found: " + line.labId());
            }
            TestLabPrice tlp = labPrices.findByTestIdAndLabId(line.testId(), line.labId())
                    .filter(TestLabPrice::isActive)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "This lab does not offer test " + line.testId()
                                    + " (no price for lab " + line.labId() + ")"));
            return new Priced(line.testId(), line.labId(), tlp.getPrice());
        }).toList();

        BigDecimal subtotal = priced.stream()
                .map(Priced::price)
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
        BigDecimal total = subtotal.subtract(discount);
        inv.setTotal(total);
        inv.setPaymentMethod(req.paymentMethod());

        // null amountPaid = pay in full; otherwise a deposit (clamped to the total).
        BigDecimal paidNow = req.amountPaid() == null ? total : req.amountPaid();
        if (paidNow.compareTo(total) > 0) {
            throw new IllegalArgumentException("Amount paid cannot exceed the invoice total");
        }
        inv.setAmountPaid(paidNow);
        inv.recomputeStatus();
        inv = invoices.save(inv);

        for (Priced p : priced) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoiceId(inv.getId());
            item.setTestId(p.testId());
            item.setLabId(p.labId());
            item.setPriceAtSale(p.price());
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

    /** Record a further payment against an invoice's outstanding balance. */
    @Transactional
    public InvoiceDetail addPayment(Long id, BillingController.PaymentRequest req, String ip) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
        if ("VOID".equals(inv.getStatus())) {
            throw new IllegalStateException("Cannot take payment on a void invoice");
        }
        BigDecimal amount = req.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (amount.compareTo(inv.getBalance()) > 0) {
            throw new IllegalArgumentException(
                    "Payment " + amount + " exceeds the outstanding balance " + inv.getBalance());
        }
        inv.setAmountPaid(inv.getAmountPaid().add(amount));
        if (req.paymentMethod() != null) {
            inv.setPaymentMethod(req.paymentMethod());
        }
        inv.recomputeStatus();
        inv = invoices.save(inv);
        audit.record(currentUser.require().getId(), "PAYMENT", "Invoice", inv.getId(), null, ip);
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
        Map<Long, Lab> labById = labs.findAllById(
                        invItems.stream().map(InvoiceItem::getLabId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Lab::getId, Function.identity()));

        List<InvoiceItemDetail> details = invItems.stream().map(item -> {
            LabTest t = testById.get(item.getTestId());
            LabOrder o = orderByItemId.get(item.getId());
            Lab lab = labById.get(item.getLabId());
            return new InvoiceItemDetail(item.getId(), item.getTestId(),
                    t != null ? t.getCode() : null, t != null ? t.getName() : null,
                    item.getLabId(), lab != null ? lab.getName() : null,
                    lab != null && lab.isOutsourced(),
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
