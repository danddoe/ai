import './i18n/config';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { AuthProvider } from './auth/AuthProvider';
import { App } from './App';
import { erpMantineTheme } from './mantineTheme';
import { DEBUG_ENTITY_STATUS_KEY, isEntityStatusAssignmentDebugEnabled } from './api/schemas';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import './index.css';

/** DevTools: {@code window.__erpDebugEntityStatus.enabled()} or {@code .isEnabled()} */
if (typeof window !== 'undefined') {
  const isEnabled = isEntityStatusAssignmentDebugEnabled;
  (window as Window & {
    __erpDebugEntityStatus?: { isEnabled: () => boolean; enabled: () => boolean; key: string };
  }).__erpDebugEntityStatus = {
    isEnabled,
    enabled: isEnabled,
    key: DEBUG_ENTITY_STATUS_KEY,
  };
  if (isEntityStatusAssignmentDebugEnabled()) {
    console.log(
      `[${DEBUG_ENTITY_STATUS_KEY}] tracing ON — open a record form: every reference field logs a "reference field snapshot" (why listRecords may not run). Dropdown mode → listRecords; Search mode → open modal and type 2+ chars for lookupRecords.`
    );
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <MantineProvider theme={erpMantineTheme} defaultColorScheme="light">
        <Notifications position="top-right" zIndex={4000} />
        <AuthProvider>
          <App />
        </AuthProvider>
      </MantineProvider>
    </BrowserRouter>
  </StrictMode>
);
