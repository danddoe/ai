package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityFieldStatuses;
import com.erp.entitybuilder.domain.EntityRelationship;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.EntityRelationshipRepository;
import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormLayoutJsonValidatorTest {

    @Mock
    private EntityRelationshipRepository relationshipRepository;

    @Mock
    private EntityFieldRepository fieldRepository;

    private FormLayoutJsonValidator validator;

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FORM_ENTITY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_ENTITY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FIELD_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        validator = new FormLayoutJsonValidator(new ObjectMapper(), relationshipRepository, fieldRepository);
    }

    private static String v2WithItem(String itemJson) {
        return """
                {
                  "version": 2,
                  "regions": [
                    {
                      "id": "reg1",
                      "role": "header",
                      "title": "H",
                      "tabGroupId": null,
                      "rows": [
                        {
                          "id": "row1",
                          "columns": [
                            {
                              "id": "col1",
                              "span": 12,
                              "items": [%s]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """
                .formatted(itemJson);
    }

    private static String v2WithRegionBinding(String bindingJson) {
        return """
                {
                  "version": 2,
                  "regions": [
                    {
                      "id": "reg1",
                      "role": "header",
                      "title": "H",
                      "tabGroupId": null,
                      %s
                      "rows": [
                        {
                          "id": "row1",
                          "columns": [
                            {
                              "id": "col1",
                              "span": 12,
                              "items": []
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """
                .formatted(bindingJson);
    }

    @Test
    void acceptsSaveActionItem() {
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"act1","kind":"action","action":"save","label":"Save"}"""),
                TENANT,
                FORM_ENTITY);
    }

    @Test
    void rejectsLinkWithoutHref() {
        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithItem("""
                                {"id":"act1","kind":"action","action":"link","label":"Docs"}"""),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnsafeLinkHref() {
        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithItem("""
                                {"id":"act1","kind":"action","action":"link","label":"X","href":"javascript:alert(1)"}"""),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsLayoutFieldItemWhenFieldNotActive() {
        EntityField f = new EntityField();
        f.setId(FIELD_ID);
        f.setEntityId(FORM_ENTITY);
        f.setStatus(EntityFieldStatuses.INACTIVE);
        when(fieldRepository.findByEntityIdAndStatusOrderBySortOrderAscNameAsc(FORM_ENTITY, EntityFieldStatuses.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithItem(String.format(
                                "{\"id\":\"i1\",\"fieldId\":\"%s\",\"fieldSlug\":\"x\",\"presentation\":"
                                        + "{\"label\":null,\"placeholder\":\"\",\"helpText\":\"\",\"readOnly\":false,"
                                        + "\"hidden\":false,\"width\":\"full\",\"componentHint\":\"default\"}}",
                                FIELD_ID)),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsLayoutFieldItemWhenFieldActive() {
        EntityField f = new EntityField();
        f.setId(FIELD_ID);
        f.setEntityId(FORM_ENTITY);
        f.setStatus(EntityFieldStatuses.ACTIVE);
        when(fieldRepository.findByEntityIdAndStatusOrderBySortOrderAscNameAsc(FORM_ENTITY, EntityFieldStatuses.ACTIVE))
                .thenReturn(List.of(f));

        validator.validateOrThrow(
                v2WithItem(String.format(
                        "{\"id\":\"i1\",\"fieldId\":\"%s\",\"fieldSlug\":\"x\",\"presentation\":"
                                + "{\"label\":null,\"placeholder\":\"\",\"helpText\":\"\",\"readOnly\":false,"
                                + "\"hidden\":false,\"width\":\"full\",\"componentHint\":\"default\"}}",
                        FIELD_ID)),
                TENANT,
                FORM_ENTITY);
    }

    @Test
    void acceptsHttpsAndPathHref() {
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"a1","kind":"action","action":"link","label":"Ext","href":"https://example.com"}"""),
                TENANT,
                FORM_ENTITY);
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"a2","kind":"action","action":"link","label":"App","href":"/entities/x"}"""),
                TENANT,
                FORM_ENTITY);
    }

    @Test
    void acceptsEntityRelationshipBindingWhenFromEntityMatches() {
        EntityRelationship r = new EntityRelationship();
        r.setId(REL_ID);
        r.setTenantId(TENANT);
        r.setFromEntityId(FORM_ENTITY);
        r.setToEntityId(OTHER_ENTITY);
        when(relationshipRepository.findByIdAndTenantId(eq(REL_ID), eq(TENANT))).thenReturn(Optional.of(r));

        validator.validateOrThrow(
                v2WithRegionBinding("\"binding\": { \"kind\": \"entity_relationship\", \"relationshipId\": \"" + REL_ID + "\" },\n"),
                TENANT,
                FORM_ENTITY);
    }

    @Test
    void rejectsBindingWhenRelationshipFromEntityDiffers() {
        EntityRelationship r = new EntityRelationship();
        r.setId(REL_ID);
        r.setTenantId(TENANT);
        r.setFromEntityId(OTHER_ENTITY);
        r.setToEntityId(FORM_ENTITY);
        when(relationshipRepository.findByIdAndTenantId(eq(REL_ID), eq(TENANT))).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithRegionBinding("\"binding\": { \"kind\": \"entity_relationship\", \"relationshipId\": \"" + REL_ID + "\" },\n"),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsBindingWhenRelationshipMissing() {
        when(relationshipRepository.findByIdAndTenantId(eq(REL_ID), eq(TENANT))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithRegionBinding("\"binding\": { \"kind\": \"entity_relationship\", \"relationshipId\": \"" + REL_ID + "\" },\n"),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsBindingWithInvalidKind() {
        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithRegionBinding("\"binding\": { \"kind\": \"other\", \"relationshipId\": \"" + REL_ID + "\" },\n"),
                        TENANT,
                        FORM_ENTITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void isSafeActionHref_unit() {
        assertThat(FormLayoutJsonValidator.isSafeActionHref("/entities/1")).isTrue();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("//evil.com")).isFalse();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("javascript:void(0)")).isFalse();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("https://example.com")).isTrue();
    }
}
