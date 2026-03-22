package com.erp.entitybuilder.web.v1.dto;

import java.util.List;
import java.util.UUID;

public class GlobalRecordSearchDtos {

    public record GlobalRecordSearchResponse(List<GlobalRecordSearchItemDto> items) {}

    public record GlobalRecordSearchItemDto(
            UUID sourceRecordId,
            UUID sourceEntityId,
            String title,
            String subtitle,
            String routePath
    ) {}
}
