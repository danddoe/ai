package com.erp.entitybuilder.service.search;

import com.erp.entitybuilder.domain.EntityField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSearchabilityTest {

    @Test
    void isSearchable_readsCamelAndSnakeCase() {
        EntityField f = new EntityField();
        f.setConfig("{\"isSearchable\": true}");
        assertThat(FieldSearchability.isSearchable(f)).isTrue();

        f.setConfig("{\"is_searchable\": true}");
        assertThat(FieldSearchability.isSearchable(f)).isTrue();

        f.setConfig("{\"isSearchable\": false}");
        assertThat(FieldSearchability.isSearchable(f)).isFalse();

        f.setConfig(null);
        assertThat(FieldSearchability.isSearchable(f)).isFalse();
    }

    @Test
    void isSearchable_acceptsTruthyNonBooleanJson() {
        EntityField f = new EntityField();
        f.setConfig("{\"isSearchable\": 1}");
        assertThat(FieldSearchability.isSearchable(f)).isTrue();

        f.setConfig("{\"isSearchable\": \"true\"}");
        assertThat(FieldSearchability.isSearchable(f)).isTrue();

        f.setConfig("{\"is_searchable\": 1}");
        assertThat(FieldSearchability.isSearchable(f)).isTrue();
    }

    @Test
    void escapeLikePattern_escapesSpecialChars() {
        assertThat(SearchLikeEscape.escapeLikePattern("a%b_c\\d")).isEqualTo("a!%b!_c\\d");
        assertThat(SearchLikeEscape.escapeLikePattern("x!y")).isEqualTo("x!!y");
    }
}
