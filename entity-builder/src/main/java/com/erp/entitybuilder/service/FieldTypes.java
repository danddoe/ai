package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;

/**
 * Well-known {@link EntityField#getFieldType()} values beyond primitive EAV types.
 * {@link #DOCUMENT_NUMBER} is stored as {@link com.erp.entitybuilder.domain.EntityRecord#getBusinessDocumentNumber()}.
 */
public final class FieldTypes {

    public static final String DOCUMENT_NUMBER = "document_number";

    private FieldTypes() {}

    public static boolean isDocumentNumber(EntityField field) {
        return field != null
                && field.getFieldType() != null
                && DOCUMENT_NUMBER.equalsIgnoreCase(field.getFieldType().trim());
    }
}
