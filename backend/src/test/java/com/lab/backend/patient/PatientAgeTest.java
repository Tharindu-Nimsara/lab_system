package com.lab.backend.patient;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatientAgeTest {

    @Test
    void ageDerivesFromDob() {
        Patient p = new Patient();
        p.setDob(LocalDate.now().minusYears(30));
        assertEquals(30, p.getAge());
    }

    @Test
    void ageAdvancesOnlyAfterFullYear() {
        Patient p = new Patient();
        // Registered by age 30 exactly 364 days ago → still 30, not 31.
        p.setDob(LocalDate.now().minusYears(30).plusDays(1));
        assertEquals(29, p.getAge());
    }

    @Test
    void noDobMeansNoAge() {
        assertNull(new Patient().getAge());
    }
}
