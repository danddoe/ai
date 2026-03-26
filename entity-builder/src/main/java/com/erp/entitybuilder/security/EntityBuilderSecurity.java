package com.erp.entitybuilder.security;

import com.erp.entitybuilder.domain.DefinitionScope;
import com.erp.entitybuilder.domain.EntityDefinition;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("entityBuilderSecurity")
public class EntityBuilderSecurity {

    public boolean isTenant(UUID tenantId) {
        TenantPrincipal p = principalOrNull();
        return p != null && tenantId != null && tenantId.equals(p.getTenantId());
    }

    public boolean hasCrossTenantAdminAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("entity_builder:security:admin") ||
                        a.getAuthority().equals("entity_builder:tenants:admin"));
    }

    /**
     * Full platform schema write (catalog sync, DDL import apply, mutating {@link DefinitionScope#STANDARD_OBJECT} entities).
     */
    public boolean canWriteFullSchema() {
        return hasAuthority("entity_builder:schema:write");
    }

    /**
     * Create/update tenant-owned schema (and layouts/relationships) — cannot mutate platform catalog entity definitions/fields.
     */
    public boolean canWriteTenantSchema() {
        return canWriteFullSchema() || hasAuthority("entity_builder:schema:tenant_write");
    }

    public boolean canMutateEntitySchema(EntityDefinition entity) {
        if (entity == null) return false;
        if (entity.getDefinitionScope() == DefinitionScope.STANDARD_OBJECT) {
            return canWriteFullSchema();
        }
        return canWriteTenantSchema();
    }

    /**
     * Schema read endpoints: {@code entity_builder:schema:read}, full write, or tenant schema write.
     */
    public boolean canReadSchema() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String g = a.getAuthority();
            return "entity_builder:schema:read".equals(g)
                    || "entity_builder:schema:write".equals(g)
                    || "entity_builder:schema:tenant_write".equals(g);
        });
    }

    /**
     * Entity metadata reads for portal UIs (e.g. Create UI entity picker): schema read/write, or IAM
     * {@code portal:navigation:write} so nav editors can resolve entity ids without full schema read.
     */
    public boolean canReadEntitiesForPortalUi() {
        if (canReadSchema()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "portal:navigation:write".equals(a.getAuthority()));
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    private TenantPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof TenantPrincipal tp) return tp;
        return null;
    }
}
