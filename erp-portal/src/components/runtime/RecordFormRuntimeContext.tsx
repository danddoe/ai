import { createContext, useContext, type ReactNode } from 'react';
import type { EntityDto } from '../../api/schemas';

export type RecordFormRuntimeContextValue = {
  tenantId: string | null;
  /** Entity definition id for the record being edited (host entity), for scoped lookups e.g. entity_status. */
  hostEntityId: string | null;
  /** Target entities keyed by slug, for reference field resolution. */
  entityBySlug: Record<string, EntityDto>;
};

const defaultValue: RecordFormRuntimeContextValue = {
  tenantId: null,
  hostEntityId: null,
  entityBySlug: {},
};

const RecordFormRuntimeContext = createContext<RecordFormRuntimeContextValue>(defaultValue);

export function RecordFormRuntimeProvider({
  tenantId,
  hostEntityId,
  entityBySlug,
  children,
}: {
  tenantId: string | null;
  hostEntityId?: string | null;
  entityBySlug: Record<string, EntityDto>;
  children: ReactNode;
}) {
  return (
    <RecordFormRuntimeContext.Provider value={{ tenantId, hostEntityId: hostEntityId ?? null, entityBySlug }}>
      {children}
    </RecordFormRuntimeContext.Provider>
  );
}

export function useRecordFormRuntime(): RecordFormRuntimeContextValue {
  return useContext(RecordFormRuntimeContext);
}
