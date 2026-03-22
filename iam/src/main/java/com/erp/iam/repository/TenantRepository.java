package com.erp.iam.repository;

import com.erp.iam.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    boolean existsBySlug(String slug);
}
