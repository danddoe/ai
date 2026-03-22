package com.erp.coreservice.repository;

import com.erp.coreservice.domain.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RegionRepository extends JpaRepository<Region, UUID> {

    Optional<Region> findByRegionIdAndTenantId(UUID regionId, UUID tenantId);

    Page<Region> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
