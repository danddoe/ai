package com.erp.jwt.tck;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTckTokenFactoryTest {

    @Test
    void accessToken_roundTripsWithJjwtParser() {
        UUID uid = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID tid = UUID.fromString("00000000-0000-4000-8000-000000000002");
        String jwt = JwtTckTokenFactory.accessTokenDefaultShape(
                uid, tid, "tck@example.com", List.of("admin"), List.of("entity_builder:schema:read"));

        var parsed = Jwts.parser()
                .verifyWith(JwtTckTokenFactory.signingKeyForSecret(JwtContractConstants.DEFAULT_DEV_SECRET))
                .requireIssuer(JwtContractConstants.DEFAULT_ISSUER)
                .requireAudience(JwtContractConstants.DEFAULT_AUDIENCE)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertEquals(uid.toString(), parsed.getSubject());
        assertEquals(tid.toString(), parsed.get("tenant_id", String.class));
        assertEquals("tck@example.com", parsed.get("email", String.class));
    }

    @Test
    void accessToken_canOmitRolesAndPermissionsClaims() {
        UUID uid = UUID.randomUUID();
        UUID tid = UUID.randomUUID();
        String jwt = JwtTckTokenFactory.accessToken(
                JwtContractConstants.DEFAULT_DEV_SECRET,
                JwtContractConstants.DEFAULT_ISSUER,
                JwtContractConstants.DEFAULT_AUDIENCE,
                uid,
                tid,
                "x@y.z",
                null,
                null,
                60L);

        var parsed = Jwts.parser()
                .verifyWith(JwtTckTokenFactory.signingKeyForSecret(JwtContractConstants.DEFAULT_DEV_SECRET))
                .requireIssuer(JwtContractConstants.DEFAULT_ISSUER)
                .requireAudience(JwtContractConstants.DEFAULT_AUDIENCE)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertNull(parsed.get("roles"));
        assertNull(parsed.get("permissions"));
    }
}
