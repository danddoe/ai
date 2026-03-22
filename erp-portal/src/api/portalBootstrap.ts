import { apiBaseUrl } from './client';

export type PortalBindingScope = 'TENANT' | 'COMPANY' | 'BUSINESS_UNIT';

export type PortalBootstrapPayload = {
  tenantId: string | null;
  companyId: string | null;
  defaultBuId: string | null;
  scope: PortalBindingScope | null;
};

/**
 * Public bootstrap: maps current host (or optional hostname override) to default tenant / company / BU.
 * Uses the API base URL; send `hostname` when the browser host is not the custom domain (e.g. local dev).
 */
export async function fetchPortalBootstrap(hostnameOverride?: string): Promise<PortalBootstrapPayload | null> {
  const q =
    hostnameOverride && hostnameOverride.trim() !== ''
      ? `?hostname=${encodeURIComponent(hostnameOverride.trim())}`
      : '';
  const res = await fetch(`${apiBaseUrl()}/v1/portal/bootstrap${q}`, {
    credentials: 'include',
  });
  if (!res.ok) {
    return null;
  }
  return (await res.json()) as PortalBootstrapPayload;
}
