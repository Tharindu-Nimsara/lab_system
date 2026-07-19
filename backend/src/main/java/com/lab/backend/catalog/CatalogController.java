package com.lab.backend.catalog;

import tools.jackson.databind.JsonNode;
import com.lab.backend.common.Json;
import com.lab.backend.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final LabTestRepository tests;
    private final TestTemplateRepository templates;

    public record TestRequest(@NotBlank String code,
                              @NotBlank String name,
                              @NotBlank String category,
                              @NotNull @PositiveOrZero BigDecimal price,
                              String specimenType,
                              @NotNull Long templateId,
                              Boolean isActive) {}

    public record TemplateRequest(@NotBlank String name, @NotNull JsonNode fields) {}

    public record TemplateResponse(Long id, String name, JsonNode fields) {
        static TemplateResponse of(TestTemplate t) {
            return new TemplateResponse(t.getId(), t.getName(), Json.parse(t.getFields()));
        }
    }

    // ---- Tests ----

    @GetMapping("/tests")
    public List<LabTest> activeTests() {
        return tests.findByIsActiveTrueOrderByCategoryAscNameAsc();
    }

    @GetMapping("/tests/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LabTest> allTests() {
        return tests.findAll();
    }

    @PostMapping("/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public LabTest createTest(@Valid @RequestBody TestRequest req) {
        requireTemplate(req.templateId());
        return applyTest(new LabTest(), req);
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LabTest updateTest(@PathVariable Long id, @Valid @RequestBody TestRequest req) {
        LabTest test = tests.findById(id)
                .orElseThrow(() -> new NotFoundException("Test not found: " + id));
        requireTemplate(req.templateId());
        return applyTest(test, req);
    }

    private void requireTemplate(Long templateId) {
        if (!templates.existsById(templateId)) {
            throw new NotFoundException("Template not found: " + templateId);
        }
    }

    private LabTest applyTest(LabTest test, TestRequest req) {
        test.setCode(req.code());
        test.setName(req.name());
        test.setCategory(req.category());
        test.setPrice(req.price());
        test.setSpecimenType(req.specimenType());
        test.setTemplateId(req.templateId());
        test.setActive(req.isActive() == null || req.isActive());
        return tests.save(test);
    }

    // ---- Templates ----

    @GetMapping("/templates")
    public List<TemplateResponse> allTemplates() {
        return templates.findAll().stream().map(TemplateResponse::of).toList();
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public TemplateResponse createTemplate(@Valid @RequestBody TemplateRequest req) {
        return applyTemplate(new TestTemplate(), req);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TemplateResponse updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        TestTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found: " + id));
        return applyTemplate(template, req);
    }

    private TemplateResponse applyTemplate(TestTemplate template, TemplateRequest req) {
        if (!req.fields().isArray()) {
            throw new IllegalArgumentException("Template fields must be a JSON array");
        }
        template.setName(req.name());
        template.setFields(req.fields().toString());
        return TemplateResponse.of(templates.save(template));
    }
}
