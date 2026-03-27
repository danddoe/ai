/** System catalog entity slug for platform record statuses (reference field targets). */
export const ENTITY_STATUS_ENTITY_SLUG = 'entity_status';

/**
 * System entity for **configuration rows** (which statuses apply per entity/field). Not a valid picklist target —
 * use {@link ENTITY_STATUS_ENTITY_SLUG} on record fields instead.
 */
export const ENTITY_STATUS_ASSIGNMENT_ENTITY_SLUG = 'entity_status_assignment';
