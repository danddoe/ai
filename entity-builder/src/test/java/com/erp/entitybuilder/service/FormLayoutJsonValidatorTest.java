package com.erp.entitybuilder.service;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormLayoutJsonValidatorTest {

    private final FormLayoutJsonValidator validator = new FormLayoutJsonValidator(new ObjectMapper());

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

    @Test
    void acceptsSaveActionItem() {
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"act1","kind":"action","action":"save","label":"Save"}"""));
    }

    @Test
    void rejectsLinkWithoutHref() {
        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithItem("""
                                {"id":"act1","kind":"action","action":"link","label":"Docs"}""")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnsafeLinkHref() {
        assertThatThrownBy(() -> validator.validateOrThrow(
                        v2WithItem("""
                                {"id":"act1","kind":"action","action":"link","label":"X","href":"javascript:alert(1)"}""")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsHttpsAndPathHref() {
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"a1","kind":"action","action":"link","label":"Ext","href":"https://example.com"}"""));
        validator.validateOrThrow(
                v2WithItem("""
                        {"id":"a2","kind":"action","action":"link","label":"App","href":"/entities/x"}"""));
    }

    @Test
    void isSafeActionHref_unit() {
        assertThat(FormLayoutJsonValidator.isSafeActionHref("/entities/1")).isTrue();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("//evil.com")).isFalse();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("javascript:void(0)")).isFalse();
        assertThat(FormLayoutJsonValidator.isSafeActionHref("https://example.com")).isTrue();
    }
}
