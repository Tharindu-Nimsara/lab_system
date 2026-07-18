package com.lab.backend;

import com.lab.backend.billing.BillingController;
import com.lab.backend.billing.BillingService;
import com.lab.backend.billing.OrderRepository;
import com.lab.backend.billing.OrderStatus;
import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.common.Json;
import com.lab.backend.patient.Patient;
import com.lab.backend.patient.PatientController;
import com.lab.backend.patient.PatientService;
import com.lab.backend.report.ReportService;
import com.lab.backend.results.WorklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end money-path integration test against a real throwaway PostgreSQL
 * (plan §8): register patient → transactional invoice creates orders →
 * collect + enter results (with H/L flagging) → finalize report.
 *
 * <p>Requires Docker. {@code @EnabledIfDockerAvailable} skips the whole class
 * (before Spring bootstraps the container) when Docker is unavailable, so the
 * suite stays green in Docker-less environments. Runs as the seeded admin so
 * {@code CurrentUserService.require()} resolves.
 */
@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@WithMockUser(username = "admin@lab.local", roles = "ADMIN")
class MoneyPathIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PatientService patients;
    @Autowired BillingService billing;
    @Autowired WorklistService worklist;
    @Autowired ReportService reports;
    @Autowired LabTestRepository tests;
    @Autowired OrderRepository orders;

    @Test
    void fullMoneyPath_invoiceToFinalizedReport() {
        // The FBS test (glucose, ref 70–100) is seeded by DataSeeder on startup.
        LabTest fbs = tests.findByIsActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(t -> "FBS".equals(t.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seeded FBS test missing"));

        // 1. Register a patient.
        Patient patient = patients.create(new PatientController.PatientRequest(
                "Test Patient", null, null, 45, "Male", "0770000000",
                null, null, null, false, false), "127.0.0.1");
        assertThat(patient.getId()).isNotNull();

        // 2. Bill it — one transactional call must create the invoice + one order.
        var detail = billing.createInvoice(new BillingController.CreateInvoiceRequest(
                patient.getId(), List.of(fbs.getId()), BigDecimal.ZERO, "CASH"), "127.0.0.1");
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.invoice().getStatus()).isEqualTo("PAID");
        Long orderId = detail.items().get(0).orderId();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);

        // 3. Move the order through collection, then enter an out-of-range result.
        worklist.changeStatus(orderId, OrderStatus.COLLECTED, "127.0.0.1");
        worklist.changeStatus(orderId, OrderStatus.IN_PROGRESS, "127.0.0.1");
        var result = worklist.enterResult(orderId,
                Json.MAPPER.readTree("{\"glucose\":130}"), "127.0.0.1");

        // Glucose 130 is above the 100 upper bound → server flags it High.
        assertThat(result.flags().get("glucose").asString()).isEqualTo("H");
        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED);

        // 4. Finalize the report — the gate passes now that results are COMPLETED.
        var status = reports.finalize(detail.invoice().getId(), "127.0.0.1");
        assertThat(status.finalized()).isTrue();
        assertThat(status.finalizedAt()).isNotNull();

        // 5. The rendered PDF is non-empty.
        byte[] pdf = reports.pdfBytes(detail.invoice().getId(), "127.0.0.1");
        assertThat(pdf).isNotEmpty();
    }
}
