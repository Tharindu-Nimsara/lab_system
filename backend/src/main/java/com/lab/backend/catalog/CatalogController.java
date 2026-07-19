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
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final LabTestRepository tests;
    private final TestTemplateRepository templates;
    private final LabRepository labs;
    private final TestLabPriceRepository labPrices;

    /**
     * {@code offeredInHouse} = our lab does this test; then {@code price} is its
     * in-house price. When false the test is outsource-only (no in-house price
     * row) and {@code price} is ignored — outsourced prices are set per lab.
     */
    public record TestRequest(@NotBlank String code,
                              @NotBlank String name,
                              @NotBlank String category,
                              @PositiveOrZero BigDecimal price,
                              String specimenType,
                              @NotNull Long templateId,
                              Boolean offeredInHouse,
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
        boolean inHouse = req.offeredInHouse() == null || req.offeredInHouse();
        BigDecimal basePrice = req.price() == null ? BigDecimal.ZERO : req.price();
        if (inHouse && req.price() == null) {
            throw new IllegalArgumentException("An in-house test needs a price");
        }

        test.setCode(req.code());
        test.setName(req.name());
        test.setCategory(req.category());
        // tests.price is a base/display price; for outsource-only tests it's 0
        // and the real prices live on the per-lab rows.
        test.setPrice(basePrice);
        test.setSpecimenType(req.specimenType());
        test.setTemplateId(req.templateId());
        test.setActive(req.isActive() == null || req.isActive());
        LabTest saved = tests.save(test);

        // Sync the in-house price row when the test is offered in-house. For an
        // outsource-only test, deactivate any existing in-house row (e.g. a test
        // we used to do and now only outsource) so it can't be billed at our lab.
        labs.findFirstByIsOutsourcedFalse().ifPresent(house -> {
            var existing = labPrices.findByTestIdAndLabId(saved.getId(), house.getId());
            if (inHouse) {
                TestLabPrice tlp = existing.orElseGet(() -> {
                    TestLabPrice n = new TestLabPrice();
                    n.setTestId(saved.getId());
                    n.setLabId(house.getId());
                    return n;
                });
                tlp.setPrice(basePrice);
                tlp.setActive(true);
                labPrices.save(tlp);
            } else {
                existing.ifPresent(tlp -> {
                    tlp.setActive(false);
                    labPrices.save(tlp);
                });
            }
        });
        return saved;
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

    // ---- Labs & per-lab prices ----

    @GetMapping("/labs")
    public List<Lab> labs() {
        return labs.findByIsActiveTrueOrderBySortOrderAscNameAsc();
    }

    /**
     * One lab's price for a test. {@code commissionRate} (percent we earn on the
     * outsourced test) is admin-only — non-admins receive null.
     */
    public record LabPrice(Long labPriceId, Long labId, String labName, boolean outsourced,
                           BigDecimal price, BigDecimal commissionRate, boolean active) {}

    /** All labs' prices for a test — powers the POS price comparison. */
    @GetMapping("/tests/{testId}/prices")
    public List<LabPrice> testPrices(@PathVariable Long testId,
                                     org.springframework.security.core.Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        Map<Long, Lab> labById = labs.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Lab::getId, l -> l));
        return labPrices.findByTestId(testId).stream()
                .filter(TestLabPrice::isActive)
                .map(p -> {
                    Lab l = labById.get(p.getLabId());
                    return new LabPrice(p.getId(), p.getLabId(),
                            l != null ? l.getName() : "?",
                            l != null && l.isOutsourced(),
                            p.getPrice(),
                            admin ? p.getCommissionRate() : null,  // admin-only
                            p.isActive());
                })
                .sorted(java.util.Comparator.comparing(LabPrice::outsourced)
                        .thenComparing(LabPrice::labName))
                .toList();
    }

    public record LabPriceRequest(@NotNull Long labId,
                                  @NotNull @PositiveOrZero BigDecimal price,
                                  @PositiveOrZero BigDecimal commissionRate,
                                  Boolean isActive) {}

    /** Add or update a test's price (and commission) at a lab (admin). Upsert on (test, lab). */
    @PutMapping("/tests/{testId}/prices")
    @PreAuthorize("hasRole('ADMIN')")
    public LabPrice setTestPrice(@PathVariable Long testId, @Valid @RequestBody LabPriceRequest req) {
        if (!tests.existsById(testId)) {
            throw new NotFoundException("Test not found: " + testId);
        }
        Lab lab = labs.findById(req.labId())
                .orElseThrow(() -> new NotFoundException("Lab not found: " + req.labId()));
        TestLabPrice tlp = labPrices.findByTestIdAndLabId(testId, req.labId())
                .orElseGet(() -> {
                    TestLabPrice n = new TestLabPrice();
                    n.setTestId(testId);
                    n.setLabId(req.labId());
                    return n;
                });
        tlp.setPrice(req.price());
        if (req.commissionRate() != null) {
            tlp.setCommissionRate(req.commissionRate());
        }
        tlp.setActive(req.isActive() == null || req.isActive());
        tlp = labPrices.save(tlp);
        return new LabPrice(tlp.getId(), lab.getId(), lab.getName(), lab.isOutsourced(),
                tlp.getPrice(), tlp.getCommissionRate(), tlp.isActive());
    }
}
