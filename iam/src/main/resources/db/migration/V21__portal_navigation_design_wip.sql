-- Work-in-progress portal links: sync with entity-builder list views / form layouts (no cross-DB FK).

ALTER TABLE portal_navigation_items
    ADD COLUMN IF NOT EXISTS design_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE portal_navigation_items
    ADD COLUMN IF NOT EXISTS linked_list_view_id UUID NULL;

ALTER TABLE portal_navigation_items
    ADD COLUMN IF NOT EXISTS linked_form_layout_id UUID NULL;
