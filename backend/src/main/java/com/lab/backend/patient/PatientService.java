package com.lab.backend.patient;

import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patients;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    @Transactional
    public Patient create(PatientController.PatientRequest req, String ip) {
        Patient p = new Patient();
        p.setPatientNo("P-%06d".formatted(patients.nextPatientNo()));
        apply(p, req);
        Patient saved = patients.save(p);
        audit.record(currentUser.require().getId(), "CREATE", "Patient", saved.getId(), null, ip);
        return saved;
    }

    @Transactional
    public Patient update(Long id, PatientController.PatientRequest req, String ip) {
        Patient p = patients.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + id));
        apply(p, req);
        Patient saved = patients.save(p);
        audit.record(currentUser.require().getId(), "UPDATE", "Patient", saved.getId(), null, ip);
        return saved;
    }

    public List<Patient> search(String query) {
        return patients.search(query, PageRequest.of(0, 20));
    }

    /** Existing active patients on this exact phone — reception uses this to catch duplicates. */
    public List<Patient> duplicatesByPhone(String phone) {
        return phone == null || phone.isBlank() ? List.of()
                : patients.findActiveByPhone(phone.trim());
    }

    /**
     * Merge {@code sourceId} into {@code targetId}: all invoices and reports are
     * repointed to the survivor, then the source is soft-deleted. Nothing is ever
     * hard-deleted (plan §5.2). Both must exist and be distinct.
     */
    @Transactional
    public Patient merge(Long sourceId, Long targetId, String ip) {
        if (sourceId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot merge a patient into itself");
        }
        Patient source = patients.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + sourceId));
        Patient target = patients.findById(targetId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + targetId));
        if (source.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source patient is already merged/deleted");
        }
        if (target.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target patient is merged/deleted");
        }

        int invoices = patients.repointInvoices(sourceId, targetId);
        int reports = patients.repointReports(sourceId, targetId);
        source.setDeletedAt(OffsetDateTime.now());
        patients.save(source);

        var details = com.lab.backend.common.Json.MAPPER.createObjectNode()
                .put("mergedInto", targetId)
                .put("invoicesMoved", invoices)
                .put("reportsMoved", reports);
        audit.record(currentUser.require().getId(), "MERGE", "Patient", sourceId, details, ip);
        return target;
    }

    public Patient view(Long id, String ip) {
        Patient p = patients.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + id));
        audit.record(currentUser.require().getId(), "VIEW", "Patient", p.getId(), null, ip);
        return p;
    }

    private void apply(Patient p, PatientController.PatientRequest req) {
        p.setName(req.name());
        p.setNicOrId(req.nicOrId());
        // Exact DOB wins; otherwise derive it from the given age so the computed
        // age advances by itself one year after this date. Neither given → keep as-is.
        if (req.dob() != null) {
            p.setDob(req.dob());
        } else if (req.age() != null) {
            p.setDob(java.time.LocalDate.now().minusYears(req.age()));
        }
        p.setGender(req.gender());
        p.setSpecialNote(req.specialNote());
        p.setPhone(req.phone());
        p.setEmail(req.email());
        p.setAddress(req.address());
        p.setConsentEmail(Boolean.TRUE.equals(req.consentEmail()));
        p.setConsentWhatsapp(Boolean.TRUE.equals(req.consentWhatsapp()));
    }
}
