package com.erp.globalsearch.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OmniboxDtos {

    public record OmniboxResponse(
            List<OmniboxItem> navigation,
            List<OmniboxItem> records,
            List<OmniboxItem> deepHistory
    ) {}

    public record OmniboxItem(
            String id,
            String category,
            String title,
            String subtitle,
            String url,
            String icon,
            Map<String, Object> meta
    ) {}

    /** IAM JSON shape */
    public record NavigationSearchResponse(List<NavigationSearchHitDto> items) {}

    public record NavigationSearchHitDto(
            UUID id,
            String label,
            String description,
            String routePath,
            String type,
            String icon,
            String categoryKey
    ) {}

    /** entity-builder JSON shape */
    public record GlobalRecordSearchResponse(List<GlobalRecordSearchItemDto> items) {}

    public record GlobalRecordSearchItemDto(
            UUID sourceRecordId,
            UUID sourceEntityId,
            String title,
            String subtitle,
            String routePath
    ) {}
}
