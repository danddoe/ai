package com.erp.entitybuilder.web.v1.dto;

import com.erp.entitybuilder.domain.BusinessRuleActionType;
import com.erp.entitybuilder.domain.BusinessRuleTrigger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BusinessRuleDtos {

    private BusinessRuleDtos() {}

    public record BusinessRuleActionDto(
            UUID id,
            int priority,
            BusinessRuleActionType actionType,
            Map<String, Object> payload,
            boolean applyUi,
            boolean applyServer
    ) {}

    public record BusinessRuleDto(
            UUID id,
            UUID tenantId,
            UUID entityId,
            UUID formLayoutId,
            String name,
            String description,
            int priority,
            BusinessRuleTrigger trigger,
            Map<String, Object> condition,
            boolean active,
            List<BusinessRuleActionDto> actions,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateBusinessRuleRequest(
            @NotBlank String name,
            String description,
            int priority,
            @NotNull BusinessRuleTrigger trigger,
            @NotNull Map<String, Object> condition,
            UUID formLayoutId,
            boolean active,
            @NotNull @Valid List<CreateBusinessRuleActionRequest> actions
    ) {}

    public record CreateBusinessRuleActionRequest(
            int priority,
            @NotNull BusinessRuleActionType actionType,
            @NotNull Map<String, Object> payload,
            Boolean applyUi,
            Boolean applyServer
    ) {}

    public record UpdateBusinessRuleRequest(
            String name,
            String description,
            Integer priority,
            BusinessRuleTrigger trigger,
            Map<String, Object> condition,
            UUID formLayoutId,
            Boolean active,
            List<UpdateBusinessRuleActionRequest> actions
    ) {}

    public record UpdateBusinessRuleActionRequest(
            UUID id,
            int priority,
            BusinessRuleActionType actionType,
            Map<String, Object> payload,
            Boolean applyUi,
            Boolean applyServer
    ) {}
}
