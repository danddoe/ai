export type RegionRole = 'header' | 'detail' | 'tab';

export interface Presentation {
  label: string | null;
  placeholder: string;
  helpText: string;
  readOnly: boolean;
  hidden: boolean;
  width: string;
  componentHint: string;
}

/** Optional metadata for reference / lookup controls (validated server-side on layout save). */
export interface ReferenceLookupConfig {
  targetEntityId: string;
  displayTemplate?: string;
  searchFieldSlugs?: string[];
}

/** Save / cancel invoke host page handlers; link opens a validated URL or in-app path. */
export type LayoutActionType = 'save' | 'cancel' | 'link';

export type LayoutActionVariant = 'primary' | 'secondary' | 'link';

/** Field placement (default when `kind` is omitted). */
export interface LayoutItemField {
  kind?: 'field';
  id: string;
  fieldId: string | null;
  fieldSlug: string | null;
  presentation: Presentation;
  referenceLookup?: ReferenceLookupConfig;
}

export interface LayoutItemAction {
  kind: 'action';
  id: string;
  action: LayoutActionType;
  label: string;
  /** Required when action is `link`. */
  href?: string;
  openInNewTab?: boolean;
  variant?: LayoutActionVariant;
}

export type LayoutItem = LayoutItemField | LayoutItemAction;

export interface LayoutColumn {
  id: string;
  span: number;
  items: LayoutItem[];
}

export interface LayoutRow {
  id: string;
  columns: LayoutColumn[];
}

export interface LayoutRegion {
  id: string;
  role: RegionRole;
  title: string;
  tabGroupId: string | null;
  rows: LayoutRow[];
  binding?: { kind: string; relationshipId?: string };
}

/** Optional portal/runtime behavior for record entry (validated on layout save in entity-builder). */
export interface LayoutRuntimeRecordEntry {
  flow: 'free' | 'wizard';
  wizard?: {
    /** Region `id` values in step order; each must exist on the layout. */
    stepOrderRegionIds: string[];
  };
}

export interface LayoutRuntime {
  recordEntry?: LayoutRuntimeRecordEntry;
}

export interface LayoutV2 {
  version: 2;
  regions: LayoutRegion[];
  runtime?: LayoutRuntime;
}

export type StructureSelection =
  | { kind: 'region'; regionIndex: number }
  | { kind: 'row'; regionIndex: number; rowIndex: number }
  | { kind: 'column'; regionIndex: number; rowIndex: number; columnIndex: number }
  | { kind: 'item'; regionIndex: number; rowIndex: number; columnIndex: number; itemIndex: number };
