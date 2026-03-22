package com.erp.globalsearch.service;

import com.erp.globalsearch.web.dto.OmniboxDtos;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class NoOpDeepSearchClient implements DeepSearchClient {

    @Override
    public List<OmniboxDtos.OmniboxItem> search(String q, int limit, UUID tenantId) {
        return List.of();
    }
}
