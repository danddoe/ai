package com.erp.iam.service;

import com.erp.iam.domain.*;
import com.erp.iam.config.JwtProperties;
import com.erp.iam.repository.*;
import com.erp.iam.service.JwtService.JwtClaims;
import com.erp.iam.service.JwtService.InvalidTokenException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       TenantUserRepository tenantUserRepository,
                       UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       PermissionRepository permissionRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public TokenResult login(String tenantSlugOrId, String email, String password) {
        Tenant tenant = resolveTenant(tenantSlugOrId);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid credentials"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AuthException("User account is not active");
        }
        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow(() -> new AuthException("User is not a member of this tenant"));
        if (!"ACTIVE".equals(tenantUser.getStatus())) {
            throw new AuthException("Tenant membership is not active");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        List<String> roleNames = resolveRoleNames(tenant.getId(), user.getId());
        List<String> permissions = resolvePermissionCodes(tenant.getId(), user.getId());

        String accessToken = jwtService.createAccessToken(user.getId(), tenant.getId(), user.getEmail(), roleNames, permissions);
        UUID jti = UUID.randomUUID();
        String refreshToken = jwtService.createRefreshToken(user.getId(), tenant.getId(), jti);

        RefreshToken rt = new RefreshToken();
        rt.setTenantId(tenant.getId());
        rt.setUserId(user.getId());
        rt.setJti(jti);
        rt.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        refreshTokenRepository.save(rt);

        return new TokenResult(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpirationSeconds(),
                effectiveLocale(tenantUser, tenant)
        );
    }

    @Transactional
    public TokenResult refresh(String refreshTokenValue) {
        JwtClaims claims = jwtService.parseToken(refreshTokenValue);
        if (!claims.isRefresh() || claims.getJti() == null) {
            throw new InvalidTokenException("Not a refresh token", null);
        }
        Optional<RefreshToken> stored = refreshTokenRepository.findByTenantIdAndJti(claims.getTenantId(), UUID.fromString(claims.getJti()));
        if (stored.isEmpty() || stored.get().getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token invalid or expired", null);
        }
        refreshTokenRepository.delete(stored.get());

        User user = userRepository.findById(claims.getUserId()).orElseThrow(() -> new AuthException("User not found"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AuthException("User account is not active");
        }
        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(claims.getTenantId(), user.getId())
                .orElseThrow(() -> new AuthException("User is not a member of this tenant"));
        Tenant tenant = tenantRepository.findById(claims.getTenantId())
                .orElseThrow(() -> new AuthException("Tenant not found"));

        List<String> roleNames = resolveRoleNames(claims.getTenantId(), user.getId());
        List<String> permissions = resolvePermissionCodes(claims.getTenantId(), user.getId());

        String accessToken = jwtService.createAccessToken(user.getId(), claims.getTenantId(), user.getEmail(), roleNames, permissions);
        UUID jti = UUID.randomUUID();
        String newRefreshToken = jwtService.createRefreshToken(user.getId(), claims.getTenantId(), jti);

        RefreshToken rt = new RefreshToken();
        rt.setTenantId(claims.getTenantId());
        rt.setUserId(user.getId());
        rt.setJti(jti);
        rt.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        refreshTokenRepository.save(rt);

        return new TokenResult(
                accessToken,
                newRefreshToken,
                jwtService.getAccessTokenExpirationSeconds(),
                effectiveLocale(tenantUser, tenant)
        );
    }

    private static String effectiveLocale(TenantUser tenantUser, Tenant tenant) {
        String u = tenantUser != null ? tenantUser.getPreferredLocale() : null;
        if (u != null && !u.isBlank()) {
            return normalizeLocaleTag(u);
        }
        String t = tenant != null ? tenant.getDefaultLocale() : null;
        if (t != null && !t.isBlank()) {
            return normalizeLocaleTag(t);
        }
        return null;
    }

    private static String normalizeLocaleTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().replace('_', '-');
        int delim = s.indexOf('-');
        String primary = delim > 0 ? s.substring(0, delim) : s;
        if (primary.isBlank()) {
            return null;
        }
        return primary.toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        try {
            JwtClaims claims = jwtService.parseToken(refreshTokenValue);
            if (claims.getJti() != null) {
                refreshTokenRepository.deleteByTenantIdAndJti(claims.getTenantId(), UUID.fromString(claims.getJti()));
            }
        } catch (Exception ignored) {
        }
    }

    private Tenant resolveTenant(String tenantSlugOrId) {
        if (tenantSlugOrId == null || tenantSlugOrId.isBlank()) {
            throw new AuthException("Tenant slug or ID is required");
        }
        String raw = tenantSlugOrId.trim();
        try {
            UUID id = UUID.fromString(raw);
            return tenantRepository.findById(id)
                    .orElseThrow(() -> new AuthException(
                            "Tenant not found for this UUID. Create the tenant or use its slug."));
        } catch (IllegalArgumentException e) {
            return tenantRepository.findBySlugIgnoreCase(raw)
                    .orElseThrow(() -> new AuthException(
                            "Tenant not found. Check the slug (bootstrap default is \"ai\") or use the tenant UUID."));
        }
    }

    private List<String> resolveRoleNames(UUID tenantId, UUID userId) {
        List<UUID> roleIds = userRoleRepository.findRoleIdsByTenantIdAndUserId(tenantId, userId);
        if (roleIds.isEmpty()) return List.of();
        return roleIds.stream()
                .map(rid -> roleRepository.findById(rid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    private List<String> resolvePermissionCodes(UUID tenantId, UUID userId) {
        List<UUID> roleIds = userRoleRepository.findRoleIdsByTenantIdAndUserId(tenantId, userId);
        if (roleIds.isEmpty()) return List.of();
        return roleIds.stream()
                .flatMap(roleId -> rolePermissionRepository.findPermissionIdsByTenantIdAndRoleId(tenantId, roleId).stream())
                .distinct()
                .map(permissionRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Permission::getCode)
                .distinct()
                .collect(Collectors.toList());
    }

    public static class TokenResult {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresInSeconds;
        /** Effective UI locale: user preference, else tenant default. */
        private final String preferredLocale;

        public TokenResult(String accessToken, String refreshToken, long expiresInSeconds, String preferredLocale) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresInSeconds = expiresInSeconds;
            this.preferredLocale = preferredLocale;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public String getPreferredLocale() {
            return preferredLocale;
        }
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
