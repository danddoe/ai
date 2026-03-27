package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.BusinessRuleService;
import com.erp.entitybuilder.service.BusinessRuleSurfaceFilter;
import com.erp.entitybuilder.web.v1.dto.BusinessRuleDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{entityId}/business-rules")
public class BusinessRulesController {

    private final BusinessRuleService businessRuleService;

    public BusinessRulesController(BusinessRuleService businessRuleService) {
        this.businessRuleService = businessRuleService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<BusinessRuleDtos.BusinessRuleDto> list(
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "ALL") String surface,
            @RequestParam(required = false) UUID formLayoutId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        BusinessRuleSurfaceFilter f = parseSurface(surface);
        if ((f == BusinessRuleSurfaceFilter.UI || f == BusinessRuleSurfaceFilter.SERVER) && !activeOnly) {
            activeOnly = true;
        }
        return businessRuleService.list(tenantId, entityId, f, formLayoutId, activeOnly);
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public BusinessRuleDtos.BusinessRuleDto get(@PathVariable UUID entityId, @PathVariable UUID ruleId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return businessRuleService.get(tenantId, entityId, ruleId);
    }

    @PostMapping
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public BusinessRuleDtos.BusinessRuleDto create(
            @PathVariable UUID entityId,
            @Valid @RequestBody BusinessRuleDtos.CreateBusinessRuleRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return businessRuleService.create(tenantId, entityId, req);
    }

    @PatchMapping("/{ruleId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public BusinessRuleDtos.BusinessRuleDto update(
            @PathVariable UUID entityId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody BusinessRuleDtos.UpdateBusinessRuleRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return businessRuleService.update(tenantId, entityId, ruleId, req);
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID entityId, @PathVariable UUID ruleId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        businessRuleService.delete(tenantId, entityId, ruleId);
    }

    private static BusinessRuleSurfaceFilter parseSurface(String raw) {
        if (raw == null || raw.isBlank()) {
            return BusinessRuleSurfaceFilter.ALL;
        }
        try {
            return BusinessRuleSurfaceFilter.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BusinessRuleSurfaceFilter.ALL;
        }
    }
}
