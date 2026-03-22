package com.erp.entitybuilder.service;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates region-based form layout JSON when {@code version} is {@code 2}. Legacy layouts without {@code version: 2} are skipped.
 */
@Component
public class FormLayoutJsonValidator {

    private static final Set<String> REGION_ROLES = Set.of("header", "detail", "tab");
    private static final Set<String> LAYOUT_ACTIONS = Set.of("save", "cancel", "link");
    private static final Set<String> ACTION_VARIANTS = Set.of("primary", "secondary", "link");

    private final ObjectMapper objectMapper;

    public FormLayoutJsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateOrThrow(String layoutJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(layoutJson);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Layout is not valid JSON");
        }
        if (!root.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Layout must be a JSON object");
        }
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isNumber() || versionNode.intValue() != 2) {
            return;
        }
        JsonNode regions = root.get("regions");
        if (regions == null || !regions.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Layout v2 requires a regions array");
        }
        java.util.Set<String> regionIds = new java.util.HashSet<>();
        for (JsonNode region : regions) {
            if (!region.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each region must be an object");
            }
            requireText(region, "id", "region.id");
            String rid = region.get("id").asText();
            regionIds.add(rid);
            requireText(region, "role", "region.role");
            String role = region.get("role").asText();
            if (!REGION_ROLES.contains(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid region role", java.util.Map.of("role", role));
            }
            JsonNode rows = region.get("rows");
            if (rows == null || !rows.isArray()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each region must have a rows array");
            }
            for (JsonNode row : rows) {
                validateRow(row);
            }
        }
        validateRuntime(root, regionIds);
    }

    private static void validateRuntime(JsonNode root, java.util.Set<String> regionIds) {
        JsonNode runtime = root.get("runtime");
        if (runtime == null || runtime.isNull()) {
            return;
        }
        if (!runtime.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime must be an object");
        }
        JsonNode recordEntry = runtime.get("recordEntry");
        if (recordEntry == null || recordEntry.isNull()) {
            return;
        }
        if (!recordEntry.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime.recordEntry must be an object");
        }
        JsonNode flowNode = recordEntry.get("flow");
        if (flowNode == null || !flowNode.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime.recordEntry.flow must be a string");
        }
        String flow = flowNode.asText();
        if (!"free".equals(flow) && !"wizard".equals(flow)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime.recordEntry.flow must be free or wizard", java.util.Map.of("flow", flow));
        }
        if ("wizard".equals(flow)) {
            JsonNode wizard = recordEntry.get("wizard");
            if (wizard == null || !wizard.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime.recordEntry.wizard is required when flow is wizard");
            }
            JsonNode steps = wizard.get("stepOrderRegionIds");
            if (steps == null || !steps.isArray() || steps.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "runtime.recordEntry.wizard.stepOrderRegionIds must be a non-empty array");
            }
            for (JsonNode stepId : steps) {
                if (!stepId.isTextual() || stepId.asText().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each stepOrderRegionIds entry must be a non-blank string");
                }
                String sid = stepId.asText();
                if (!regionIds.contains(sid)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "wizard step references unknown region id", java.util.Map.of("regionId", sid));
                }
            }
        }
    }

    private static void validateRow(JsonNode row) {
        if (!row.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each row must be an object");
        }
        requireText(row, "id", "row.id");
        JsonNode columns = row.get("columns");
        if (columns == null || !columns.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each row must have a columns array");
        }
        for (JsonNode col : columns) {
            validateColumn(col);
        }
    }

    private static void validateColumn(JsonNode col) {
        if (!col.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column must be an object");
        }
        requireText(col, "id", "column.id");
        JsonNode span = col.get("span");
        if (span == null || !span.isNumber() || span.intValue() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column must have a positive numeric span");
        }
        JsonNode items = col.get("items");
        if (items == null || !items.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each column must have an items array");
        }
        for (JsonNode item : items) {
            validateItem(item);
        }
    }

    private static void validateItem(JsonNode item) {
        if (!item.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Each item must be an object");
        }
        requireText(item, "id", "item.id");
        JsonNode kindNode = item.get("kind");
        if (kindNode != null && kindNode.isTextual() && "action".equals(kindNode.asText())) {
            validateActionItem(item);
            return;
        }
        JsonNode rl = item.get("referenceLookup");
        if (rl != null) {
            if (!rl.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "item.referenceLookup must be an object");
            }
            JsonNode tid = rl.get("targetEntityId");
            if (tid != null) {
                if (!tid.isTextual() || tid.asText().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "referenceLookup.targetEntityId must be a non-blank string UUID");
                }
                try {
                    java.util.UUID.fromString(tid.asText());
                } catch (Exception e) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "referenceLookup.targetEntityId must be a valid UUID");
                }
            }
            JsonNode dt = rl.get("displayTemplate");
            if (dt != null && !dt.isTextual()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "referenceLookup.displayTemplate must be a string");
            }
            JsonNode sfs = rl.get("searchFieldSlugs");
            if (sfs != null) {
                if (!sfs.isArray()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "referenceLookup.searchFieldSlugs must be an array");
                }
                for (JsonNode x : sfs) {
                    if (!x.isTextual()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "referenceLookup.searchFieldSlugs entries must be strings");
                    }
                }
            }
        }
    }

    private static void validateActionItem(JsonNode item) {
        requireText(item, "label", "action item label");
        JsonNode ac = item.get("action");
        if (ac == null || !ac.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Action item requires action (save, cancel, or link)");
        }
        String a = ac.asText();
        if (!LAYOUT_ACTIONS.contains(a)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid layout action", java.util.Map.of("action", a));
        }
        if ("link".equals(a)) {
            JsonNode href = item.get("href");
            if (href == null || !href.isTextual() || href.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Link action requires a non-blank href");
            }
            if (!isSafeActionHref(href.asText())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Link href must be https URL, http URL, or absolute path starting with / (not //)");
            }
        }
        JsonNode ont = item.get("openInNewTab");
        if (ont != null && !ont.isBoolean()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "action item openInNewTab must be a boolean");
        }
        JsonNode var = item.get("variant");
        if (var != null && (!var.isTextual() || !ACTION_VARIANTS.contains(var.asText()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid action variant", java.util.Map.of("variant", var.asText()));
        }
    }

    /**
     * Allows same-origin style paths and http(s); rejects javascript: and protocol-relative // URLs.
     */
    static boolean isSafeActionHref(String href) {
        String t = href.trim();
        if (t.startsWith("/") && !t.startsWith("//")) {
            return true;
        }
        try {
            java.net.URI u = java.net.URI.create(t);
            String scheme = u.getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    private static void requireText(JsonNode object, String field, String label) {
        JsonNode n = object.get(field);
        if (n == null || !n.isTextual() || n.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Missing or invalid " + label);
        }
    }
}
