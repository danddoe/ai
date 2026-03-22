package com.erp.entitybuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentNumberGenerationPolicyTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void defaultsToManualWhenMissing() {
        DocumentNumberGenerationPolicy p = DocumentNumberGenerationPolicy.fromFieldConfig(null, om);
        assertThat(p.getStrategy()).isEqualTo(DocumentNumberGenerationPolicy.Strategy.MANUAL);
        assertThat(p.isAutoGenerate()).isFalse();
    }

    @Test
    void parsesMonthlySequence() throws Exception {
        String json = om.writeValueAsString(java.util.Map.of(
                "documentNumberGeneration", java.util.Map.of(
                        "strategy", "MONTHLY_SEQUENCE",
                        "prefix", "JV",
                        "sequenceWidth", 6,
                        "timeZone", "America/New_York"
                )
        ));
        DocumentNumberGenerationPolicy p = DocumentNumberGenerationPolicy.fromFieldConfig(json, om);
        assertThat(p.getStrategy()).isEqualTo(DocumentNumberGenerationPolicy.Strategy.MONTHLY_SEQUENCE);
        assertThat(p.getPrefix()).isEqualTo("JV");
        assertThat(p.getSequenceWidth()).isEqualTo(6);
        assertThat(p.getTimeZone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(p.isAutoGenerate()).isTrue();
    }

    @Test
    void invalidTimeZoneFallsBackToUtc() throws Exception {
        String json = om.writeValueAsString(java.util.Map.of(
                "documentNumberGeneration", java.util.Map.of(
                        "strategy", "MONTHLY_SEQUENCE",
                        "timeZone", "Not/A/Zone"
                )
        ));
        DocumentNumberGenerationPolicy p = DocumentNumberGenerationPolicy.fromFieldConfig(json, om);
        assertThat(p.getTimeZone()).isEqualTo(ZoneId.of("UTC"));
    }
}
