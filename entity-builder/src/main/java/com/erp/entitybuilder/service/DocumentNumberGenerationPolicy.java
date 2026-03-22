package com.erp.entitybuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZoneId;
import java.util.Locale;

/**
 * Parsed from {@code entity_fields.config} under {@code documentNumberGeneration}
 * (see portal / API contract for {@code document_number} field type).
 */
public final class DocumentNumberGenerationPolicy {

    public enum Strategy {
        MANUAL,
        TIMESTAMP,
        TENANT_SEQUENCE,
        MONTHLY_SEQUENCE
    }

    private final Strategy strategy;
    private final String prefix;
    private final int sequenceWidth;
    private final ZoneId timeZone;

    private DocumentNumberGenerationPolicy(Strategy strategy, String prefix, int sequenceWidth, ZoneId timeZone) {
        this.strategy = strategy;
        this.prefix = prefix;
        this.sequenceWidth = sequenceWidth;
        this.timeZone = timeZone;
    }

    public static DocumentNumberGenerationPolicy fromFieldConfig(String configJson, ObjectMapper objectMapper) {
        Strategy strategy = Strategy.MANUAL;
        String prefix = "";
        int width = 4;
        ZoneId zone = ZoneId.of("UTC");
        if (configJson != null && !configJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(configJson);
                JsonNode gen = root.get("documentNumberGeneration");
                if (gen != null && gen.isObject()) {
                    JsonNode s = gen.get("strategy");
                    if (s != null && s.isTextual()) {
                        strategy = parseStrategy(s.asText());
                    }
                    JsonNode p = gen.get("prefix");
                    if (p != null && p.isTextual()) {
                        prefix = p.asText();
                    }
                    JsonNode w = gen.get("sequenceWidth");
                    if (w != null && w.isNumber()) {
                        int v = w.intValue();
                        width = Math.min(12, Math.max(1, v));
                    }
                    JsonNode tz = gen.get("timeZone");
                    if (tz != null && tz.isTextual() && !tz.asText().isBlank()) {
                        try {
                            zone = ZoneId.of(tz.asText().trim());
                        } catch (Exception ignored) {
                            zone = ZoneId.of("UTC");
                        }
                    }
                }
            } catch (Exception ignored) {
                return new DocumentNumberGenerationPolicy(Strategy.MANUAL, "", 4, ZoneId.of("UTC"));
            }
        }
        return new DocumentNumberGenerationPolicy(strategy, prefix, width, zone);
    }

    private static Strategy parseStrategy(String raw) {
        if (raw == null) {
            return Strategy.MANUAL;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "TIMESTAMP" -> Strategy.TIMESTAMP;
            case "TENANT_SEQUENCE" -> Strategy.TENANT_SEQUENCE;
            case "MONTHLY_SEQUENCE" -> Strategy.MONTHLY_SEQUENCE;
            default -> Strategy.MANUAL;
        };
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public String getPrefix() {
        return prefix != null ? prefix : "";
    }

    public int getSequenceWidth() {
        return sequenceWidth;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public boolean isAutoGenerate() {
        return strategy != Strategy.MANUAL;
    }
}
