package com.erp.entitybuilder.service.rules;

import com.erp.entitybuilder.domain.BusinessRule;
import com.erp.entitybuilder.domain.BusinessRuleAction;
import com.erp.entitybuilder.domain.BusinessRuleActionType;
import com.erp.entitybuilder.domain.BusinessRuleTrigger;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.repository.BusinessRuleRepository;
import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessRuleEngineTest {

    @Mock
    private BusinessRuleRepository businessRuleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BusinessRuleEngine engine() {
        return new BusinessRuleEngine(
                businessRuleRepository,
                new RuleConditionEvaluator(objectMapper),
                objectMapper
        );
    }

    @Test
    void server_set_field_runs_when_condition_true() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        EntityField f = new EntityField();
        f.setSlug("status");
        f.setFieldType("string");
        Map<String, EntityField> bySlug = Map.of("status", f);

        BusinessRule rule = new BusinessRule();
        rule.setTrigger(BusinessRuleTrigger.BEFORE_CREATE);
        rule.setPriority(0);
        rule.setConditionJson("{\"op\":\"cmp\",\"field\":\"country\",\"operator\":\"eq\",\"value\":\"US\"}");

        BusinessRuleAction action = new BusinessRuleAction();
        action.setPriority(0);
        action.setActionType(BusinessRuleActionType.SERVER_SET_FIELD_VALUE);
        action.setPayload(objectMapper.writeValueAsString(Map.of("targetField", "status", "value", "APPROVED")));
        action.setApplyServer(true);
        action.setApplyUi(false);
        rule.addAction(action);

        when(businessRuleRepository.findActiveWithActionsByTenantAndEntity(tenant, entity))
                .thenReturn(List.of(rule));

        Map<String, Object> values = new LinkedHashMap<>(Map.of(
                "country", "US",
                "status", "DRAFT"
        ));
        engine().applyServerRules(tenant, entity, BusinessRuleTrigger.BEFORE_CREATE, values, bySlug);
        assertThat(values.get("status")).isEqualTo("APPROVED");
    }

    @Test
    void server_add_error_throws() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        EntityField f = new EntityField();
        f.setSlug("a");
        f.setFieldType("string");
        Map<String, EntityField> bySlug = Map.of("a", f);

        BusinessRule rule = new BusinessRule();
        rule.setTrigger(BusinessRuleTrigger.BEFORE_CREATE);
        rule.setConditionJson("{\"op\":\"cmp\",\"field\":\"country\",\"operator\":\"eq\",\"value\":\"US\"}");

        BusinessRuleAction action = new BusinessRuleAction();
        action.setActionType(BusinessRuleActionType.SERVER_ADD_ERROR);
        action.setPayload(objectMapper.writeValueAsString(Map.of("field", "tax_id", "message", "Tax id required")));
        action.setApplyServer(true);
        action.setApplyUi(false);
        rule.addAction(action);

        when(businessRuleRepository.findActiveWithActionsByTenantAndEntity(tenant, entity))
                .thenReturn(List.of(rule));

        Map<String, Object> values = new LinkedHashMap<>(Map.of("country", "US"));
        assertThatThrownBy(() -> engine().applyServerRules(tenant, entity, BusinessRuleTrigger.BEFORE_CREATE, values, bySlug))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tax");
    }
}
