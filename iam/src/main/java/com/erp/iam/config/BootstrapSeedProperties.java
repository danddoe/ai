package com.erp.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Values used when profile {@code default-bootstrap} seeds an empty database.
 * Override with env, e.g. {@code SEED_SUPERADMIN_PASSWORD}.
 */
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapSeedProperties {

    private String tenantName = "AI";
    private String tenantSlug = "ai";
    private String adminEmail = "superadmin@ai.com";
    private String adminPassword = "SuperAdminDev123!";
    private String adminDisplayName = "Super Admin";
    /** Role name stored in {@code roles.name} for the seeded user */
    private String adminRoleName = "SUPERADMIN";

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantSlug() {
        return tenantSlug;
    }

    public void setTenantSlug(String tenantSlug) {
        this.tenantSlug = tenantSlug;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }

    public String getAdminRoleName() {
        return adminRoleName;
    }

    public void setAdminRoleName(String adminRoleName) {
        this.adminRoleName = adminRoleName;
    }
}
