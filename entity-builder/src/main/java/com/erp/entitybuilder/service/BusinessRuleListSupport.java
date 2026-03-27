package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.BusinessRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hibernate returns one row per (rule × action) with {@code join fetch}; collapse to one instance per rule.
 */
public final class BusinessRuleListSupport {

    private BusinessRuleListSupport() {}

    public static List<BusinessRule> dedupeRulesSorted(List<BusinessRule> rows) {
        Map<UUID, BusinessRule> byId = new LinkedHashMap<>();
        for (BusinessRule r : rows) {
            byId.putIfAbsent(r.getId(), r);
        }
        List<BusinessRule> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparingInt(BusinessRule::getPriority)
                .thenComparing(BusinessRule::getId));
        return out;
    }
}
