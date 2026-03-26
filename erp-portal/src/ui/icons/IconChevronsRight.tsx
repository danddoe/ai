import type { SVGProps } from 'react';

/** Inline SVG (Tabler-style double chevron) — avoids @tabler/icons-react barrel + fragile installs on Windows. */
export function IconChevronsRight({
  size = 18,
  stroke = 1.5,
  ...props
}: SVGProps<SVGSVGElement> & { size?: number; stroke?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      <path d="M7 7l5 5-5 5" />
      <path d="M13 7l5 5-5 5" />
    </svg>
  );
}
