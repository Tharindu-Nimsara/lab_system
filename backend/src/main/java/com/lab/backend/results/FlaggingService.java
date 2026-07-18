package com.lab.backend.results;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.lab.backend.common.Json;
import org.springframework.stereotype.Service;

/**
 * Compares entered result values to the test template's reference ranges and
 * produces H/L flags — the server-side source of truth for anomaly marking.
 */
@Service
public class FlaggingService {

    public ObjectNode computeFlags(JsonNode templateFields, JsonNode values) {
        ObjectNode flags = Json.MAPPER.createObjectNode();
        if (templateFields == null || !templateFields.isArray()) {
            return flags;
        }
        for (JsonNode field : templateFields) {
            if (!field.hasNonNull("key")) {
                continue;
            }
            String key = field.get("key").asString();
            JsonNode value = values.get(key);
            if (value == null || !value.isNumber()) {
                continue;
            }
            double v = value.asDouble();
            if (field.hasNonNull("refHigh") && v > field.get("refHigh").asDouble()) {
                flags.put(key, "H");
            } else if (field.hasNonNull("refLow") && v < field.get("refLow").asDouble()) {
                flags.put(key, "L");
            }
        }
        return flags;
    }
}
