package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.FormLayout;
import com.erp.entitybuilder.service.FormLayoutService;
import com.erp.entitybuilder.web.v1.dto.FormLayoutDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{entityId}/form-layouts")
public class FormLayoutsController {

    private final FormLayoutService formLayoutService;
    private final ObjectMapper objectMapper;

    public FormLayoutsController(FormLayoutService formLayoutService, ObjectMapper objectMapper) {
        this.formLayoutService = formLayoutService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public List<FormLayoutDtos.FormLayoutDto> list(@PathVariable UUID entityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return formLayoutService.list(tenantId, entityId).stream().map(this::toDto).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public FormLayoutDtos.FormLayoutDto create(
            @PathVariable UUID entityId,
            @Valid @RequestBody FormLayoutDtos.CreateFormLayoutRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String layoutJson;
        try {
            layoutJson = objectMapper.writeValueAsString(req.getLayout());
        } catch (Exception e) {
            throw new com.erp.entitybuilder.web.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "bad_request", "Invalid layout JSON");
        }

        FormLayout l = formLayoutService.create(tenantId, entityId, req.getName(), layoutJson, req.isDefault());
        return toDto(l);
    }

    @GetMapping("/{layoutId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public FormLayoutDtos.FormLayoutDto get(@PathVariable UUID entityId, @PathVariable UUID layoutId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        FormLayout l = formLayoutService.get(tenantId, layoutId);
        return toDto(l);
    }

    @PatchMapping("/{layoutId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public FormLayoutDtos.FormLayoutDto update(
            @PathVariable UUID entityId,
            @PathVariable UUID layoutId,
            @Valid @RequestBody FormLayoutDtos.UpdateFormLayoutRequest req
    ) throws Exception {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String layoutJson = req.getLayout() != null ? objectMapper.writeValueAsString(req.getLayout()) : null;
        FormLayout l = formLayoutService.update(
                tenantId,
                layoutId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(layoutJson),
                Optional.ofNullable(req.getIsDefault()),
                Optional.ofNullable(req.getStatus())
        );
        return toDto(l);
    }

    @DeleteMapping("/{layoutId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID entityId, @PathVariable UUID layoutId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        formLayoutService.delete(tenantId, layoutId);
    }

    private FormLayoutDtos.FormLayoutDto toDto(FormLayout l) {
        try {
            Map<String, Object> layoutMap = objectMapper.readValue(l.getLayout(), new TypeReference<>() {});
            return new FormLayoutDtos.FormLayoutDto(
                    l.getId(),
                    l.getTenantId(),
                    l.getEntityId(),
                    l.getName(),
                    l.isDefault(),
                    l.getStatus(),
                    layoutMap,
                    l.getCreatedAt(),
                    l.getUpdatedAt()
            );
        } catch (Exception e) {
            // If stored layout isn't valid JSON, return raw string under "layout" as a best-effort.
            return new FormLayoutDtos.FormLayoutDto(
                    l.getId(),
                    l.getTenantId(),
                    l.getEntityId(),
                    l.getName(),
                    l.isDefault(),
                    l.getStatus(),
                    Map.of("raw", l.getLayout()),
                    l.getCreatedAt(),
                    l.getUpdatedAt()
            );
        }
    }
}

