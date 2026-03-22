# Portal navigation (IAM)

Global ERP portal menu and search metadata live in **IAM** so one service owns **who sees which shell routes** for the single SPA.

## Storage

Table **`portal_navigation_items`** (Flyway `V11`, seed `V12`):

- **Tree:** `parent_id`, `sort_order` — arbitrary depth; nested `children` in the API response.
- **Categorization:** `category_key` (nullable varchar, e.g. `entity_builder`, `accounting`, `accounts_payable`) — module / product-area bucket for grouped sidebars and search facets. If null on a row, the API **inherits** the nearest ancestor’s category when building the DTO so deep tree nodes still carry a stable bucket.
- **Route:** `route_path` (required for `internal` / `external`; optional for `section` / `divider`)
- **Presentation:** `label`, `description`, `type`, `icon`
- **Search:** `search_keywords` (JSON array of strings)
- **Access:** `required_permissions`, `required_roles` (JSON arrays of strings)
- **Lifecycle:** `is_active`, timestamps

## API

- **`GET /v1/navigation`** (authenticated) – Returns `{ "items": [ … ] }` with nested `children`. Server filters rows using the caller’s JWT permissions and roles. Empty requirement arrays mean “any authenticated user.”

## Semantics

- **OR** inside `required_permissions`: user needs **at least one** listed permission.
- **OR** inside `required_roles`: user needs **at least one** listed role name (as issued in the JWT).
- If **both** lists are non-empty, the user must satisfy **both** checks.
- Nav visibility does **not** grant API access; modules must keep their own guards.

## Portal integration

The SPA should call the gateway (`GET /v1/navigation`), render a tree, and merge API-backed entries (e.g. entities) into **global search** as needed. Icon values are opaque keys (e.g. `layers`) resolved in the frontend.

## Admin CRUD

v1 ships **read-only** API + Flyway seed. Add admin endpoints or a back-office flow later to mutate `portal_navigation_items` without new migrations.
