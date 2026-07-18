package com.lab.backend.results;

import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.common.Json;
import com.lab.backend.common.NotFoundException;
import com.lab.backend.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The anomaly alert queue (plan §5.6): flagged, unreviewed results awaiting a
 * clinician's acknowledge/dismiss decision. This is aggregate review of
 * out-of-range values, not diagnosis.
 */
@Service
@RequiredArgsConstructor
public class AnomalyService {

    static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    static final String DISMISSED = "DISMISSED";

    private final ResultRepository results;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public record AnomalyItem(Long resultId, Long orderId, String testCode, String testName,
                              String patientNo, String patientName,
                              Object values, Object flags, Instant enteredAt) {}

    public List<AnomalyItem> queue() {
        return results.openAnomalies().stream()
                .map(r -> new AnomalyItem(
                        r.getResultId(), r.getOrderId(), r.getTestCode(), r.getTestName(),
                        r.getPatientNo(), r.getPatientName(),
                        Json.parse(r.getResultValues()), Json.parse(r.getFlags()),
                        r.getEnteredAt()))
                .toList();
    }

    @Transactional
    public void acknowledge(Long orderId, String ip) {
        review(orderId, ACKNOWLEDGED, ip);
    }

    @Transactional
    public void dismiss(Long orderId, String ip) {
        review(orderId, DISMISSED, ip);
    }

    private void review(Long orderId, String action, String ip) {
        Result result = results.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Result not found for order: " + orderId));
        AppUser user = currentUser.require();
        result.setAnomalyReviewedAt(OffsetDateTime.now());
        result.setAnomalyReviewedBy(user.getId());
        result.setAnomalyAction(action);
        results.save(result);
        audit.record(user.getId(), action, "Result", result.getId(), null, ip);
    }
}
