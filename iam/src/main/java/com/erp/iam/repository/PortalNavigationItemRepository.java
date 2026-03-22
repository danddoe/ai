package com.erp.iam.repository;

import com.erp.iam.domain.PortalNavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortalNavigationItemRepository extends JpaRepository<PortalNavigationItem, UUID> {

    List<PortalNavigationItem> findAllByActiveTrueOrderByParentIdAscSortOrderAsc();

    List<PortalNavigationItem> findAllByTenantId(UUID tenantId);
}
