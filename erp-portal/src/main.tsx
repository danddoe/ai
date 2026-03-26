import './i18n/config';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { AuthProvider } from './auth/AuthProvider';
import { App } from './App';
import { erpMantineTheme } from './mantineTheme';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import './index.css';

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
