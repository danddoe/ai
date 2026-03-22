package com.erp.entitybuilder.domain;

import com.erp.entitybuilder.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityCategoryKeysTest {

    @Test
    void normalizeOrNull_blankBecomesNull() {
        assertThat(EntityCategoryKeys.normalizeOrNull(null)).isNull();
        assertThat(EntityCategoryKeys.normalizeOrNull("")).isNull();
        assertThat(EntityCategoryKeys.normalizeOrNull("  ")).isNull();
    }

    @Test
    void normalizeOrNull_acceptsAllowedKey() {
        assertThat(EntityCategoryKeys.normalizeOrNull("accounts_payable")).isEqualTo("accounts_payable");
        assertThat(EntityCategoryKeys.normalizeOrNull("  general_ledger  ")).isEqualTo("general_ledger");
        assertThat(EntityCategoryKeys.normalizeOrNull("master_data")).isEqualTo("master_data");
    }

    @Test
    void normalizeOrNull_rejectsUnknown() {
        assertThatThrownBy(() -> EntityCategoryKeys.normalizeOrNull("crm"))
                .isInstanceOf(ApiException.class);
    }
}
