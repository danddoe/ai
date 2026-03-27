-- Per-field flag: field contributes to concatenated record list "Display" column (basic layout).

ALTER TABLE entity_fields
    ADD COLUMN IF NOT EXISTS include_in_list_summary_display BOOL NOT NULL DEFAULT false;
