package com.erp.iam.repository;

import com.erp.iam.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTenantIdAndJti(UUID tenantId, UUID jti);

    void deleteByTenantIdAndJti(UUID tenantId, UUID jti);

    void deleteByExpiresAtBefore(Instant instant);
}
