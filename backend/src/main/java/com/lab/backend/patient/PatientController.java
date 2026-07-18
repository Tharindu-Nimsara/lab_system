package com.lab.backend.patient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService service;

    public record PatientRequest(@NotBlank String name,
                                 String nicOrId,
                                 LocalDate dob,
                                 String gender,
                                 @NotBlank String phone,
                                 String email,
                                 String address,
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

    @GetMapping("/{id}")
    public Patient get(@PathVariable Long id, HttpServletRequest http) {
        return service.view(id, http.getRemoteAddr());
    }
}
