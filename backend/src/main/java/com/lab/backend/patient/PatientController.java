package com.lab.backend.patient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService service;

    /**
     * Reception registers by {@code age} for convenience; the exact {@code dob}
     * can be supplied instead (and wins when both are present). The stored DOB
     * remains editable later via update.
     */
    public record PatientRequest(@NotBlank String name,
                                 String nicOrId,
                                 LocalDate dob,
                                 @PositiveOrZero @Max(150) Integer age,
                                 String gender,
                                 @NotBlank String phone,
                                 String email,
                                 String address,
                                 String specialNote,
                                 Boolean consentEmail,
                                 Boolean consentWhatsapp) {}

    @PostMapping
    public Patient register(@Valid @RequestBody PatientRequest req, HttpServletRequest http) {
        return service.create(req, http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public Patient update(@PathVariable Long id, @Valid @RequestBody PatientRequest req,
                          HttpServletRequest http) {
        return service.update(id, req, http.getRemoteAddr());
    }

    @GetMapping
    public List<Patient> search(@RequestParam(name = "search", defaultValue = "") String search) {
        return search.isBlank() ? List.of() : service.search(search.trim());
    }

    /** Active patients already on this phone — reception checks before creating a duplicate. */
    @GetMapping("/duplicates")
    public List<Patient> duplicates(@RequestParam("phone") String phone) {
        return service.duplicatesByPhone(phone);
    }

    /** Merge a duplicate into the surviving record (admin only). */
    public record MergeRequest(Long sourceId, Long targetId) {}

    @PostMapping("/merge")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Patient merge(@RequestBody MergeRequest req, HttpServletRequest http) {
        return service.merge(req.sourceId(), req.targetId(), http.getRemoteAddr());
    }

    @GetMapping("/{id}")
    public Patient get(@PathVariable Long id, HttpServletRequest http) {
        return service.view(id, http.getRemoteAddr());
    }
}
