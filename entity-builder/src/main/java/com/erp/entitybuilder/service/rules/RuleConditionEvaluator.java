package com.erp.entitybuilder.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluates shared JSON conditions against a flat field-slug → value map (record payload).
 * <p>
 * Schema:
 * <ul>
 *   <li>{@code { "op": "and" | "or", "children": [ condition, ... ] }}</li>
 *   <li>{@code { "op": "cmp", "field": "&lt;slug&gt;", "operator": "eq"|"neq"|"gt"|"gte"|"lt"|"lte"|"isEmpty"|"isNotEmpty", "value": &lt;optional json&gt; }}</li>
 * </ul>
 */
@Component
public class RuleConditionEvaluator {

    private final ObjectMapper objectMapper;

    public RuleConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean evaluate(String conditionJson, Map<String, Object> values) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(conditionJson);
            return evalNode(root, values);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid business rule condition JSON");
        }
    }

    private boolean evalNode(JsonNode node, Map<String, Object> values) {
        if (node == null || !node.isObject()) {
            return false;
        }
        String op = readRequiredText(node, "op");
        if (op == null) {
            return false;
        }
        return switch (op) {
            case "and" -> evalAnd(node.get("children"), values);
            case "or" -> evalOr(node.get("children"), values);
            case "cmp" -> evalCmp(node, values);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "Unsupported condition op: " + op, Map.of("op", op));
        };
    }

    private boolean evalAnd(JsonNode children, Map<String, Object> values) {
        if (children == null || !children.isArray() || children.isEmpty()) {
            return true;
        }
        for (JsonNode c : children) {
            if (!evalNode(c, values)) {
                return false;
            }
        }
        return true;
    }

    private boolean evalOr(JsonNode children, Map<String, Object> values) {
        if (children == null || !children.isArray() || children.isEmpty()) {
            return false;
        }
        for (JsonNode c : children) {
            if (evalNode(c, values)) {
                return true;
            }
        }
        return false;
    }

    private boolean evalCmp(JsonNode node, Map<String, Object> values) {
        String field = readRequiredText(node, "field");
        String operator = readRequiredText(node, "operator");
        if (field == null || operator == null) {
            return false;
        }
        Object actual = values.get(field);
        JsonNode expectedNode = node.get("value");
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "isempty" -> isEmptyValue(actual);
            case "isnotempty" -> !isEmptyValue(actual);
            case "eq" -> compareEqual(actual, jsonToComparable(expectedNode));
            case "neq" -> !compareEqual(actual, jsonToComparable(expectedNode));
            case "gt" -> compareNumeric(actual, expectedNode) > 0;
            case "gte" -> compareNumeric(actual, expectedNode) >= 0;
            case "lt" -> compareNumeric(actual, expectedNode) < 0;
            case "lte" -> compareNumeric(actual, expectedNode) <= 0;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "Unsupported cmp operator: " + operator, Map.of("operator", operator));
        };
    }

    private static boolean isEmptyValue(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.trim().isEmpty();
        }
        return false;
    }

    private static String readRequiredText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return null;
    }

    private Object jsonToComparable(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isIntegralNumber()) {
            return n.longValue();
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isTextual()) {
            return n.asText();
        }
        return n.toString();
    }

    private boolean compareEqual(Object actual, Object expected) {
        Object a = normalizeUuidish(actual);
        Object e = normalizeUuidish(expected);
        if (a instanceof BigDecimal ba && e instanceof BigDecimal be) {
            return ba.compareTo(be) == 0;
        }
        if (a instanceof Number na && e instanceof Number ne) {
            return new BigDecimal(na.toString()).compareTo(new BigDecimal(ne.toString())) == 0;
        }
        return Objects.equals(a, e);
    }

    private static Object normalizeUuidish(Object v) {
        if (v instanceof UUID u) {
            return u.toString();
        }
        if (v instanceof String s) {
            try {
                return UUID.fromString(s).toString();
            } catch (IllegalArgumentException ignored) {
                return s;
            }
        }
        return v;
    }

    private int compareNumeric(Object actual, JsonNode expectedNode) {
        BigDecimal a = coerceNumber(actual);
        BigDecimal b = coerceNumber(jsonToComparable(expectedNode));
        if (a == null || b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "Numeric comparison requires numeric values");
        }
        return a.compareTo(b);
    }

    private static BigDecimal coerceNumber(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
