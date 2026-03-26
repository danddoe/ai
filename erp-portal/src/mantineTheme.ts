import { createTheme } from '@mantine/core';

/** Aligns with prior ERP portal neutrals (dark primary actions, system UI stack). */
export const erpMantineTheme = createTheme({
  primaryColor: 'dark',
  defaultRadius: 'md',
  fontFamily: 'system-ui, "Segoe UI", Roboto, sans-serif',
  headings: {
    fontFamily: 'system-ui, "Segoe UI", Roboto, sans-serif',
  },
});
