import { Command } from 'cmdk';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchOmnibox, type OmniboxItem, type OmniboxResponse } from '../api/schemas';

const DEBOUNCE_MS = 250;

function formatCategory(cat: string): string {
  if (!cat) return 'General';
  return cat.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export function GlobalSearch() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [debounced, setDebounced] = useState('');
  const [data, setData] = useState<OmniboxResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(input.trim()), DEBOUNCE_MS);
    return () => window.clearTimeout(t);
  }, [input]);

  const runSearch = useCallback(async (q: string) => {
    abortRef.current?.abort();
    if (q.length < 2) {
      setData(null);
      setLoading(false);
      setError(null);
      return;
    }
    const ac = new AbortController();
    abortRef.current = ac;
    setLoading(true);
    setError(null);
    try {
      const res = await fetchOmnibox(q, ac.signal);
      if (!ac.signal.aborted) {
        setData(res);
      }
    } catch (e) {
      if (!ac.signal.aborted) {
        setData(null);
        setError(e instanceof Error ? e.message : 'Search failed');
      }
    } finally {
      if (!ac.signal.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    void runSearch(debounced);
  }, [debounced, open, runSearch]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen((o) => !o);
      }
      if (e.key === 'Escape') {
        setOpen(false);
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (open) {
      setInput('');
      setDebounced('');
      setData(null);
      setError(null);
      queueMicrotask(() => {
        const el = panelRef.current?.querySelector<HTMLInputElement>('[cmdk-input]');
        el?.focus();
      });
    }
  }, [open]);

  useEffect(() => {
    function onMouseDown(e: MouseEvent) {
      if (open && !panelRef.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener('mousedown', onMouseDown);
      return () => document.removeEventListener('mousedown', onMouseDown);
    }
  }, [open]);

  function go(hit: OmniboxItem) {
    const url = hit.url;
    if (!url) return;
    if (url.startsWith('http')) {
      window.open(url, '_blank', 'noopener,noreferrer');
    } else {
      navigate(url);
    }
    setOpen(false);
    setInput('');
  }

  const navItems = data?.navigation ?? [];
  const recordItems = data?.records ?? [];
  const deepItems = data?.deepHistory ?? [];

  return (
    <div className="global-search" ref={panelRef}>
      <button
        type="button"
        className="global-search-trigger"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="dialog"
      >
        <span className="global-search-placeholder">Search…</span>
        <kbd className="global-search-kbd">
          {typeof navigator !== 'undefined' && /Mac/i.test(navigator.platform) ? '⌘K' : 'Ctrl+K'}
        </kbd>
      </button>
      {open && (
        <div className="global-search-panel" role="dialog" aria-label="Global search">
          <Command shouldFilter={false} loop>
            <Command.Input
              className="global-search-input"
              placeholder="Search navigation, records, activity…"
              value={input}
              onValueChange={setInput}
              aria-autocomplete="list"
            />
            {error && (
              <p className="global-search-hint text-error" role="status">
                {error}
              </p>
            )}
            {loading && debounced.length >= 2 && (
              <p className="global-search-hint" role="status">
                Searching…
              </p>
            )}
            <Command.List className="global-search-results" id="global-search-results">
              <Command.Empty>
                {debounced.length < 2 ? 'Type at least 2 characters' : loading ? '…' : 'No matches'}
              </Command.Empty>

              {navItems.length > 0 && (
                <Command.Group heading="Navigation">
                  {navItems.map((h) => (
                    <Command.Item
                      key={h.id}
                      value={h.id}
                      className="global-search-hit"
                      onSelect={() => go(h)}
                    >
                      <span className="global-search-hit-title">{h.title}</span>
                      {h.subtitle ? <code className="global-search-hit-sub">{h.subtitle}</code> : null}
                      <span className="global-search-hit-meta">
                        {formatCategory('navigation')} · App
                      </span>
                    </Command.Item>
                  ))}
                </Command.Group>
              )}

              {recordItems.length > 0 && (
                <Command.Group heading="Records">
                  {recordItems.map((h) => (
                    <Command.Item
                      key={h.id}
                      value={h.id}
                      className="global-search-hit"
                      onSelect={() => go(h)}
                    >
                      <span className="global-search-hit-title">{h.title}</span>
                      {h.subtitle ? <code className="global-search-hit-sub">{h.subtitle}</code> : null}
                      <span className="global-search-hit-meta">
                        {formatCategory('records')} · Record
                      </span>
                    </Command.Item>
                  ))}
                </Command.Group>
              )}

              {deepItems.length > 0 && (
                <Command.Group heading="Deep history">
                  {deepItems.map((h) => (
                    <Command.Item
                      key={h.id}
                      value={h.id}
                      className="global-search-hit"
                      onSelect={() => go(h)}
                    >
                      <span className="global-search-hit-title">{h.title}</span>
                      {h.subtitle ? <code className="global-search-hit-sub">{h.subtitle}</code> : null}
                      <span className="global-search-hit-meta">{formatCategory('deep_history')}</span>
                    </Command.Item>
                  ))}
                </Command.Group>
              )}
            </Command.List>
          </Command>
          <button type="button" className="global-search-close link-btn" onClick={() => setOpen(false)}>
            Close
          </button>
        </div>
      )}
    </div>
  );
}
