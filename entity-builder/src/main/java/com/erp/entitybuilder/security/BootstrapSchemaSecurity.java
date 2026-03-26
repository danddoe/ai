package com.erp.entitybuilder.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Runs code with {@link EntityBuilderSecurity#canWriteFullSchema()} satisfied, for internal bootstrap
 * (no HTTP/JWT). Restores or clears the security context when finished.
 */
public final class BootstrapSchemaSecurity {

    /** Synthetic principal used only for catalog provisioning at startup. */
    public static final UUID BOOTSTRAP_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private BootstrapSchemaSecurity() {}

    public static void runWithFullSchemaWrite(UUID platformTenantId, Runnable action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            var principal = new TenantPrincipal(
                    BOOTSTRAP_USER_ID,
                    platformTenantId,
                    "bootstrap@entity-builder.internal",
                    List.of(),
                    List.of("entity_builder:schema:write")
            );
            var auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    "bootstrap",
                    List.of(new SimpleGrantedAuthority("entity_builder:schema:write"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            action.run();
        } finally {
            if (previous != null) {
                SecurityContextHolder.getContext().setAuthentication(previous);
            } else {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
