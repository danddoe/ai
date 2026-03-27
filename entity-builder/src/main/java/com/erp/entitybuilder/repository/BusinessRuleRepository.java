package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.BusinessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRuleRepository extends JpaRepository<BusinessRule, UUID> {

    /**
     * No {@code distinct} / {@code order by} here: some databases reject DISTINCT with ORDER BY on fetch joins,
     * and join fetch duplicates the parent row per child. Use {@link BusinessRuleListSupport#dedupeRulesSorted}.
     */
    @Query("""
            select r from BusinessRule r
            left join fetch r.actions
            where r.tenantId = :tenantId and r.entityId = :entityId and r.active = true
            """)
    List<BusinessRule> findActiveWithActionsByTenantAndEntity(
            @Param("tenantId") UUID tenantId,
            @Param("entityId") UUID entityId
    );

    @Query("""
            select r from BusinessRule r
            left join fetch r.actions
            where r.tenantId = :tenantId and r.entityId = :entityId
            """)
    List<BusinessRule> findAllWithActionsByTenantAndEntity(
            @Param("tenantId") UUID tenantId,
            @Param("entityId") UUID entityId
    );

    Optional<BusinessRule> findByIdAndTenantId(UUID id, UUID tenantId);
}
