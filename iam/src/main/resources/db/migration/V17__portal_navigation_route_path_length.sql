-- Longer routes for entity records list config (cols, inline, actions, etc.) in query string.
ALTER TABLE portal_navigation_items ALTER COLUMN route_path TYPE VARCHAR(2048);
