package com.erp.iam.repository;

import com.erp.iam.domain.PortalNavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortalNavigationItemRepository extends JpaRepository<PortalNavigationItem, UUID> {

    List<PortalNavigationItem> findAllByActiveTrueOrderByParentIdAscSortOrderAsc();

    List<PortalNavigationItem> findAllByTenantId(UUID tenantId);

    /**
     * Rows visible to tenant navigation editors: platform-wide ({@code tenant_id} null) plus this tenant's own rows.
     */
    @Query("SELECT p FROM PortalNavigationItem p WHERE p.tenantId IS NULL OR p.tenantId = :tenantId")
    List<PortalNavigationItem> findAllGlobalAndByTenantId(@Param("tenantId") UUID tenantId);
}
