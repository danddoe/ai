package com.erp.entitybuilder.domain;

/**
 * Discriminator for {@link EntityStatusAssignment}: scope id refers to an entity definition row or a field row.
 */
public enum AssignmentScope {
    ENTITY_DEFINITION,
    ENTITY_FIELD
}
