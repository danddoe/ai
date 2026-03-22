package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.GlobalSearchDocument;
import com.erp.entitybuilder.repository.GlobalSearchDocumentRepository;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.GlobalRecordSearchDtos;
import com.erp.entitybuilder.service.search.SearchLikeEscape;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GlobalRecordSearchService {

    private final GlobalSearchDocumentRepository repository;

    public GlobalRecordSearchService(GlobalSearchDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public GlobalRecordSearchDtos.GlobalRecordSearchResponse search(UUID tenantId, String q, int limit) {
        if (q == null || q.isBlank() || q.trim().length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "q must be at least 2 characters");
        }
        int lim = limit <= 0 ? 12 : Math.min(limit, 50);
        String pattern = "%" + SearchLikeEscape.escapeLikePattern(q.trim()) + "%";

        List<UUID> ids = repository.findIdsForTenantSearch(tenantId, pattern, lim);
        List<GlobalRecordSearchDtos.GlobalRecordSearchItemDto> items = new ArrayList<>();
        for (UUID id : ids) {
            repository.findById(id).ifPresent(doc -> items.add(toItem(doc)));
        }
        return new GlobalRecordSearchDtos.GlobalRecordSearchResponse(items);
    }

    private static GlobalRecordSearchDtos.GlobalRecordSearchItemDto toItem(GlobalSearchDocument d) {
        return new GlobalRecordSearchDtos.GlobalRecordSearchItemDto(
                d.getSourceRecordId(),
                d.getSourceEntityId(),
                d.getTitle(),
                d.getSubtitle(),
                d.getRoutePath()
        );
    }
}
