package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.AssignmentScope;
import com.erp.entitybuilder.domain.EntityStatusAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EntityStatusAssignmentRepository extends JpaRepository<EntityStatusAssignment, UUID> {

    List<EntityStatusAssignment> findByTenantIdAndAssignmentScopeAndScopeIdOrderBySortOrderAsc(
            UUID tenantId,
            AssignmentScope assignmentScope,
            UUID scopeId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EntityStatusAssignment a WHERE a.tenantId = :tenantId AND a.assignmentScope = :scope AND a.scopeId = :scopeId")
    void deleteByTenantIdAndAssignmentScopeAndScopeId(
            @Param("tenantId") UUID tenantId,
            @Param("scope") AssignmentScope scope,
            @Param("scopeId") UUID scopeId
    );

    @Query(
            "SELECT a.entityStatusId FROM EntityStatusAssignment a "
                    + "WHERE a.tenantId = :tenantId AND a.assignmentScope = :scope AND a.scopeId = :scopeId "
                    + "ORDER BY a.sortOrder ASC"
    )
    List<UUID> findEntityStatusIdsByTenantIdAndAssignmentScopeAndScopeId(
            @Param("tenantId") UUID tenantId,
            @Param("scope") AssignmentScope scope,
            @Param("scopeId") UUID scopeId
    );
}
