-- Optional display / short name distinct from legal name

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS alias VARCHAR(255);

ALTER TABLE business_units
    ADD COLUMN IF NOT EXISTS alias VARCHAR(255);
