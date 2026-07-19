package com.lab.backend.report;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Phone normalization to WhatsApp's country-code form (Sri Lanka default cc 94). */
class WhatsAppNormalizeTest {

    private WhatsAppService service() {
        WhatsAppService s = new WhatsAppService(null);
        ReflectionTestUtils.setField(s, "defaultCountryCode", "94");
        return s;
    }

    @Test
    void leadingZeroBecomesCountryCode() {
        assertThat(service().normalize("0771234567")).isEqualTo("94771234567");
    }

    @Test
    void stripsSpacesAndPlus() {
        assertThat(service().normalize("+94 77 123 4567")).isEqualTo("94771234567");
    }

    @Test
    void bareLocalNumberGetsCountryCode() {
        assertThat(service().normalize("771234567")).isEqualTo("94771234567");
    }

    @Test
    void alreadyInternationalUnchanged() {
        assertThat(service().normalize("94771234567")).isEqualTo("94771234567");
    }
}
