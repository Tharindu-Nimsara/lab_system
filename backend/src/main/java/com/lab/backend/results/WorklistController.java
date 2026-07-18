package com.lab.backend.results;

import tools.jackson.databind.JsonNode;
import com.lab.backend.billing.LabOrder;
import com.lab.backend.billing.OrderRepository;
import com.lab.backend.billing.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class WorklistController {

    private final WorklistService service;

    public record StatusRequest(@NotNull OrderStatus status) {}

    public record ResultRequest(@NotNull JsonNode values) {}

    @GetMapping
    public List<OrderRepository.WorklistRow> worklist(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.worklist(status, date);
    }

    @PatchMapping("/{id}/status")
    public LabOrder changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req,
                                 HttpServletRequest http) {
        return service.changeStatus(id, req.status(), http.getRemoteAddr());
    }

    @PostMapping("/{id}/result")
    public WorklistService.ResultResponse enterResult(@PathVariable Long id,
                                                      @Valid @RequestBody ResultRequest req,
                                                      HttpServletRequest http) {
        return service.enterResult(id, req.values(), http.getRemoteAddr());
    }

    @GetMapping("/{id}/result")
    public WorklistService.ResultResponse getResult(@PathVariable Long id) {
        return service.getResult(id);
    }

    @PostMapping("/{id}/verify")
    public LabOrder verify(@PathVariable Long id, HttpServletRequest http) {
        return service.verify(id, http.getRemoteAddr());
    }
}
