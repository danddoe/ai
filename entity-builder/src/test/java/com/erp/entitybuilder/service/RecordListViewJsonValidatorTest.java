package com.erp.entitybuilder.service;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordListViewJsonValidatorTest {

    private final RecordListViewJsonValidator validator = new RecordListViewJsonValidator(new ObjectMapper());

    @Test
    void acceptsMinimalV1() {
        String json = """
                {"version":1,"columns":[{"fieldSlug":"a","order":0}],"showRowActions":true}
                """;
        assertThatCode(() -> validator.validateOrThrow(json)).doesNotThrowAnyException();
    }

    @Test
    void acceptsSlugWithHyphenAndDotLikeEntityFields() {
        String json = """
                {"version":1,"columns":[
                  {"fieldSlug":"requested-amount","order":0},
                  {"fieldSlug":"loan.id","order":1}
                ]}
                """;
        assertThatCode(() -> validator.validateOrThrow(json)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSlugWithWhitespace() {
        String json = "{\"version\":1,\"columns\":[{\"fieldSlug\":\"bad slug\",\"order\":0}]}";
        assertThatThrownBy(() -> validator.validateOrThrow(json)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsWrongVersion() {
        assertThatThrownBy(() -> validator.validateOrThrow("{\"version\":2,\"columns\":[]}"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsDuplicateOrder() {
        String json = """
                {"version":1,"columns":[
                  {"fieldSlug":"a","order":0},
                  {"fieldSlug":"b","order":0}
                ]}
                """;
        assertThatThrownBy(() -> validator.validateOrThrow(json)).isInstanceOf(ApiException.class);
    }
}
