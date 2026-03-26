package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTypesTest {

    @Test
    void isOptimisticVersionField_acceptsCaseInsensitiveSlugAndNumericAliases() {
        EntityField f1 = new EntityField();
        f1.setEntityId(UUID.randomUUID());
        f1.setName("Version");
        f1.setSlug("Version");
        f1.setFieldType("number");
        assertThat(FieldTypes.isOptimisticVersionField(f1)).isTrue();

        EntityField f2 = new EntityField();
        f2.setEntityId(UUID.randomUUID());
        f2.setName("Version");
        f2.setSlug(" version ");
        f2.setFieldType("NUMBER");
        assertThat(FieldTypes.isOptimisticVersionField(f2)).isTrue();

        EntityField f3 = new EntityField();
        f3.setEntityId(UUID.randomUUID());
        f3.setName("Version");
        f3.setSlug("version");
        f3.setFieldType("integer");
        assertThat(FieldTypes.isOptimisticVersionField(f3)).isTrue();

        EntityField f3b = new EntityField();
        f3b.setEntityId(UUID.randomUUID());
        f3b.setName("Version");
        f3b.setSlug("version");
        f3b.setFieldType("numeric(19,0)");
        assertThat(FieldTypes.isOptimisticVersionField(f3b)).isTrue();
        assertThat(FieldTypes.normalizeSqlFieldType("numeric(19,0)")).isEqualTo("numeric");
        assertThat(FieldTypes.isNumericFieldType("numeric(19,0)")).isTrue();

        EntityField f3c = new EntityField();
        f3c.setEntityId(UUID.randomUUID());
        f3c.setName("Version");
        f3c.setSlug("version");
        f3c.setFieldType(null);
        assertThat(FieldTypes.isOptimisticVersionField(f3c)).isTrue();

        EntityField f4 = new EntityField();
        f4.setEntityId(UUID.randomUUID());
        f4.setName("Not version");
        f4.setSlug("version_notes");
        f4.setFieldType("number");
        assertThat(FieldTypes.isOptimisticVersionField(f4)).isFalse();

        EntityField f5 = new EntityField();
        f5.setEntityId(UUID.randomUUID());
        f5.setName("Version label");
        f5.setSlug("version");
        f5.setFieldType("string");
        assertThat(FieldTypes.isOptimisticVersionField(f5)).isFalse();
    }
}
