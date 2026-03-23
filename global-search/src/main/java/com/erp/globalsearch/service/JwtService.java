package com.erp.globalsearch.service;

import com.erp.globalsearch.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.hmac-secret is required (Vault, JWT_SECRET via application.yml, or spring.profiles.active=dev)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
            String subject = claims.getSubject();
            String tenantIdStr = claims.get("tenant_id", String.class);
            if (subject == null || subject.isBlank() || tenantIdStr == null || tenantIdStr.isBlank()) {
                throw new InvalidTokenException("Token missing subject or tenant_id", null);
            }
            return new JwtClaims(
                    UUID.fromString(subject.trim()),
                    UUID.fromString(tenantIdStr.trim()),
                    claims.get("email", String.class),
                    normalizeStringClaim(claims.get("roles")),
                    normalizeStringClaim(claims.get("permissions")),
                    claims.get("jti", String.class),
                    "refresh".equals(claims.get("type", String.class))
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    /** Coerce JWT list claims to non-null strings; avoids NPE and IllegalArgumentException in {@link org.springframework.security.core.authority.SimpleGrantedAuthority}. */
    @SuppressWarnings("unchecked")
    private static List<String> normalizeStringClaim(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = o.toString().trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    public record JwtClaims(
            UUID userId,
            UUID tenantId,
            String email,
            List<String> roles,
            List<String> permissions,
            String jti,
            boolean refresh
    ) {
        /** Match IAM / entity-builder: missing JWT claims must not become null (avoids NPE in security filter). */
        public JwtClaims {
            roles = roles != null ? List.copyOf(roles) : List.of();
            permissions = permissions != null ? List.copyOf(permissions) : List.of();
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
