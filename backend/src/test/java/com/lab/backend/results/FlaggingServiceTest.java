package com.lab.backend.results;

import com.lab.backend.common.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlaggingServiceTest {

    private final FlaggingService service = new FlaggingService();

    private static final String TEMPLATE = """
        [{"key":"glucose","label":"Fasting Glucose","unit":"mg/dL","refLow":70,"refHigh":100,"type":"number"},
         {"key":"note","label":"Note","type":"text"}]
        """;

    private JsonNode flags(String valuesJson) {
        return service.computeFlags(Json.parse(TEMPLATE), Json.parse(valuesJson));
    }

    @Test
    void flagsHighValue() {
        JsonNode f = flags("{\"glucose\": 126}");
        assertEquals("H", f.get("glucose").asString());
    }

    @Test
    void flagsLowValue() {
        JsonNode f = flags("{\"glucose\": 55}");
        assertEquals("L", f.get("glucose").asString());
    }

    @Test
    void inRangeValueGetsNoFlag() {
        assertTrue(flags("{\"glucose\": 85}").isEmpty());
    }

    @Test
    void boundaryValuesAreNotFlagged() {
        assertTrue(flags("{\"glucose\": 70}").isEmpty());
        assertTrue(flags("{\"glucose\": 100}").isEmpty());
    }

    @Test
    void missingAndNonNumericValuesAreIgnored() {
        assertTrue(flags("{}").isEmpty());
        assertTrue(flags("{\"glucose\": \"abc\"}").isEmpty());
        assertFalse(flags("{\"glucose\": 200, \"note\": 5}").has("note"));
    }

    @Test
    void nullTemplateProducesNoFlags() {
        assertTrue(service.computeFlags(null, Json.parse("{\"glucose\": 500}")).isEmpty());
    }
}
