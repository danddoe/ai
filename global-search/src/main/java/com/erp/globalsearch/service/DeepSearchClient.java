package com.erp.globalsearch.service;

import com.erp.globalsearch.web.dto.OmniboxDtos;

import java.util.List;
import java.util.UUID;

/**
 * Phase 4: route long-running / historical text search (e.g. ClickHouse, Elasticsearch).
 * v1 returns an empty list.
 */
public interface DeepSearchClient {

    List<OmniboxDtos.OmniboxItem> search(String q, int limit, UUID tenantId);
}
