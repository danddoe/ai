package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.BusinessRule;
import com.erp.entitybuilder.domain.BusinessRuleAction;
import com.erp.entitybuilder.domain.BusinessRuleActionType;
import com.erp.entitybuilder.domain.BusinessRuleTrigger;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.FormLayout;
import com.erp.entitybuilder.repository.BusinessRuleRepository;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.FormLayoutRepository;
import com.erp.entitybuilder.security.EntityBuilderSecurity;
import com.erp.entitybuilder.service.rules.RuleConditionEvaluator;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.BusinessRuleDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessRuleService {

    private static final EnumSet<BusinessRuleTrigger> UI_TRIGGERS =
            EnumSet.of(BusinessRuleTrigger.ON_FORM_LOAD, BusinessRuleTrigger.ON_FORM_CHANGE);
    private static final EnumSet<BusinessRuleTrigger> SERVER_TRIGGERS =
            EnumSet.of(BusinessRuleTrigger.BEFORE_CREATE, BusinessRuleTrigger.BEFORE_UPDATE);

    private final BusinessRuleRepository businessRuleRepository;
    private final EntityDefinitionRepository entityRepository;
    private final FormLayoutRepository formLayoutRepository;
    private final EntitySchemaService entitySchemaService;
    private final EntityBuilderSecurity entityBuilderSecurity;
    private final RuleConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;

    public BusinessRuleService(
            BusinessRuleRepository businessRuleRepository,
            EntityDefinitionRepository entityRepository,
            FormLayoutRepository formLayoutRepository,
            EntitySchemaService entitySchemaService,
            EntityBuilderSecurity entityBuilderSecurity,
            RuleConditionEvaluator conditionEvaluator,
            ObjectMapper objectMapper
    ) {
        this.businessRuleRepository = businessRuleRepository;
        this.entityRepository = entityRepository;
        this.formLayoutRepository = formLayoutRepository;
        this.entitySchemaService = entitySchemaService;
        this.entityBuilderSecurity = entityBuilderSecurity;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BusinessRuleDtos.BusinessRuleDto> list(
            UUID tenantId,
            UUID entityId,
            BusinessRuleSurfaceFilter surface,
            UUID formLayoutId,
            boolean activeOnly
    ) {
        resolveEntity(tenantId, entityId);
        List<BusinessRule> rules = BusinessRuleListSupport.dedupeRulesSorted(
                businessRuleRepository.findAllWithActionsByTenantAndEntity(tenantId, entityId));
        List<BusinessRuleDtos.BusinessRuleDto> out = new ArrayList<>();
        for (BusinessRule r : rules) {
            if (activeOnly && !r.isActive()) {
                continue;
            }
            if (!matchesSurface(r, surface)) {
                continue;
            }
            if (!matchesLayout(r, formLayoutId)) {
                continue;
            }
            out.add(toDto(r));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public BusinessRuleDtos.BusinessRuleDto get(UUID tenantId, UUID entityId, UUID ruleId) {
        resolveEntity(tenantId, entityId);
        BusinessRule r = businessRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .filter(rule -> rule.getEntityId().equals(entityId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Business rule not found"));
        return toDto(r);
    }

    @Transactional
    public BusinessRuleDtos.BusinessRuleDto create(UUID tenantId, UUID entityId, BusinessRuleDtos.CreateBusinessRuleRequest req) {
        EntityDefinition entity = resolveEntity(tenantId, entityId);
        assertCanMutate(entity);
        validateLayoutScope(tenantId, entityId, req.formLayoutId());
        conditionEvaluator.evaluate(writeJson(req.condition()), Map.of());

        BusinessRule rule = new BusinessRule();
        rule.setTenantId(tenantId);
        rule.setEntityId(entityId);
        rule.setFormLayoutId(req.formLayoutId());
        rule.setName(req.name().trim());
        rule.setDescription(req.description() != null ? req.description().trim() : null);
        rule.setPriority(req.priority());
        rule.setTrigger(req.trigger());
        rule.setConditionJson(writeJson(req.condition()));
        rule.setActive(req.active());

        for (BusinessRuleDtos.CreateBusinessRuleActionRequest ar : req.actions()) {
            rule.addAction(toNewAction(ar));
        }
        validateRuleActions(rule);
        BusinessRule saved = businessRuleRepository.save(rule);
        return toDto(saved);
    }

    @Transactional
    public BusinessRuleDtos.BusinessRuleDto update(
            UUID tenantId,
            UUID entityId,
            UUID ruleId,
            BusinessRuleDtos.UpdateBusinessRuleRequest req
    ) {
        EntityDefinition entity = resolveEntity(tenantId, entityId);
        assertCanMutate(entity);
        BusinessRule rule = businessRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .filter(r -> r.getEntityId().equals(entityId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Business rule not found"));

        if (req.name() != null) {
            rule.setName(req.name().trim());
        }
        if (req.description() != null) {
            rule.setDescription(req.description().trim());
        }
        if (req.priority() != null) {
            rule.setPriority(req.priority());
        }
        if (req.trigger() != null) {
            rule.setTrigger(req.trigger());
        }
        if (req.condition() != null) {
            Map<String, Object> cond = req.condition();
            conditionEvaluator.evaluate(writeJson(cond), Map.of());
            rule.setConditionJson(writeJson(cond));
        }
        if (req.formLayoutId() != null) {
            validateLayoutScope(tenantId, entityId, req.formLayoutId());
            rule.setFormLayoutId(req.formLayoutId());
        }
        if (req.active() != null) {
            rule.setActive(req.active());
        }
        if (req.actions() != null) {
            rule.getActions().clear();
            for (BusinessRuleDtos.UpdateBusinessRuleActionRequest ar : req.actions()) {
                BusinessRuleAction a = new BusinessRuleAction();
                a.setPriority(ar.priority());
                a.setActionType(ar.actionType());
                a.setPayload(writeJson(ar.payload() != null ? ar.payload() : Map.of()));
                applySurfaceFlags(a, ar.actionType(), ar.applyUi(), ar.applyServer());
                rule.addAction(a);
            }
        }
        validateRuleActions(rule);
        return toDto(businessRuleRepository.save(rule));
    }

    @Transactional
    public void delete(UUID tenantId, UUID entityId, UUID ruleId) {
        EntityDefinition entity = resolveEntity(tenantId, entityId);
        assertCanMutate(entity);
        BusinessRule rule = businessRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .filter(r -> r.getEntityId().equals(entityId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Business rule not found"));
        businessRuleRepository.delete(rule);
    }

    private EntityDefinition resolveEntity(UUID tenantId, UUID entityId) {
        entitySchemaService.resolveEntityForTenantAccess(tenantId, entityId);
        return entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
    }

    private void assertCanMutate(EntityDefinition entity) {
        if (!entityBuilderSecurity.canMutateEntitySchema(entity)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
                    "Cannot modify business rules for this entity");
        }
    }

    private void validateLayoutScope(UUID tenantId, UUID entityId, UUID formLayoutId) {
        if (formLayoutId == null) {
            return;
        }
        FormLayout layout = formLayoutRepository.findByIdAndTenantId(formLayoutId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Form layout not found"));
        if (!layout.getEntityId().equals(entityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Form layout does not belong to entity");
        }
    }

    private static boolean matchesSurface(BusinessRule r, BusinessRuleSurfaceFilter surface) {
        return switch (surface) {
            case ALL -> true;
            case UI -> UI_TRIGGERS.contains(r.getTrigger());
            case SERVER -> SERVER_TRIGGERS.contains(r.getTrigger());
        };
    }

    private static boolean matchesLayout(BusinessRule r, UUID formLayoutId) {
        if (formLayoutId == null) {
            return true;
        }
        return r.getFormLayoutId() == null || r.getFormLayoutId().equals(formLayoutId);
    }

    private BusinessRuleAction toNewAction(BusinessRuleDtos.CreateBusinessRuleActionRequest ar) {
        BusinessRuleAction a = new BusinessRuleAction();
        a.setPriority(ar.priority());
        a.setActionType(ar.actionType());
        a.setPayload(writeJson(ar.payload()));
        applySurfaceFlags(a, ar.actionType(), ar.applyUi(), ar.applyServer());
        return a;
    }

    private static void applySurfaceFlags(
            BusinessRuleAction a,
            BusinessRuleActionType type,
            Boolean applyUi,
            Boolean applyServer
    ) {
        boolean ui;
        boolean server;
        if (type.name().startsWith("UI_")) {
            ui = applyUi == null || applyUi;
            server = Boolean.TRUE.equals(applyServer);
        } else if (type.name().startsWith("SERVER_")) {
            server = applyServer == null || applyServer;
            ui = Boolean.TRUE.equals(applyUi);
        } else {
            ui = Boolean.TRUE.equals(applyUi);
            server = Boolean.TRUE.equals(applyServer);
        }
        a.setApplyUi(ui);
        a.setApplyServer(server);
    }

    private void validateRuleActions(BusinessRule rule) {
        for (BusinessRuleAction a : rule.getActions()) {
            BusinessRuleActionType t = a.getActionType();
            if (t.name().startsWith("SERVER_") && !a.isApplyServer()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "Server action must have applyServer true", Map.of("actionType", t.name()));
            }
            if (t.name().startsWith("UI_") && !a.isApplyUi()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "UI action must have applyUi true", Map.of("actionType", t.name()));
            }
            if (SERVER_TRIGGERS.contains(rule.getTrigger()) && t.name().startsWith("UI_")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "UI actions are not valid on server triggers", Map.of("trigger", rule.getTrigger().name()));
            }
            if (UI_TRIGGERS.contains(rule.getTrigger()) && t.name().startsWith("SERVER_")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "Server actions are not valid on UI triggers", Map.of("trigger", rule.getTrigger().name()));
            }
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Could not serialize JSON");
        }
    }

    private Map<String, Object> readJsonObject(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BusinessRuleDtos.BusinessRuleDto toDto(BusinessRule r) {
        List<BusinessRuleDtos.BusinessRuleActionDto> actions = r.getActions().stream()
                .sorted(java.util.Comparator.comparingInt(BusinessRuleAction::getPriority)
                        .thenComparing(BusinessRuleAction::getId))
                .map(a -> new BusinessRuleDtos.BusinessRuleActionDto(
                        a.getId(),
                        a.getPriority(),
                        a.getActionType(),
                        readJsonObject(a.getPayload()),
                        a.isApplyUi(),
                        a.isApplyServer()
                ))
                .toList();

        return new BusinessRuleDtos.BusinessRuleDto(
                r.getId(),
                r.getTenantId(),
                r.getEntityId(),
                r.getFormLayoutId(),
                r.getName(),
                r.getDescription(),
                r.getPriority(),
                r.getTrigger(),
                readJsonObject(r.getConditionJson()),
                r.isActive(),
                actions,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
