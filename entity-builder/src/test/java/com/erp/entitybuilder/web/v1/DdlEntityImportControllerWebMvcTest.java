package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.security.TenantPrincipal;
import com.erp.entitybuilder.service.ddl.DdlEntityImportService;
import com.erp.entitybuilder.web.v1.dto.DdlImportDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc (no method-security proxy) — asserts {@code POST /v1/entities/import/ddl/preview}
 * and {@code POST /v1/entities/import/ddl} are mapped to the controller (not static resources).
 */
@ExtendWith(MockitoExtension.class)
class DdlEntityImportControllerWebMvcTest {

    @Mock
    private DdlEntityImportService ddlEntityImportService;

    @InjectMocks
    private DdlEntityImportController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postPreview_isMapped() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                tenantAuth(tenantId, "entity_builder:schema:read"));
        when(ddlEntityImportService.preview(eq(tenantId), any())).thenReturn(
                new DdlImportDtos.DdlImportPreviewResponse(List.of(), List.of()));

        mockMvc.perform(post("/v1/entities/import/ddl/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ddl\":\"CREATE TABLE t (id INT);\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void postApply_isMapped() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                tenantAuth(tenantId, "entity_builder:schema:write"));
        when(ddlEntityImportService.apply(eq(tenantId), any())).thenReturn(
                new DdlImportDtos.DdlImportApplyResponse(List.of(), List.of()));

        mockMvc.perform(post("/v1/entities/import/ddl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ddl\":\"CREATE TABLE t (id INT);\"}"))
                .andExpect(status().isOk());
    }

    private static Authentication tenantAuth(UUID tenantId, String permission) {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), tenantId, "t@test", List.of(), List.of(permission));
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority(permission)));
    }
}
