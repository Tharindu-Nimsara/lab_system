package com.lab.backend.auth;

import com.lab.backend.catalog.LabTest;
import com.lab.backend.catalog.LabTestRepository;
import com.lab.backend.catalog.TestTemplate;
import com.lab.backend.catalog.TestTemplateRepository;
import com.lab.backend.common.Branch;
import com.lab.backend.common.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Dev/bootstrap seeding: first branch, first admin (plus one user per role for
 * RBAC testing), and two sample test templates so every screen is exercisable.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final BranchRepository branches;
    private final UserRepository users;
    private final TestTemplateRepository templates;
    private final LabTestRepository tests;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:ChangeMe123!}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Branch branch;
        if (branches.count() == 0) {
            branch = new Branch();
            branch.setName("Main Branch");
            branch = branches.save(branch);
        } else {
            branch = branches.findAll().getFirst();
        }

        if (users.count() == 0) {
            seedUser(branch, "Administrator", "admin@lab.local", Role.ADMIN);
            seedUser(branch, "Reception Desk", "reception@lab.local", Role.RECEPTIONIST);
            seedUser(branch, "Lab Technician", "lab@lab.local", Role.LAB_STAFF);
            log.warn("Seeded default users (admin@lab.local, reception@lab.local, lab@lab.local) "
                    + "with the configured default password. Change these before production.");
        }

        if (templates.count() == 0) {
            TestTemplate fbs = new TestTemplate();
            fbs.setName("Fasting Blood Sugar");
            fbs.setFields("""
                [{"key":"glucose","label":"Fasting Glucose","unit":"mg/dL",
                  "refLow":70,"refHigh":100,"type":"number"}]
                """);
            fbs = templates.save(fbs);

            TestTemplate lipid = new TestTemplate();
            lipid.setName("Lipid Profile");
            lipid.setFields("""
                [{"key":"totalCholesterol","label":"Total Cholesterol","unit":"mg/dL","refLow":125,"refHigh":200,"type":"number"},
                 {"key":"hdl","label":"HDL","unit":"mg/dL","refLow":40,"refHigh":90,"type":"number"},
                 {"key":"ldl","label":"LDL","unit":"mg/dL","refLow":0,"refHigh":130,"type":"number"},
                 {"key":"triglycerides","label":"Triglycerides","unit":"mg/dL","refLow":0,"refHigh":150,"type":"number"}]
                """);
            lipid = templates.save(lipid);

            seedTest("FBS", "Fasting Blood Sugar", "Biochemistry", "1200.00", "Blood", fbs);
            seedTest("LIPID", "Lipid Profile", "Biochemistry", "3500.00", "Blood", lipid);
        }
    }

    private void seedUser(Branch branch, String name, String email, Role role) {
        AppUser u = new AppUser();
        u.setBranchId(branch.getId());
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(defaultPassword));
        u.setRole(role);
        u.setActive(true);
        users.save(u);
    }

    private void seedTest(String code, String name, String category, String price,
                          String specimen, TestTemplate template) {
        LabTest t = new LabTest();
        t.setCode(code);
        t.setName(name);
        t.setCategory(category);
        t.setPrice(new BigDecimal(price));
        t.setSpecimenType(specimen);
        t.setTemplateId(template.getId());
        t.setActive(true);
        tests.save(t);
    }
}
