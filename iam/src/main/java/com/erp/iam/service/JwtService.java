package com.erp.iam.service;

import com.erp.iam.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // For development: use HMAC key from config or a default. For production use RSA keys from files.
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = "erp-iam-dev-secret-key-at-least-256-bits-long-for-hs256";
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, UUID tenantId, String email, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTokenExpirationSeconds());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public String createRefreshToken(UUID userId, UUID tenantId, UUID jti) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getRefreshTokenExpirationSeconds());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("jti", jti.toString())
                .claim("type", "refresh")
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public JwtClaims parseToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            return new JwtClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("tenant_id", String.class)),
                    claims.get("email", String.class),
                    claims.get("roles", List.class),
                    claims.get("permissions", List.class),
                    claims.get("jti", String.class),
                    "refresh".equals(claims.get("type", String.class))
            );
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.getAccessTokenExpirationSeconds();
    }

    public static class JwtClaims {
        private final UUID userId;
        private final UUID tenantId;
        private final String email;
        private final List<String> roles;
        private final List<String> permissions;
        private final String jti;
        private final boolean refresh;

        public JwtClaims(UUID userId, UUID tenantId, String email, List<String> roles, List<String> permissions, String jti, boolean refresh) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.email = email;
            this.roles = roles != null ? roles : List.of();
            this.permissions = permissions != null ? permissions : List.of();
            this.jti = jti;
            this.refresh = refresh;
        }

        public UUID getUserId() { return userId; }
        public UUID getTenantId() { return tenantId; }
        public String getEmail() { return email; }
        public List<String> getRoles() { return roles; }
        public List<String> getPermissions() { return permissions; }
        public String getJti() { return jti; }
        public boolean isRefresh() { return refresh; }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
