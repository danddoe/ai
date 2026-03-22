package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.GlobalSearchDocument;
import com.erp.entitybuilder.repository.GlobalSearchDocumentRepository;
import com.erp.entitybuilder.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalRecordSearchServiceTest {

    @Mock
    GlobalSearchDocumentRepository repository;

    @InjectMocks
    GlobalRecordSearchService service;

    @Test
    void rejectsShortQuery() {
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> service.search(tenantId, "a", 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void mapsDocumentsInOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        when(repository.findIdsForTenantSearch(eq(tenantId), anyString(), eq(5))).thenReturn(List.of(rowId));

        GlobalSearchDocument doc = new GlobalSearchDocument();
        doc.setSourceRecordId(recordId);
        doc.setSourceEntityId(entityId);
        doc.setTitle("Acme");
        doc.setSubtitle("Vendor");
        doc.setRoutePath("/entities/" + entityId + "/records/" + recordId);
        when(repository.findById(rowId)).thenReturn(Optional.of(doc));

        var res = service.search(tenantId, "ac", 5);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).title()).isEqualTo("Acme");
        assertThat(res.items().get(0).sourceRecordId()).isEqualTo(recordId);
    }
}
