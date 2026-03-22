import type { ReactElement, SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

function SvgFrame(props: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      width="1em"
      height="1em"
      {...props}
    />
  );
}

function IconLayers(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <polygon points="12 2 2 7 12 12 22 7 12 2" />
      <polyline points="2 17 12 22 22 17" />
      <polyline points="2 12 12 17 22 12" />
    </SvgFrame>
  );
}

function IconLayoutList(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </SvgFrame>
  );
}

function IconHome(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <polyline points="9 22 9 12 15 12 15 22" />
    </SvgFrame>
  );
}

function IconFolder(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </SvgFrame>
  );
}

function IconFileText(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </SvgFrame>
  );
}

function IconUsers(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </SvgFrame>
  );
}

function IconSettings(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
    </SvgFrame>
  );
}

function IconLock(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </SvgFrame>
  );
}

function IconBuilding(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4" />
      <line x1="9" y1="9" x2="9" y2="9.01" />
      <line x1="9" y1="12" x2="9" y2="12.01" />
      <line x1="9" y1="15" x2="9" y2="15.01" />
      <line x1="9" y1="18" x2="9" y2="18.01" />
    </SvgFrame>
  );
}

function IconChart(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <line x1="18" y1="20" x2="18" y2="10" />
      <line x1="12" y1="20" x2="12" y2="4" />
      <line x1="6" y1="20" x2="6" y2="14" />
    </SvgFrame>
  );
}

function IconSearch(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <circle cx="11" cy="11" r="8" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </SvgFrame>
  );
}

function IconDatabase(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
      <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
    </SvgFrame>
  );
}

function IconBriefcase(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
      <path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2" />
    </SvgFrame>
  );
}

function IconShopping(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <circle cx="9" cy="21" r="1" />
      <circle cx="20" cy="21" r="1" />
      <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
    </SvgFrame>
  );
}

function IconCredit(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
      <line x1="1" y1="10" x2="23" y2="10" />
    </SvgFrame>
  );
}

function IconPackage(p: IconProps) {
  return (
    <SvgFrame {...p}>
      <path d="M12 22V12M12 12L2 7l10-5 10 5-10 5" />
      <path d="M2 17l10 5 10-5M2 12l10 5 10-5" />
    </SvgFrame>
  );
}

export type PortalNavIconOption = {
  /** Stored in IAM `portal_navigation_items.icon` */
  key: string;
  label: string;
  Icon: (props: IconProps) => ReactElement;
};

export const PORTAL_NAV_ICON_OPTIONS: PortalNavIconOption[] = [
  { key: 'layers', label: 'Layers (stack)', Icon: IconLayers },
  { key: 'layout-list', label: 'Layout list', Icon: IconLayoutList },
  { key: 'home', label: 'Home', Icon: IconHome },
  { key: 'folder', label: 'Folder', Icon: IconFolder },
  { key: 'file-text', label: 'Document', Icon: IconFileText },
  { key: 'users', label: 'Users', Icon: IconUsers },
  { key: 'settings', label: 'Settings', Icon: IconSettings },
  { key: 'lock', label: 'Lock / security', Icon: IconLock },
  { key: 'building', label: 'Building', Icon: IconBuilding },
  { key: 'chart', label: 'Chart', Icon: IconChart },
  { key: 'search', label: 'Search', Icon: IconSearch },
  { key: 'database', label: 'Database', Icon: IconDatabase },
  { key: 'briefcase', label: 'Briefcase', Icon: IconBriefcase },
  { key: 'shopping', label: 'Shopping', Icon: IconShopping },
  { key: 'credit', label: 'Payment / card', Icon: IconCredit },
  { key: 'package', label: 'Package', Icon: IconPackage },
];

const ICON_BY_KEY: Record<string, PortalNavIconOption> = Object.fromEntries(
  PORTAL_NAV_ICON_OPTIONS.map((o) => [o.key, o])
);

export function portalNavIconKeys(): string[] {
  return PORTAL_NAV_ICON_OPTIONS.map((o) => o.key);
}

type PortalNavIconProps = {
  name: string | null | undefined;
  className?: string;
  title?: string;
};

/**
 * Renders a registered SVG icon, or plain text for unknown keys (legacy data).
 */
export function PortalNavIcon({ name, className, title }: PortalNavIconProps) {
  if (!name || !name.trim()) return null;
  const key = name.trim();
  const def = ICON_BY_KEY[key];
  if (!def) {
    return (
      <span className={className} title={title ?? key} aria-hidden>
        {key}
      </span>
    );
  }
  const Icon = def.Icon;
  return (
    <span className={className ?? 'portal-nav-icon'} title={title ?? def.label} aria-hidden>
      <Icon />
    </span>
  );
}
