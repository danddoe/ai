import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import {
  createExtensionRecord,
  createLoan,
  getEntityBySlug,
  loadMergedLoanView,
  syncCatalog,
  type LoanDto,
} from '../api/hybridLoan';

export function LoanHybridDemoPage() {
  const { tenantId: tenantFromAuth } = useAuth();
  const [tenantId, setTenantId] = useState(tenantFromAuth);

  useEffect(() => {
    if (tenantFromAuth) setTenantId(tenantFromAuth);
  }, [tenantFromAuth]);
  const [status, setStatus] = useState('DRAFT');
  const [amount, setAmount] = useState('10000');
  const [productCode, setProductCode] = useState('');
  const [branchNotes, setBranchNotes] = useState('');
  const [loan, setLoan] = useState<LoanDto | null>(null);
  const [merged, setMerged] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const runSync = useCallback(async () => {
    setError(null);
    setBusy(true);
    try {
      await syncCatalog(tenantId.trim());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }, [tenantId]);

  const runCreate = useCallback(async () => {
    setError(null);
    setBusy(true);
    try {
      const tid = tenantId.trim();
      const body: { status: string; requestedAmount: string; productCode?: string } = {
        status: status.trim(),
        requestedAmount: amount.trim(),
      };
      if (productCode.trim()) body.productCode = productCode.trim();
      const created = await createLoan(tid, body);
      setLoan(created);
      const entity = await getEntityBySlug('loan_application');
      if (branchNotes.trim()) {
        await createExtensionRecord(tid, entity.id, created.id, { branch_notes: branchNotes.trim() });
      }
      const view = await loadMergedLoanView(tid, created.id);
      setMerged(view);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }, [tenantId, status, amount, productCode, branchNotes]);

  const reloadMerged = useCallback(async () => {
    if (!loan) return;
    setError(null);
    setBusy(true);
    try {
      const view = await loadMergedLoanView(tenantId.trim(), loan.id);
      setMerged(view);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }, [loan, tenantId]);

  return (
    <div className="page-shell page-shell-wide">
      <h1 className="page-title">Hybrid loan demo</h1>
      <p className="page-sub">
        Core fields live in the <strong>loans-module</strong> service; extension values use{' '}
        <code>entity_record_values</code> with <code>external_id = loan id</code>. Merge happens in the browser (same
        pattern a BFF would use).
      </p>
      <p className="builder-muted">
        <Link to="/home">← Home</Link>
      </p>

      <section style={{ marginTop: 24 }}>
        <label className="builder-field-label">
          Tenant ID (UUID)
          <input
            className="builder-input"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            placeholder="From JWT tenant_id"
            style={{ width: '100%', maxWidth: 420 }}
          />
        </label>
      </section>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button type="button" className="builder-btn builder-btn-secondary" disabled={busy} onClick={runSync}>
          Sync system catalog (loan_application)
        </button>
      </div>

      <h2 style={{ marginTop: 32 }}>Create loan</h2>
      <div className="builder-form-grid" style={{ maxWidth: 480 }}>
        <label className="builder-field-label">
          Status
          <input className="builder-input" value={status} onChange={(e) => setStatus(e.target.value)} />
        </label>
        <label className="builder-field-label">
          Requested amount
          <input className="builder-input" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </label>
        <label className="builder-field-label">
          Product code (optional)
          <input className="builder-input" value={productCode} onChange={(e) => setProductCode(e.target.value)} />
        </label>
        <label className="builder-field-label">
          Branch notes (EAV extension)
          <input className="builder-input" value={branchNotes} onChange={(e) => setBranchNotes(e.target.value)} />
        </label>
      </div>
      <button type="button" className="builder-btn builder-btn-primary" style={{ marginTop: 12 }} disabled={busy} onClick={runCreate}>
        Create + merge
      </button>

      {loan && (
        <button type="button" className="builder-btn builder-btn-secondary" style={{ marginLeft: 8 }} disabled={busy} onClick={reloadMerged}>
          Reload merged view
        </button>
      )}

      {error && (
        <p role="alert" className="text-error" style={{ marginTop: 16 }}>
          {error}
        </p>
      )}

      {merged && (
        <pre
          style={{
            marginTop: 24,
            padding: 16,
            background: 'var(--builder-surface-2, #1e1e1e)',
            borderRadius: 8,
            overflow: 'auto',
            fontSize: 13,
          }}
        >
          {JSON.stringify(merged, null, 2)}
        </pre>
      )}
    </div>
  );
}
