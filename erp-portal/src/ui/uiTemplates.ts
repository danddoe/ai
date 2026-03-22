/**
 * Presets for the Create UI wizard (template → entity → IAM nav row).
 * Routes must stay aligned with {@link PortalNavigationAdminService} allowlist on the IAM side.
 */

export type UiTemplateId = 'list_only' | 'list_and_form' | 'multi_step' | 'form_landing';

export const UI_TEMPLATES: {
  id: UiTemplateId;
  title: string;
  description: string;
}[] = [
  {
    id: 'list_only',
    title: 'List only',
    description: 'Opens the record list for the entity (no builder changes).',
  },
  {
    id: 'list_and_form',
    title: 'List + form',
    description: 'Same entry as list; add/edit uses the default form layout (no builder changes).',
  },
  {
    id: 'multi_step',
    title: 'Multi-step (wizard)',
    description: 'Sets default layout runtime to wizard mode (one step per region, in order). Requires a default layout.',
  },
  {
    id: 'form_landing',
    title: 'Form landing',
    description: 'Jumps straight to “new record” for the entity. Nav suggests records:write in addition to read.',
  },
];

export function routePathForTemplate(template: UiTemplateId, entityId: string): string {
  const base = `/entities/${entityId}/records`;
  if (template === 'form_landing') return `${base}/new`;
  return base;
}

export function requiredPermissionsForTemplate(template: UiTemplateId): string[] {
  const read = 'entity_builder:records:read';
  if (template === 'form_landing') {
    return [read, 'entity_builder:records:write'];
  }
  return [read];
}

export function searchKeywordsForEntity(name: string, slug: string): string[] {
  const k = [name, slug, 'records', 'list'];
  return [...new Set(k.map((x) => x.trim()).filter(Boolean))];
}
