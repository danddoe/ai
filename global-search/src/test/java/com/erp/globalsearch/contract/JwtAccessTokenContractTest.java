package com.erp.globalsearch.contract;

import com.erp.globalsearch.config.JwtProperties;
import com.erp.globalsearch.service.JwtService;
import com.erp.jwt.tck.JwtContractConstants;
import com.erp.jwt.tck.JwtTckTokenFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAccessTokenContractTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtService jwtService = new JwtService(properties);

    @BeforeEach
    void assumeCompatibleJwtSecret() {
        String s = System.getenv("JWT_SECRET");
        Assumptions.assumeTrue(
                s == null || s.isBlank() || JwtContractConstants.DEFAULT_DEV_SECRET.equals(s),
                "JWT_SECRET must be unset, blank, or equal to the dev default (see JwtContractConstants)"
        );
    }

    @Test
    void parsesTckMintedAccessToken() {
        UUID uid = UUID.fromString("aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee");
        UUID tid = UUID.fromString("11111111-2222-4333-8444-555555555555");
        String jwt = JwtTckTokenFactory.accessTokenDefaultShape(
                uid,
                tid,
                "contract@example.com",
                List.of("tenant_admin"),
                List.of("entity_builder:schema:read")
        );

        JwtService.JwtClaims c = jwtService.parseToken(jwt);
        assertEquals(uid, c.userId());
        assertEquals(tid, c.tenantId());
        assertEquals("contract@example.com", c.email());
        assertTrue(c.permissions().contains("entity_builder:schema:read"));
        assertFalse(c.refresh());
    }

    @Test
    void parsesAccessTokenWithOmittedRolesAndPermissions() {
        UUID uid = UUID.randomUUID();
        UUID tid = UUID.randomUUID();
        String jwt = JwtTckTokenFactory.accessToken(
                JwtContractConstants.DEFAULT_DEV_SECRET,
                JwtContractConstants.DEFAULT_ISSUER,
                JwtContractConstants.DEFAULT_AUDIENCE,
                uid,
                tid,
                "noreply@example.com",
                null,
                null,
                120L
        );

        JwtService.JwtClaims c = jwtService.parseToken(jwt);
        assertEquals(uid, c.userId());
        assertTrue(c.roles().isEmpty());
        assertTrue(c.permissions().isEmpty());
    }
}
