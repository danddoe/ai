---
name: Entity Form Builder UI
overview: "Admin-only v1: React+TS SPA via API gateway; layout JSON v2 with header/detail/tab regions; classpath templates; GET entities/fields; memory access token + httpOnly refresh cookie; keyboard-first builder (no DnD required). Full spec in repo design/Entity_Form_Builder_UI.md."
todos:
  - id: auth-cookie-gateway
    content: Gateway/IAM — Set-Cookie httpOnly refresh on login; refresh endpoint uses cookie; SPA access token in memory + credentials include
    status: pending
  - id: gateway-routes-templates
    content: api-gateway — Route GET /v1/form-layout-templates** to entity-builder if added at root v1
    status: pending
  - id: api-list-entities-fields
    content: entity-builder — GET /v1/entities (list), GET /v1/entities/{id}/fields
    status: pending
  - id: layout-json-v2-regions
    content: Document + validate layout version 2 (regions header/detail/tab); PATCH/create accept v2
    status: pending
  - id: templates-classpath
    content: entity-builder — form-layout-library on classpath; GET templates metadata; POST form-layouts/from-template
    status: pending
  - id: isdefault-constraint
    content: FormLayoutService — at most one isDefault per entity per tenant (+ optional DB constraint)
    status: pending
  - id: spa-react
    content: New web app — React+TS, BrowserRouter deep links, static hosting with index.html fallback, VITE_API_BASE_URL to gateway
    status: pending
  - id: builder-a11y
    content: Builder UI — keyboard structure editor (add field, move up/down); no DnD required for v1
    status: pending
isProject: false
---

# Entity & Form Builder UI

**Canonical specification:** repo file `design/Entity_Form_Builder_UI.md`.

## Locked decisions (summary)


| Area      | Choice                                                                                         |
| --------- | ---------------------------------------------------------------------------------------------- |
| Scope     | Admin builder only; **no** end-user runtime in v1                                              |
| Layout    | **Regions:** header, detail, tabs (`version: 2` JSON)                                          |
| Templates | **Classpath JSON only** (no DB template table v1)                                              |
| UI stack  | **React + TypeScript** static SPA                                                              |
| URLs      | Honor deep links; deploy **fallback to index.html**                                            |
| Traffic   | Browser → **API gateway** only                                                                 |
| Auth      | Access token **memory**; refresh **httpOnly** cookie; `fetch(..., { credentials: 'include' })` |
| APIs      | Add **GET /v1/entities**, **GET /v1/entities/{id}/fields**                                     |
| Layouts   | Many per entity; **one `isDefault`** per entity                                                |
| A11y      | **Keyboard-first**; **non-DnD** editing required for v1                                        |

