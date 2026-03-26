import {
  patchFormLayout,
  patchNavigationItem,
  patchRecordListView,
} from '../api/schemas';
import { clearPortalNavigationCache } from '../hooks/usePortalNavigation';

export type PublishDesignParams = {
  entityId: string;
  navigationItemId: string;
  listViewId?: string | null;
  formLayoutId?: string | null;
};

/**
 * Marks linked list view + form layout ACTIVE and navigation PUBLISHED (Create UI WIP flow).
 */
export async function publishDesignArtifacts(params: PublishDesignParams): Promise<void> {
  const { entityId, navigationItemId, listViewId, formLayoutId } = params;
  if (listViewId) {
    await patchRecordListView(entityId, listViewId, { status: 'ACTIVE' });
  }
  if (formLayoutId) {
    await patchFormLayout(entityId, formLayoutId, { status: 'ACTIVE' });
  }
  await patchNavigationItem(navigationItemId, { designStatus: 'PUBLISHED' });
  clearPortalNavigationCache();
}
