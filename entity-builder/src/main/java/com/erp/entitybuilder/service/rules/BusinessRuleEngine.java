package com.erp.entitybuilder.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.entitybuilder.domain.BusinessRule;
import com.erp.entitybuilder.domain.BusinessRuleAction;
import com.erp.entitybuilder.domain.BusinessRuleActionType;
import com.erp.entitybuilder.domain.BusinessRuleTrigger;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.repository.BusinessRuleRepository;
import com.erp.entitybuilder.service.BusinessRuleListSupport;
import com.erp.entitybuilder.service.FieldTypes;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads active rules and applies server-side actions to a mutable value map.
 */
@Service
public class BusinessRuleEngine {

    private final BusinessRuleRepository businessRuleRepository;
    private final RuleConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;

    public BusinessRuleEngine(
            BusinessRuleRepository businessRuleRepository,
            RuleConditionEvaluator conditionEvaluator,
            ObjectMapper objectMapper
    ) {
        this.businessRuleRepository = businessRuleRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
    }

    /**
     * Applies all active rules for the trigger with at least one {@code applyServer} action.
     * Mutates {@code values} for {@link BusinessRuleActionType#SERVER_SET_FIELD_VALUE}.
     *
     * @throws ApiException BAD_REQUEST for {@link BusinessRuleActionType#SERVER_ADD_ERROR}
     */
    public void applyServerRules(
            UUID tenantId,
            UUID entityId,
            BusinessRuleTrigger trigger,
            Map<String, Object> values,
            Map<String, EntityField> fieldBySlug
    ) {
        List<BusinessRule> rules = BusinessRuleListSupport.dedupeRulesSorted(
                businessRuleRepository.findActiveWithActionsByTenantAndEntity(tenantId, entityId));
        List<BusinessRule> matching = new ArrayList<>();
        for (BusinessRule r : rules) {
            if (r.getTrigger() == trigger && r.getActions().stream().anyMatch(BusinessRuleAction::isApplyServer)) {
                matching.add(r);
            }
        }
        matching.sort(Comparator.comparingInt(BusinessRule::getPriority)
                .thenComparing(BusinessRule::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        for (BusinessRule rule : matching) {
            if (!conditionEvaluator.evaluate(rule.getConditionJson(), values)) {
                continue;
            }
            List<BusinessRuleAction> actions = new ArrayList<>(rule.getActions());
            actions.sort(Comparator.comparingInt(BusinessRuleAction::getPriority)
                    .thenComparing(BusinessRuleAction::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            for (BusinessRuleAction action : actions) {
                if (!action.isApplyServer()) {
                    continue;
                }
                applyServerAction(rule.getId(), action, values, fieldBySlug);
            }
        }
    }

    private void applyServerAction(
            UUID ruleId,
            BusinessRuleAction action,
            Map<String, Object> values,
            Map<String, EntityField> fieldBySlug
    ) {
        BusinessRuleActionType type = action.getActionType();
        JsonNode payload;
        try {
            payload = objectMapper.readTree(action.getPayload() != null ? action.getPayload() : "{}");
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            if (ruleId != null) {
                err.put("ruleId", ruleId);
            }
            if (action.getId() != null) {
                err.put("actionId", action.getId());
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid action payload JSON", err);
        }
        switch (type) {
            case SERVER_SET_FIELD_VALUE -> {
                String target = text(payload, "targetField");
                if (target == null || target.isBlank()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    if (ruleId != null) {
                        d.put("ruleId", ruleId);
                    }
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "SERVER_SET_FIELD_VALUE requires targetField", d);
                }
                EntityField field = fieldBySlug.get(target);
                if (field == null) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    if (ruleId != null) {
                        d.put("ruleId", ruleId);
                    }
                    d.put("field", target);
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Rule references unknown field", d);
                }
                if (FieldStorage.isCoreDomain(field) || FieldTypes.isDocumentNumber(field)
                        || FieldTypes.isOptimisticVersionField(field)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                            "SERVER_SET_FIELD_VALUE cannot target this field", Map.of("field", target));
                }
                JsonNode valueNode = payload.get("value");
                values.put(target, jsonNodeToFieldValue(field.getFieldType(), valueNode));
            }
            case SERVER_ADD_ERROR -> {
                String message = text(payload, "message");
                if (message == null || message.isBlank()) {
                    message = "Record rejected by business rule";
                }
                String field = text(payload, "field");
                Map<String, Object> details = new LinkedHashMap<>();
                if (ruleId != null) {
                    details.put("ruleId", ruleId);
                }
                details.put("action", "SERVER_ADD_ERROR");
                if (field != null && !field.isBlank()) {
                    details.put("field", field);
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", message, details);
            }
            default -> {
                Map<String, Object> d = new LinkedHashMap<>();
                if (ruleId != null) {
                    d.put("ruleId", ruleId);
                }
                d.put("actionType", type.name());
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "Action type is not allowed on server surface: " + type, d);
            }
        }
    }

    private Object jsonNodeToFieldValue(String fieldType, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String t = FieldTypes.normalizeSqlFieldType(fieldType);
        if (FieldTypes.isNumericFieldType(fieldType)) {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            return new BigDecimal(node.asText().trim());
        }
        return switch (t) {
            case "string", "text", "document_number" -> node.isTextual()
                    ? node.asText()
                    : (node.isNumber() || node.isBoolean() ? node.asText() : String.valueOf(node));
            case "boolean" -> node.isBoolean() ? node.booleanValue() : Boolean.parseBoolean(node.asText());
            case "date", "datetime" -> {
                if (node.isTextual()) {
                    yield Instant.parse(node.asText());
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Date/datetime value must be ISO-8601 text");
            }
            case "reference" -> {
                String s = node.isTextual() ? node.asText() : node.asText();
                yield UUID.fromString(s);
            }
            default -> node.isValueNode() ? node.asText() : node.toString();
        };
    }

    private static String text(JsonNode node, String field) {
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
        return n.toString();
    }
}
