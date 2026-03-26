package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.RecordListView;
import com.erp.entitybuilder.service.RecordListViewService;
import com.erp.entitybuilder.web.v1.dto.RecordListViewDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{entityId}/record-list-views")
public class RecordListViewsController {

    private final RecordListViewService recordListViewService;
    private final ObjectMapper objectMapper;

    public RecordListViewsController(RecordListViewService recordListViewService, ObjectMapper objectMapper) {
        this.recordListViewService = recordListViewService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<RecordListViewDtos.RecordListViewDto> list(@PathVariable UUID entityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return recordListViewService.list(tenantId, entityId).stream().map(this::toDto).toList();
    }

    @PostMapping
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public RecordListViewDtos.RecordListViewDto create(
            @PathVariable UUID entityId,
            @Valid @RequestBody RecordListViewDtos.CreateRecordListViewRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(req.getDefinition());
        } catch (Exception e) {
            throw new com.erp.entitybuilder.web.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "bad_request", "Invalid definition JSON");
        }
        RecordListView v = recordListViewService.create(tenantId, entityId, req.getName(), definitionJson, req.isDefault(), req.getStatus());
        return toDto(v);
    }

    @GetMapping("/{viewId}")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public RecordListViewDtos.RecordListViewDto get(@PathVariable UUID entityId, @PathVariable UUID viewId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        RecordListView v = recordListViewService.getForEntity(tenantId, entityId, viewId);
        return toDto(v);
    }

    @PatchMapping("/{viewId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public RecordListViewDtos.RecordListViewDto update(
            @PathVariable UUID entityId,
            @PathVariable UUID viewId,
            @Valid @RequestBody RecordListViewDtos.UpdateRecordListViewRequest req
    ) throws Exception {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        recordListViewService.getForEntity(tenantId, entityId, viewId);
        String definitionJson = req.getDefinition() != null ? objectMapper.writeValueAsString(req.getDefinition()) : null;
        RecordListView v = recordListViewService.update(
                tenantId,
                viewId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(definitionJson),
                Optional.ofNullable(req.getIsDefault()),
                Optional.ofNullable(req.getStatus())
        );
        return toDto(v);
    }

    @DeleteMapping("/{viewId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID entityId, @PathVariable UUID viewId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        recordListViewService.getForEntity(tenantId, entityId, viewId);
        recordListViewService.delete(tenantId, viewId);
    }

    private RecordListViewDtos.RecordListViewDto toDto(RecordListView v) {
        String definitionJson = v.getDefinition();
        try {
            JsonNode node = objectMapper.readTree(definitionJson);
            if (!node.isObject()) {
                return toDtoWithRawFallback(v, definitionJson);
            }
            Map<String, Object> defMap = objectMapper.convertValue(node, new TypeReference<>() {});
            return new RecordListViewDtos.RecordListViewDto(
                    v.getId(),
                    v.getTenantId(),
                    v.getEntityId(),
                    v.getName(),
                    v.isDefault(),
                    v.getStatus(),
                    defMap,
                    v.getCreatedAt(),
                    v.getUpdatedAt()
            );
        } catch (Exception e) {
            return toDtoWithRawFallback(v, definitionJson);
        }
    }

    private RecordListViewDtos.RecordListViewDto toDtoWithRawFallback(RecordListView v, String definitionJson) {
        return new RecordListViewDtos.RecordListViewDto(
                v.getId(),
                v.getTenantId(),
                v.getEntityId(),
                v.getName(),
                v.isDefault(),
                v.getStatus(),
                Map.of("raw", definitionJson != null ? definitionJson : ""),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
