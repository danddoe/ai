package com.erp.entitybuilder.service.query;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.RecordQueryDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class RecordFilterValidator {

    public static final int MAX_DEPTH = 4;
    public static final int MAX_CHILDREN_PER_GROUP = 32;
    public static final int MAX_TOTAL_CLAUSES = 32;
    public static final int MAX_IN_SIZE = 50;

    public ResolvedFilter validate(
            RecordQueryDtos.FilterNode raw,
            List<EntityField> fields,
            boolean piiReadPermission
    ) {
        if (raw == null) {
            return null;
        }
        Map<String, EntityField> bySlug = new HashMap<>();
        for (EntityField f : fields) {
            bySlug.put(f.getSlug(), f);
        }
        ClauseCounter counter = new ClauseCounter();
        return validateNode(raw, bySlug, piiReadPermission, 0, counter);
    }

    private static ResolvedFilter validateNode(
            RecordQueryDtos.FilterNode raw,
            Map<String, EntityField> bySlug,
            boolean piiReadPermission,
            int depth,
            ClauseCounter counter
    ) {
        if (depth > MAX_DEPTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Filter nesting too deep", Map.of("maxDepth", MAX_DEPTH));
        }
        boolean hasChildren = raw.getChildren() != null && !raw.getChildren().isEmpty();
        boolean hasField = raw.getField() != null && !raw.getField().isBlank();
        if (hasChildren == hasField) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "Each filter node must be either a group (children) or a clause (field), not both/neither");
        }
        if (hasChildren) {
            return validateGroup(raw, bySlug, piiReadPermission, depth, counter);
        }
        return validateFieldOrMetadataClause(raw, bySlug, piiReadPermission, counter);
    }

    private static ResolvedFilter.ResolvedGroup validateGroup(
            RecordQueryDtos.FilterNode raw,
            Map<String, EntityField> bySlug,
            boolean piiReadPermission,
            int depth,
            ClauseCounter counter
    ) {
        String gop = raw.getOp();
        if (gop == null || gop.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Group op required", Map.of("op", "and|or"));
        }
        String opNorm = gop.trim().toLowerCase(Locale.ROOT);
        if (!"and".equals(opNorm) && !"or".equals(opNorm)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid group op", Map.of("op", gop));
        }
        if (raw.getValue() != null && !raw.getValue().isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Group must not have value");
        }
        List<RecordQueryDtos.FilterNode> ch = raw.getChildren();
        if (ch.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Group must have at least one child");
        }
        if (ch.size() > MAX_CHILDREN_PER_GROUP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Too many children in group",
                    Map.of("max", MAX_CHILDREN_PER_GROUP));
        }
        List<ResolvedFilter> resolved = new ArrayList<>();
        for (RecordQueryDtos.FilterNode c : ch) {
            resolved.add(validateNode(c, bySlug, piiReadPermission, depth + 1, counter));
        }
        return new ResolvedFilter.ResolvedGroup(opNorm, List.copyOf(resolved));
    }

    private static ResolvedFilter validateFieldOrMetadataClause(
            RecordQueryDtos.FilterNode raw,
            Map<String, EntityField> bySlug,
            boolean piiReadPermission,
            ClauseCounter counter
    ) {
        counter.count++;
        if (counter.count > MAX_TOTAL_CLAUSES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Too many filter clauses",
                    Map.of("max", MAX_TOTAL_CLAUSES));
        }
        String fieldKey = raw.getField().trim();
        ResolvedFilter.RecordMetadataField metaField = ResolvedFilter.RecordMetadataField.fromRequestField(fieldKey);
        if (metaField != null) {
            return validateMetadataClause(raw, metaField, counter);
        }

        EntityField ef = bySlug.get(fieldKey);
        if (ef == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug", Map.of("field", raw.getField()));
        }
        if (FieldStorage.isCoreDomain(ef)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "Cannot filter on CORE_DOMAIN field; values are not stored in entity-builder",
                    Map.of("field", ef.getSlug()));
        }
        if (ef.isPii() && !piiReadPermission) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Cannot filter on PII field without pii:read",
                    Map.of("field", ef.getSlug()));
        }
        String cop = raw.getOp();
        if (cop == null || cop.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Clause op required", Map.of("field", ef.getSlug()));
        }
        String opNorm = cop.trim().toLowerCase(Locale.ROOT);
        ResolvedFilter.ValueKind kind = valueKind(ef.getFieldType());
        ResolvedFilter.ClauseOp clauseOp = parseClauseOp(opNorm);
        allowOpForKind(kind, clauseOp, ef.getSlug());

        List<Object> params = bindClauseValues(kind, clauseOp, raw.getValue(), ef.getSlug());
        return new ResolvedFilter.ResolvedClause(ef.getId(), kind, clauseOp, params);
    }

    private static ResolvedFilter.ResolvedMetadataClause validateMetadataClause(
            RecordQueryDtos.FilterNode raw,
            ResolvedFilter.RecordMetadataField metaField,
            ClauseCounter counter
    ) {
        counter.count++;
        if (counter.count > MAX_TOTAL_CLAUSES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Too many filter clauses",
                    Map.of("max", MAX_TOTAL_CLAUSES));
        }
        String slug = metaField.requestKey();
        String cop = raw.getOp();
        if (cop == null || cop.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Clause op required", Map.of("field", slug));
        }
        String opNorm = cop.trim().toLowerCase(Locale.ROOT);
        ResolvedFilter.ValueKind kind = switch (metaField) {
            case CREATED_AT, UPDATED_AT -> ResolvedFilter.ValueKind.DATE;
            case CREATED_BY, UPDATED_BY -> ResolvedFilter.ValueKind.REFERENCE;
        };
        ResolvedFilter.ClauseOp clauseOp = parseClauseOp(opNorm);
        allowOpForKind(kind, clauseOp, slug);
        List<Object> params = bindClauseValues(kind, clauseOp, raw.getValue(), slug);
        return new ResolvedFilter.ResolvedMetadataClause(metaField, clauseOp, List.copyOf(params));
    }

    private static ResolvedFilter.ValueKind valueKind(String fieldType) {
        String ft = fieldType != null ? fieldType.toLowerCase(Locale.ROOT) : "";
        return switch (ft) {
            case "string", "text" -> ResolvedFilter.ValueKind.TEXT;
            case "number" -> ResolvedFilter.ValueKind.NUMBER;
            case "date", "datetime" -> ResolvedFilter.ValueKind.DATE;
            case "boolean" -> ResolvedFilter.ValueKind.BOOLEAN;
            case "reference" -> ResolvedFilter.ValueKind.REFERENCE;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unsupported field type for filtering",
                    Map.of("fieldType", fieldType));
        };
    }

    private static ResolvedFilter.ClauseOp parseClauseOp(String op) {
        return switch (op) {
            case "eq" -> ResolvedFilter.ClauseOp.EQ;
            case "ne" -> ResolvedFilter.ClauseOp.NE;
            case "gt" -> ResolvedFilter.ClauseOp.GT;
            case "gte" -> ResolvedFilter.ClauseOp.GTE;
            case "lt" -> ResolvedFilter.ClauseOp.LT;
            case "lte" -> ResolvedFilter.ClauseOp.LTE;
            case "between" -> ResolvedFilter.ClauseOp.BETWEEN;
            case "in" -> ResolvedFilter.ClauseOp.IN;
            case "contains" -> ResolvedFilter.ClauseOp.CONTAINS;
            case "starts_with" -> ResolvedFilter.ClauseOp.STARTS_WITH;
            case "is_null" -> ResolvedFilter.ClauseOp.IS_NULL;
            case "is_not_null" -> ResolvedFilter.ClauseOp.IS_NOT_NULL;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown clause op", Map.of("op", op));
        };
    }

    private static void allowOpForKind(ResolvedFilter.ValueKind kind, ResolvedFilter.ClauseOp op, String slug) {
        boolean ok = switch (kind) {
            case TEXT -> switch (op) {
                case EQ, NE, CONTAINS, STARTS_WITH, IS_NULL, IS_NOT_NULL -> true;
                default -> false;
            };
            case NUMBER, DATE -> switch (op) {
                case EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL -> true;
                default -> false;
            };
            case BOOLEAN -> switch (op) {
                case EQ, NE, IS_NULL, IS_NOT_NULL -> true;
                default -> false;
            };
            case REFERENCE -> switch (op) {
                case EQ, NE, IN, IS_NULL, IS_NOT_NULL -> true;
                default -> false;
            };
        };
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Operator not allowed for field type",
                    Map.of("field", slug, "clauseOp", op.name()));
        }
    }

    private static List<Object> bindClauseValues(
            ResolvedFilter.ValueKind kind,
            ResolvedFilter.ClauseOp op,
            JsonNode value,
            String slug
    ) {
        if (op == ResolvedFilter.ClauseOp.IS_NULL || op == ResolvedFilter.ClauseOp.IS_NOT_NULL) {
            if (value != null && !value.isNull()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "is_null / is_not_null must not have value",
                        Map.of("field", slug));
            }
            return List.of();
        }
        if (value == null || value.isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Value required", Map.of("field", slug));
        }
        return switch (op) {
            case BETWEEN -> parseBetween(kind, value, slug);
            case IN -> parseIn(kind, value, slug);
            case CONTAINS, STARTS_WITH -> {
                if (!value.isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Text value required", Map.of("field", slug));
                }
                yield List.of(value.asText());
            }
            case EQ, NE, GT, GTE, LT, LTE -> List.of(parseScalar(kind, value, slug));
            default -> throw new IllegalStateException();
        };
    }

    private static List<Object> parseBetween(ResolvedFilter.ValueKind kind, JsonNode value, String slug) {
        if (!value.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "between requires array of two values", Map.of("field", slug));
        }
        if (value.size() != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "between requires exactly two values", Map.of("field", slug));
        }
        Object a = parseScalar(kind, value.get(0), slug);
        Object b = parseScalar(kind, value.get(1), slug);
        return List.of(a, b);
    }

    private static List<Object> parseIn(ResolvedFilter.ValueKind kind, JsonNode value, String slug) {
        if (!value.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "in requires array", Map.of("field", slug));
        }
        if (value.isEmpty() || value.size() > MAX_IN_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "in array size invalid",
                    Map.of("field", slug, "max", MAX_IN_SIZE));
        }
        List<Object> out = new ArrayList<>();
        for (JsonNode n : value) {
            out.add(parseScalar(kind, n, slug));
        }
        return out;
    }

    private static Object parseScalar(ResolvedFilter.ValueKind kind, JsonNode n, String slug) {
        if (n == null || n.isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Null value not allowed", Map.of("field", slug));
        }
        return switch (kind) {
            case TEXT -> {
                if (!n.isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "String value required", Map.of("field", slug));
                }
                yield n.asText();
            }
            case NUMBER -> {
                if (n.isNumber()) {
                    yield n.decimalValue();
                }
                if (n.isTextual()) {
                    try {
                        yield new BigDecimal(n.asText());
                    } catch (NumberFormatException e) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid number", Map.of("field", slug));
                    }
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Number value required", Map.of("field", slug));
            }
            case DATE -> {
                if (!n.isTextual()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "ISO-8601 date string required", Map.of("field", slug));
                }
                try {
                    yield Instant.parse(n.asText());
                } catch (Exception e) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid instant", Map.of("field", slug));
                }
            }
            case BOOLEAN -> {
                if (!n.isBoolean()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Boolean value required", Map.of("field", slug));
                }
                yield n.booleanValue();
            }
            case REFERENCE -> {
                if (n.isTextual()) {
                    try {
                        yield UUID.fromString(n.asText());
                    } catch (IllegalArgumentException e) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid UUID", Map.of("field", slug));
                    }
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "UUID string required", Map.of("field", slug));
            }
        };
    }

    private static final class ClauseCounter {
        int count;
    }
}
