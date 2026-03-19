package com.erp.iam.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Tenant slug or ID is required")
    private String tenantSlugOrId;

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public String getTenantSlugOrId() { return tenantSlugOrId; }
    public void setTenantSlugOrId(String tenantSlugOrId) { this.tenantSlugOrId = tenantSlugOrId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
