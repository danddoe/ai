import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthProvider';
import { AppShell } from './layouts/AppShell';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { EntitiesPage } from './pages/EntitiesPage';
import { EntityLayoutsPage } from './pages/EntityLayoutsPage';
import { FormBuilderPage } from './pages/FormBuilderPage';
import { ListViewDesignerPage } from './pages/ListViewDesignerPage';
import { FormLayoutPreviewPage } from './pages/FormLayoutPreviewPage';
import { LoanHybridDemoPage } from './pages/LoanHybridDemoPage';
import { EntityRecordsListPage } from './pages/EntityRecordsListPage';
import { RecordFormPage } from './pages/RecordFormPage';
import { AuditHubPage } from './pages/AuditHubPage';
import { EntityAuditPage } from './pages/EntityAuditPage';
import { CreateUiWizardPage } from './pages/CreateUiWizardPage';
import { PortalNavigationItemsPage } from './pages/PortalNavigationItemsPage';

function Protected({ children }: { children: ReactNode }) {
  const { accessToken, sessionRestored } = useAuth();
  const location = useLocation();
  if (!sessionRestored) {
    return (
      <div className="page-shell" style={{ padding: '2rem', textAlign: 'center' }}>
        <p className="builder-muted">Restoring session…</p>
      </div>
    );
  }
  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <Protected>
            <AppShell />
          </Protected>
        }
      >
        <Route index element={<Navigate to="home" replace />} />
        <Route path="home" element={<HomePage />} />
        <Route path="audit" element={<AuditHubPage />} />
        <Route path="ui/create" element={<CreateUiWizardPage />} />
        <Route path="settings/navigation-items" element={<PortalNavigationItemsPage />} />
        <Route path="entities" element={<EntitiesPage />} />
        <Route path="entities/:entityId/layouts" element={<EntityLayoutsPage />} />
        <Route path="entities/:entityId/layouts/:layoutId/preview" element={<FormLayoutPreviewPage />} />
        <Route path="entities/:entityId/layouts/:layoutId" element={<FormBuilderPage />} />
        <Route path="entities/:entityId/list-views/:viewId" element={<ListViewDesignerPage />} />
        {/* :recordId is "new" for create — do not use a separate /records/new path (no :recordId param breaks RecordFormPage). */}
        <Route path="entities/:entityId/audit" element={<EntityAuditPage />} />
        <Route path="entities/:entityId/records/:recordId" element={<RecordFormPage />} />
        <Route path="entities/:entityId/records" element={<EntityRecordsListPage />} />
        <Route path="loans-module/hybrid-demo" element={<LoanHybridDemoPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
