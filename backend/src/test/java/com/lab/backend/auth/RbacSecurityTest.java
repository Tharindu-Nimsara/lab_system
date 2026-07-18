package com.lab.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the URL-level RBAC rules in {@link SecurityConfig}: each role can reach
 * exactly its own endpoints and is forbidden from others (plan §8 API RBAC tests).
 *
 * <p>Strategy: authorization is decided before the handler runs, so a forbidden
 * request returns 403 regardless of the DB. For permitted requests we assert the
 * response is NOT an auth rejection (401/403) — the handler may still 200/404/500,
 * but that is out of scope for an access-control test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacSecurityTest {

    @Autowired
    MockMvc mvc;

    private void assertForbidden(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    private void assertNotAuthRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) {
                throw new AssertionError("Expected access to " + path + " but got " + s);
            }
        });
    }

    // ---- Unauthenticated ----

    @Test
    void unauthenticatedIsRejectedFromApi() throws Exception {
        mvc.perform(get("/api/patients?search=x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        assertNotAuthRejected("/actuator/health");
    }

    // ---- Receptionist: POS + patients, NOT worklist/finance/admin ----

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistReachesPatientsNotLabOrAdmin() throws Exception {
        assertNotAuthRejected("/api/patients?search=x");
        assertForbidden("/api/results/anything");
        assertForbidden("/api/admin/stats");
        assertForbidden("/api/finance/summary/daily");
    }

    // ---- Lab staff: worklist/results, NOT patients/finance/admin ----

    @Test
    @WithMockUser(roles = "LAB_STAFF")
    void labStaffReachesResultsNotPatientsOrAdmin() throws Exception {
        assertNotAuthRejected("/api/results/anything");
        assertForbidden("/api/patients?search=x");
        assertForbidden("/api/admin/stats");
        assertForbidden("/api/finance/summary/daily");
    }

    // ---- Reports: reachable by all three operational roles ----

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void reportsReachableByReceptionist() throws Exception {
        assertNotAuthRejected("/api/reports/anything");
    }

    @Test
    @WithMockUser(roles = "LAB_STAFF")
    void reportsReachableByLabStaff() throws Exception {
        assertNotAuthRejected("/api/reports/anything");
    }

    // ---- Admin: everything ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReachesEverything() throws Exception {
        assertNotAuthRejected("/api/patients?search=x");
        assertNotAuthRejected("/api/results/anything");
        assertNotAuthRejected("/api/admin/stats");
        assertNotAuthRejected("/api/finance/summary/daily");
    }

    // ---- Marketing: aggregate only — no patient data, no finance, no admin ----

    @Test
    @WithMockUser(roles = "MARKETING")
    void marketingIsForbiddenFromSensitiveEndpoints() throws Exception {
        assertForbidden("/api/patients?search=x");
        assertForbidden("/api/results/anything");
        assertForbidden("/api/finance/summary/daily");
        assertForbidden("/api/admin/stats");
    }
}
