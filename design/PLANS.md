# Plans and design docs

## Where things live

- **Living specs and hand-written plans** (edit these in git): directly under [`design/`](.) — e.g. [`Dynamic_entity_builder_plan.md`](Dynamic_entity_builder_plan.md), [`Dynamic_entity_builder.md`](Dynamic_entity_builder.md), [`Entity_Form_Builder_UI.md`](Entity_Form_Builder_UI.md).

- **Cursor-generated plan exports** (copied from `~/.cursor/plans/`): [`design/plans/`](plans/) — each file keeps the original Cursor filename (`<name>_<id>.plan.md`).

## `design/plans/` contents (snapshot)

| File | Topic (from title / name) |
|------|---------------------------|
| `single_search_vector_b5254630.plan.md` | Single search vector + lookup API |
| `dynamic_entity_builder_backend_1dec2c27.plan.md` | Dynamic entity builder backend |
| `entity_form_builder_ui_476314b8.plan.md` | Entity form builder UI |
| `kong_gateway_bff_setup_b8e5ae18.plan.md` | Kong gateway BFF |
| `nav_registry_and_search_31af4fbe.plan.md` | Navigation registry / search |
| `iam_*.plan.md` | Various IAM / Gradle / E2E plans |
| `dv-register-report-launcher_21e3498c.plan.md` | External tool plan |

## Convention for new work

When a new Cursor plan is created, copy it into `design/plans/` (or save the plan content there from the start) so it is versioned with the repo:

```powershell
Copy-Item "$env:USERPROFILE\.cursor\plans\<newfile>.plan.md" -Destination "c:\project\ai\design\plans\"
```

Optionally add a one-line entry to the table above.
