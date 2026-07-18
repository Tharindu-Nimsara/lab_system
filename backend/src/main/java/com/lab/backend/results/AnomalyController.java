package com.lab.backend.results;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Anomaly alert queue: review out-of-range results (plan §5.6). Lab staff + admin. */
@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService service;

    @GetMapping
    public List<AnomalyService.AnomalyItem> queue() {
        return service.queue();
    }

    @PostMapping("/{orderId}/acknowledge")
    public void acknowledge(@PathVariable Long orderId, HttpServletRequest http) {
        service.acknowledge(orderId, http.getRemoteAddr());
    }

    @PostMapping("/{orderId}/dismiss")
    public void dismiss(@PathVariable Long orderId, HttpServletRequest http) {
        service.dismiss(orderId, http.getRemoteAddr());
    }
}
