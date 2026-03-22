import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  createFormLayout,
  getEntity,
  listFormLayouts,
  listRecordListViews,
  type EntityDto,
  type FormLayoutDto,
  type RecordListViewDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { blankLayoutV2 } from '../utils/layoutV2';
import { TemplatePickerModal } from '../components/builder/TemplatePickerModal';
import { EntitySettingsModal } from '../components/EntitySettingsModal';

export function EntityLayoutsPage() {
  const { entityId = '' } = useParams();
  const navigate = useNavigate();
  const { canSchemaWrite } = useAuth();
  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [layouts, setLayouts] = useState<FormLayoutDto[] | null>(null);
  const [listViews, setListViews] = useState<RecordListViewDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tplOpen, setTplOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const load = useCallback(async () => {
    if (!entityId) return;
    setError(null);
    try {
      const [e, ls, lv] = await Promise.all([
        getEntity(entityId),
        listFormLayouts(entityId),
        listRecordListViews(entityId),
      ]);
      setEntity(e);
      setLayouts(ls);
      setListViews(lv);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
      setEntity(null);
      setLayouts(null);
      setListViews(null);
    }
  }, [entityId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function newBlankLayout() {
    if (!canSchemaWrite) return;
    const name = window.prompt('Layout name?', 'New layout');
    if (!name?.trim()) return;
    try {
      const dto = await createFormLayout(entityId, {
        name: name.trim(),
        layout: blankLayoutV2(),
        isDefault: false,
      });
      navigate(`/entities/${entityId}/layouts/${dto.id}`);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Create failed');
    }
  }

  return (
    <div className="page-shell">
      <nav className="breadcrumb">
        <Link to="/entities">Entities</Link>
        <span aria-hidden> / </span>
        <span>{entity?.name ?? '…'}</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 id="form-layouts" className="page-title">
            Form layouts
          </h1>
          <p className="page-sub">{entity ? `${entity.name} — open or create a layout.` : 'Loading…'}</p>
          <p className="builder-muted" style={{ fontSize: '0.9rem', marginTop: 8, maxWidth: 640 }}>
            The layout marked <span className="pill pill-on">default</span> is the data entry form for{' '}
            <strong>New record</strong> and <strong>Edit record</strong>. Set it in the form builder (checkbox next to the
            layout name) or when creating from a template.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {canSchemaWrite && (
            <>
              <button type="button" className="btn btn-secondary" onClick={() => void newBlankLayout()}>
                New blank layout
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setTplOpen(true)}>
                From template
              </button>
            </>
          )}
          {entity && canSchemaWrite && (
            <button type="button" className="btn btn-secondary" onClick={() => setSettingsOpen(true)}>
              Entity settings
            </button>
          )}
          {entity && (
            <Link className="btn btn-secondary" to={`/entities/${entityId}/records`}>
              Records
            </Link>
          )}
          {canSchemaWrite && entity && (
            <Link className="btn btn-secondary" to={`/entities/${entityId}/list-views/new`}>
              New list view
            </Link>
          )}
        </div>
      </header>
      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      <ul className="layout-list">
        {layouts?.map((l) => (
          <li key={l.id}>
            <Link className="layout-card" to={`/entities/${entityId}/layouts/${l.id}`}>
              <span className="layout-card-name">{l.name}</span>
              {l.isDefault && <span className="pill pill-on">default</span>}
              <span className="entity-card-chev" aria-hidden>
                →
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {layouts && layouts.length === 0 && <p className="builder-muted">No layouts yet — create one.</p>}

      <h2 id="record-list-views" className="page-title" style={{ fontSize: '1.05rem', marginTop: 28 }}>
        Record list views
      </h2>
      <p className="builder-muted" style={{ marginBottom: 12, fontSize: '0.9rem' }}>
        Each view stores its own columns and row actions. The one marked <span className="pill pill-on">default</span>{' '}
        is what users see on <strong>Records</strong> when the URL has no <code>?view=</code> (you can still switch views
        from the dropdown there). Saving in the designer updates that view for all users immediately; use{' '}
        <strong>Open in records</strong> for a bookmarkable link to a specific view.
      </p>
      <ul className="layout-list">
        {listViews?.map((v) => (
          <li key={v.id}>
            <Link className="layout-card" to={`/entities/${entityId}/list-views/${v.id}`}>
              <span className="layout-card-name">{v.name}</span>
              {v.isDefault && <span className="pill pill-on">default</span>}
              <span className="entity-card-chev" aria-hidden>
                →
              </span>
            </Link>
            <div style={{ marginTop: 6, paddingLeft: 4 }}>
              <Link className="link-btn" to={`/entities/${entityId}/records?view=${v.id}`}>
                Open in records
              </Link>
            </div>
          </li>
        ))}
      </ul>
      {listViews && listViews.length === 0 && <p className="builder-muted">No saved list views yet.</p>}

      {tplOpen && (
        <TemplatePickerModal
          entityId={entityId}
          onClose={() => setTplOpen(false)}
          onCreated={(layoutId) => navigate(`/entities/${entityId}/layouts/${layoutId}`)}
        />
      )}
      {settingsOpen && entity && (
        <EntitySettingsModal
          entityId={entityId}
          entity={entity}
          onClose={() => setSettingsOpen(false)}
          onSaved={(e) => setEntity(e)}
        />
      )}
    </div>
  );
}
