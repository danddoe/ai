package com.erp.iam.web.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/ai")
public class AiGuideController {

    @GetMapping("/guide")
    public Map<String, Object> guide() {
        return Map.of(
                "service", "iam",
                "version", "v1",
                "auth", Map.of(
                        "type", "bearer",
                        "header", "Authorization",
                        "format", "Bearer <accessToken>"
                ),
                "docs", Map.of(
                        "openapi", "/v3/api-docs",
                        "swaggerUi", "/swagger-ui/index.html"
                ),
                "errorEnvelope", Map.of(
                        "shape", Map.of(
                                "error", Map.of(
                                        "code", "string",
                                        "message", "string",
                                        "details", "object",
                                        "requestId", "string",
                                        "path", "string"
                                )
                        )
                ),
                "workflows", List.of(
                        Map.of(
                                "id", "createTenant",
                                "description", "Create a tenant (cross-tenant admin).",
                                "requiredAuthorities", List.of("iam:tenants:admin"),
                                "steps", List.of(
                                        Map.of("method", "POST", "path", "/v1/tenants", "bodySchema", "CreateTenantRequest"),
                                        Map.of("method", "GET", "path", "/v1/tenants/{tenantId}")
                                )
                        ),
                        Map.of(
                                "id", "provisionTenantAdmin",
                                "description", "Create role and grant permissions; then assign role to a user in the tenant.",
                                "requiredAuthorities", List.of("iam:security:admin (cross-tenant) OR tenant-scoped write permissions"),
                                "steps", List.of(
                                        Map.of("method", "POST", "path", "/v1/tenants/{tenantId}/roles", "bodySchema", "CreateRoleRequest"),
                                        Map.of("method", "GET", "path", "/v1/permissions"),
                                        Map.of("method", "PUT", "path", "/v1/tenants/{tenantId}/roles/{roleId}/permissions", "bodySchema", "ReplaceRolePermissionsRequest"),
                                        Map.of("method", "POST", "path", "/v1/tenants/{tenantId}/members", "bodySchema", "AddMemberRequest"),
                                        Map.of("method", "PUT", "path", "/v1/tenants/{tenantId}/users/{userId}/roles", "bodySchema", "ReplaceUserRolesRequest")
                                )
                        )
                ),
                "safety", List.of(
                        "Do not log or store access/refresh tokens.",
                        "Prefer PUT replace-set endpoints for role/permission assignments to avoid drift.",
                        "Avoid deleting tenants/roles unless explicitly instructed; deletes cascade."
                )
        );
    }
}

