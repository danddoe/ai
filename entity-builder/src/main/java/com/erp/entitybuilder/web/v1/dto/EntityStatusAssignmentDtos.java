package com.erp.entitybuilder.web.v1.dto;

import java.util.List;
import java.util.UUID;

public class EntityStatusAssignmentDtos {

    public record AssignmentRowDto(UUID entityStatusId, String code, String label, int sortOrder) {}

    public record AvailableStatusDto(UUID entityStatusId, String code, String label) {}

    public record ReplaceAssignmentsRequest(List<UUID> entityStatusIds) {}
}
