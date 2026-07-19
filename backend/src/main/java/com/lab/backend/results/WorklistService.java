package com.lab.backend.results;

import tools.jackson.databind.JsonNode;
import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.billing.InvoiceItem;
import com.lab.backend.billing.InvoiceItemRepository;
import com.lab.backend.billing.LabOrder;
import com.lab.backend.billing.OrderRepository;
import com.lab.backend.billing.OrderStatus;
import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.catalog.TestTemplate;
import com.lab.backend.catalog.TestTemplateRepository;
import com.lab.backend.common.Json;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorklistService {

    private final OrderRepository orders;
    private final InvoiceItemRepository invoiceItems;
    private final LabTestRepository tests;
    private final TestTemplateRepository templates;
    private final ResultRepository results;
    private final FlaggingService flagging;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public List<OrderRepository.WorklistRow> worklist(OrderStatus status, LocalDate day) {
        return orders.worklist(status == null ? null : status.name(), day);
    }

    @Transactional
    public LabOrder changeStatus(Long orderId, OrderStatus target, String ip) {
        LabOrder order = require(orderId);
        if (target == OrderStatus.COMPLETED || target == OrderStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    target + " is set by result entry/verification, not directly");
        }
        if (!order.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot move order from " + order.getStatus() + " to " + target);
        }
        if (target == OrderStatus.COLLECTED) {
            order.setSampleCollectedAt(OffsetDateTime.now());
        }
        order.setStatus(target);
        order.setUpdatedAt(OffsetDateTime.now());
        LabOrder saved = orders.save(order);
        audit.record(currentUser.require().getId(), "UPDATE", "Order", saved.getId(), null, ip);
        return saved;
    }

    public record ResultResponse(Long orderId, OrderStatus status, JsonNode values, JsonNode flags) {}

    @Transactional
    public ResultResponse enterResult(Long orderId, JsonNode values, String ip) {
        AppUser user = currentUser.require();
        LabOrder order = require(orderId);
        if (order.getStatus() != OrderStatus.IN_PROGRESS && order.getStatus() != OrderStatus.COLLECTED) {
            throw new IllegalStateException(
                    "Results can only be entered for COLLECTED or IN_PROGRESS orders (current: "
                            + order.getStatus() + ")");
        }

        InvoiceItem item = invoiceItems.findById(order.getInvoiceItemId())
                .orElseThrow(() -> new NotFoundException("Invoice item missing for order " + orderId));
        LabTest test = tests.findById(item.getTestId())
                .orElseThrow(() -> new NotFoundException("Test missing for order " + orderId));
        TestTemplate template = templates.findById(test.getTemplateId())
                .orElseThrow(() -> new NotFoundException("Template missing for test " + test.getCode()));

        JsonNode flags = flagging.computeFlags(Json.parse(template.getFields()), values);

        Result result = results.findByOrderId(orderId).orElseGet(Result::new);
        result.setOrderId(orderId);
        result.setResultValues(values.toString());
        result.setFlags(flags.toString());
        results.save(result);

        order.setStatus(OrderStatus.COMPLETED);
        order.setResultEnteredBy(user.getId());
        order.setUpdatedAt(OffsetDateTime.now());
        orders.save(order);

        audit.record(user.getId(), "CREATE", "Result", orderId, null, ip);
        return new ResultResponse(orderId, order.getStatus(), values, flags);
    }

    @Transactional
    public LabOrder verify(Long orderId, String ip) {
        AppUser user = currentUser.require();
        LabOrder order = require(orderId);
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED orders can be verified");
        }
        if (user.getId().equals(order.getResultEnteredBy())) {
            throw new IllegalStateException("Results must be verified by a different staff member");
        }
        order.setStatus(OrderStatus.VERIFIED);
        order.setVerifiedBy(user.getId());
        order.setUpdatedAt(OffsetDateTime.now());
        LabOrder saved = orders.save(order);
        audit.record(user.getId(), "VERIFY", "Order", saved.getId(), null, ip);
        return saved;
    }

    public ResultResponse getResult(Long orderId) {
        LabOrder order = require(orderId);
        Result result = results.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("No result entered for order " + orderId));
        return new ResultResponse(orderId, order.getStatus(),
                Json.parse(result.getResultValues()), Json.parse(result.getFlags()));
    }

    private LabOrder require(Long orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }
}
