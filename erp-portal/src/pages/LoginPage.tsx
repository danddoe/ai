import { FormEvent, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

export function LoginPage() {
  const { accessToken, login, sessionRestored } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/home';

  const [tenantSlugOrId, setTenantSlugOrId] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  if (!sessionRestored) {
    return (
      <div style={{ maxWidth: 360, margin: '4rem auto', padding: '0 1rem', textAlign: 'center' }}>
        <p className="builder-muted">Checking session…</p>
      </div>
    );
  }

  if (accessToken) {
    return <Navigate to={from} replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      await login(tenantSlugOrId.trim(), email.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setPending(false);
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: '4rem auto', padding: '0 1rem' }}>
      <h1 style={{ fontSize: '1.25rem' }}>Sign in</h1>
      <p style={{ color: '#52525b', fontSize: '0.875rem' }}>
        API gateway + IAM. Refresh token is stored in an httpOnly cookie when IAM cookie mode is on.
      </p>
      <form onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: '0.75rem', marginTop: '1.5rem' }}>
        <label style={{ display: 'grid', gap: '0.25rem', fontSize: '0.875rem' }}>
          Tenant slug or ID
          <input
            value={tenantSlugOrId}
            onChange={(e) => setTenantSlugOrId(e.target.value)}
            required
            autoComplete="organization"
            style={{ padding: '0.5rem 0.6rem', borderRadius: 6, border: '1px solid #d4d4d8' }}
          />
        </label>
        <label style={{ display: 'grid', gap: '0.25rem', fontSize: '0.875rem' }}>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
            style={{ padding: '0.5rem 0.6rem', borderRadius: 6, border: '1px solid #d4d4d8' }}
          />
        </label>
        <label style={{ display: 'grid', gap: '0.25rem', fontSize: '0.875rem' }}>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            style={{ padding: '0.5rem 0.6rem', borderRadius: 6, border: '1px solid #d4d4d8' }}
          />
        </label>
        {error && (
          <p role="alert" style={{ color: '#b91c1c', fontSize: '0.875rem', margin: 0 }}>
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={pending}
          style={{
            padding: '0.6rem',
            borderRadius: 6,
            border: 'none',
            background: '#18181b',
            color: '#fff',
          }}
        >
          {pending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
