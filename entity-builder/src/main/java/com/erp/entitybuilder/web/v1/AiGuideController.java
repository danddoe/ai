package com.erp.entitybuilder.web.v1;

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
                "service", "entity-builder",
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
                "workflows", List.of(
                        Map.of(
                                "id", "createEntityAndFirstRecord",
                                "description", "Create an entity (schema), create fields, then create a record with values and optional links.",
                                "requiredAuthorities", List.of(
                                        "entity_builder:schema:write",
                                        "entity_builder:records:write"
                                ),
                                "steps", List.of(
                                        Map.of("method", "POST", "path", "/v1/entities", "bodySchema", "CreateEntityRequest"),
                                        Map.of("method", "POST", "path", "/v1/entities/{entityId}/fields", "bodySchema", "CreateEntityFieldRequest"),
                                        Map.of("method", "POST", "path", "/v1/tenants/{tenantId}/entities/{entityId}/records", "bodySchema", "CreateRecordRequest")
                                )
                        )
                ),
                "safety", List.of(
                        "Do not log tokens or PII values.",
                        "Treat `externalId` and `Idempotency-Key` as idempotency inputs.",
                        "PII values are stored encrypted in pii_vault and masked unless pii read permission is granted."
                )
        );
    }
}

