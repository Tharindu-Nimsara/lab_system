package com.lab.backend.patient;

import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Patient view(Long id, String ip) {
        Patient p = patients.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + id));
        audit.record(currentUser.require().getId(), "VIEW", "Patient", p.getId(), null, ip);
        return p;
    }

    private void apply(Patient p, PatientController.PatientRequest req) {
        p.setName(req.name());
        p.setNicOrId(req.nicOrId());
        p.setDob(req.dob());
        p.setGender(req.gender());
        p.setPhone(req.phone());
        p.setEmail(req.email());
        p.setAddress(req.address());
        p.setConsentEmail(Boolean.TRUE.equals(req.consentEmail()));
        p.setConsentWhatsapp(Boolean.TRUE.equals(req.consentWhatsapp()));
    }
}
