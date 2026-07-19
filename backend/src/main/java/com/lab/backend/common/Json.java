package com.lab.backend.common;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single Jackson 3 mapper for converting between the raw-JSON strings stored in
 * JSONB entity columns and the JsonNode trees used at the API boundary.
 * (Hibernate 7's bundled format mapper is Jackson 2, Spring MVC's is Jackson 3,
 * so entities keep String and conversion happens here.)
 */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static JsonNode parse(String raw) {
        return raw == null ? null : MAPPER.readTree(raw);
    }

    public static String write(JsonNode node) {
        return node == null ? null : node.toString();
    }
}
