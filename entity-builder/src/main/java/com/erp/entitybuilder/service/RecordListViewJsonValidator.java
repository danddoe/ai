package com.erp.entitybuilder.service;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates record list view definition JSON when {@code version} is {@code 1}.
 */
@Component
public class RecordListViewJsonValidator {

    /** Matches {@code entity_fields.slug} column (varchar 100); slugs may use hyphens, dots, etc. */
    private static final int MAX_FIELD_SLUG_LENGTH = 100;
    private static final Set<String> WIDTH_TOKENS = Set.of("narrow", "medium", "wide");
    private static final Set<String> ALIGNS = Set.of("left", "center", "right");

    private final ObjectMapper objectMapper;

    public RecordListViewJsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateOrThrow(String definitionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Definition is not valid JSON");
        }
        if (!root.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Definition must be a JSON object");
        }
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isNumber() || versionNode.intValue() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Definition version must be 1");
        }
        JsonNode columns = root.get("columns");
        if (columns == null || !columns.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Definition requires a columns array");
        }
        Set<Integer> orders = new HashSet<>();
        for (JsonNode col : columns) {
            if (!col.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column must be an object");
            }
            requireFieldSlug(col);
            JsonNode orderNode = col.get("order");
            if (orderNode == null || !orderNode.isNumber() || !orderNode.canConvertToInt()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column requires an integer order");
            }
            int ord = orderNode.intValue();
            if (ord < 0 || ord > 1_000_000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Column order out of range");
            }
            if (!orders.add(ord)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Duplicate column order", java.util.Map.of("order", ord));
            }
            JsonNode label = col.get("label");
            if (label != null && !label.isNull() && (!label.isTextual() || label.asText().length() > 500)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Column label must be a string up to 500 chars");
            }
            JsonNode width = col.get("width");
            if (width != null && !width.isNull()) {
                if (width.isTextual()) {
                    String w = width.asText();
                    if (!WIDTH_TOKENS.contains(w)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid width token", java.util.Map.of("width", w));
                    }
                } else if (width.isNumber() && width.canConvertToInt()) {
                    int px = width.intValue();
                    if (px < 1 || px > 2000) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Width in pixels must be 1–2000");
                    }
                } else {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Column width must be narrow|medium|wide or a pixel number");
                }
            }
            JsonNode align = col.get("align");
            if (align != null && !align.isNull()) {
                if (!align.isTextual() || !ALIGNS.contains(align.asText())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Column align must be left, center, or right");
                }
            }
            for (String boolKey : new String[] {"inlineEditable", "linkToRecord", "visible"}) {
                JsonNode b = col.get(boolKey);
                if (b != null && !b.isNull() && !b.isBoolean()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", boolKey + " must be a boolean");
                }
            }
        }
        JsonNode showActions = root.get("showRowActions");
        if (showActions != null && !showActions.isNull() && !showActions.isBoolean()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "showRowActions must be a boolean");
        }
        JsonNode showRecordId = root.get("showRecordId");
        if (showRecordId != null && !showRecordId.isNull() && !showRecordId.isBoolean()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "showRecordId must be a boolean");
        }
    }

    private static void requireFieldSlug(JsonNode col) {
        JsonNode slug = col.get("fieldSlug");
        if (slug == null || !slug.isTextual() || slug.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column requires fieldSlug");
        }
        String s = slug.asText().trim();
        if (s.length() > MAX_FIELD_SLUG_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "fieldSlug exceeds max length",
                    Map.of("fieldSlug", s, "maxLength", MAX_FIELD_SLUG_LENGTH));
        }
        if (s.chars().anyMatch(ch -> ch < 32 || Character.isWhitespace(ch))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid fieldSlug", Map.of("fieldSlug", s));
        }
    }
}
