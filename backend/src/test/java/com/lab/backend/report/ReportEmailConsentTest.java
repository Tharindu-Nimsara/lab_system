package com.lab.backend.report;

import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.billing.Invoice;
import com.lab.backend.billing.InvoiceRepository;
import com.lab.backend.common.audit.AuditService;
import com.lab.backend.patient.Patient;
import com.lab.backend.patient.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The consent gate on report emailing (plan §5.4 privacy requirement): no email
 * leaves the system without a patient email on file AND email consent.
 */
@ExtendWith(MockitoExtension.class)
class ReportEmailConsentTest {

    @Mock InvoiceRepository invoices;
    @Mock PatientRepository patients;
    @Mock ReportRepository reports;
    @Mock EmailService email;
    @Mock AuditService audit;
    @Mock CurrentUserService currentUser;

    @InjectMocks ReportService service;

    private Report finalizedReport(Path pdf) {
        Report r = new Report();
        r.setInvoiceId(10L);
        r.setPatientId(5L);
        r.setPdfPath(pdf.toString());
        r.setFinalizedAt(OffsetDateTime.now());
        return r;
    }

    private Patient patient(String email, boolean consent) {
        Patient p = new Patient();
        p.setName("Jane Doe");
        p.setEmail(email);
        p.setConsentEmail(consent);
        return p;
    }

    @BeforeEach
    void reportExists(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("report-INV-1.pdf");
        Files.write(pdf, new byte[]{1, 2, 3});
        lenient().when(reports.findByInvoiceId(10L)).thenReturn(Optional.of(finalizedReport(pdf)));
    }

    @Test
    void rejectsWhenNoEmailOnFile() {
        when(patients.findById(5L)).thenReturn(Optional.of(patient(null, true)));

        assertThatThrownBy(() -> service.emailReport(10L, "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no email");
        verifyNoInteractions(email);
    }

    @Test
    void rejectsWhenNoConsent() {
        when(patients.findById(5L)).thenReturn(Optional.of(patient("jane@example.com", false)));

        assertThatThrownBy(() -> service.emailReport(10L, "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consent");
        verifyNoInteractions(email);
    }

    @Test
    void sendsAndStampsWhenEmailAndConsentPresent() {
        when(patients.findById(5L)).thenReturn(Optional.of(patient("jane@example.com", true)));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV-1");
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));
        AppUser user = new AppUser();
        user.setId(99L);
        when(currentUser.require()).thenReturn(user);

        var status = service.emailReport(10L, "ip");

        verify(email).sendReport(eq("jane@example.com"), eq("Jane Doe"), eq("INV-1"), any());
        verify(reports).save(argThat(r -> r.getSentEmailAt() != null));
        verify(audit).record(eq(99L), eq("SEND_EMAIL"), eq("Report"), eq(10L), any(), eq("ip"));
        assertThat(status.sentEmailAt()).isNotNull();
    }
}
