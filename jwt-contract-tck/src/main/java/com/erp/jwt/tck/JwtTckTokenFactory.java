package com.erp.jwt.tck;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints JWTs using the same claim shape as {@code com.erp.iam.service.JwtService#createAccessToken} /
 * {@code #createRefreshToken} for cross-module contract tests.
 */
public final class JwtTckTokenFactory {

    private JwtTckTokenFactory() {}

    public static SecretKey signingKeyForSecret(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access token with {@code roles} and {@code permissions} claims (arrays; may be empty).
     * Pass {@code null} for {@code roles} or {@code permissions} to <strong>omit</strong> the claim (consumer tolerance tests).
     */
    public static String accessToken(
            String hmacSecret,
            String issuer,
            String audience,
            UUID userId,
            UUID tenantId,
            String email,
            List<String> roles,
            List<String> permissions,
            long ttlSeconds
    ) {
        SecretKey key = signingKeyForSecret(hmacSecret);
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (permissions != null) {
            builder.claim("permissions", permissions);
        }
        return builder.compact();
    }

    /** Convenience: default dev secret, issuer, audience, 15-minute TTL. */
    public static String accessTokenDefaultShape(
            UUID userId,
            UUID tenantId,
            String email,
            List<String> roles,
            List<String> permissions
    ) {
        return accessToken(
                JwtContractConstants.DEFAULT_DEV_SECRET,
                JwtContractConstants.DEFAULT_ISSUER,
                JwtContractConstants.DEFAULT_AUDIENCE,
                userId,
                tenantId,
                email,
                roles,
                permissions,
                JwtContractConstants.DEFAULT_ACCESS_TTL_SECONDS
        );
    }

    public static String refreshToken(
            String hmacSecret,
            String issuer,
            String audience,
            UUID userId,
            UUID tenantId,
            UUID jti,
            long ttlSeconds
    ) {
        SecretKey key = signingKeyForSecret(hmacSecret);
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("jti", jti.toString())
                .claim("type", "refresh")
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}
