package com.erp.iam.repository;

import com.erp.iam.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tenantId = :tenantId AND r.jti = :jti")
    Optional<RefreshToken> findByTenantIdAndJti(@Param("tenantId") UUID tenantId, @Param("jti") UUID jti);

    void deleteByTenantIdAndJti(UUID tenantId, UUID jti);

    void deleteByExpiresAtBefore(Instant instant);
}
