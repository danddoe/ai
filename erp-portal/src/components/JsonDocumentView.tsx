import { Fragment, useMemo } from 'react';

type Token = { kind: 'ws' | 'key' | 'string' | 'number' | 'keyword' | 'punct'; text: string };

function tokenizeHighlightedJson(formatted: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < formatted.length) {
    const c = formatted[i];
    if (/\s/.test(c)) {
      let j = i;
      while (j < formatted.length && /\s/.test(formatted[j])) j++;
      tokens.push({ kind: 'ws', text: formatted.slice(i, j) });
      i = j;
      continue;
    }
    if (c === '"') {
      let j = i + 1;
      let esc = false;
      while (j < formatted.length) {
        if (esc) {
          esc = false;
          j++;
          continue;
        }
        if (formatted[j] === '\\') {
          esc = true;
          j++;
          continue;
        }
        if (formatted[j] === '"') {
          j++;
          break;
        }
        j++;
      }
      const text = formatted.slice(i, j);
      let k = j;
      while (k < formatted.length && /\s/.test(formatted[k])) k++;
      const isKey = formatted[k] === ':';
      tokens.push({ kind: isKey ? 'key' : 'string', text });
      i = j;
      continue;
    }
    if (c === '-' || (c >= '0' && c <= '9')) {
      let j = i + 1;
      while (j < formatted.length && /[-0-9.eE+]/.test(formatted[j])) j++;
      tokens.push({ kind: 'number', text: formatted.slice(i, j) });
      i = j;
      continue;
    }
    if (c === 't' || c === 'f' || c === 'n') {
      const rest = formatted.slice(i);
      let matched = false;
      for (const w of ['true', 'false', 'null'] as const) {
        const after = i + w.length;
        if (
          rest.startsWith(w) &&
          (after >= formatted.length || !/[a-zA-Z0-9_]/.test(formatted[after]))
        ) {
          tokens.push({ kind: 'keyword', text: w });
          i += w.length;
          matched = true;
          break;
        }
      }
      if (matched) continue;
    }
    tokens.push({ kind: 'punct', text: c });
    i++;
  }
  return tokens;
}

function classForToken(kind: Token['kind']): string {
  switch (kind) {
    case 'key':
      return 'json-doc-key';
    case 'string':
      return 'json-doc-str';
    case 'number':
      return 'json-doc-num';
    case 'keyword':
      return 'json-doc-kw';
    case 'punct':
      return 'json-doc-punct';
    default:
      return 'json-doc-ws';
  }
}

type Props = {
  value: unknown;
  /** aria-label for the document region */
  label?: string;
};

/**
 * Renders pretty-printed JSON with soft “markdown code fence” styling and token colors.
 */
export function JsonDocumentView({ value, label = 'JSON payload' }: Props) {
  const tokens = useMemo(() => {
    let formatted: string;
    try {
      formatted = JSON.stringify(value ?? null, null, 2);
    } catch {
      formatted = String(value);
    }
    return tokenizeHighlightedJson(formatted);
  }, [value]);

  return (
    <pre className="json-doc-panel" tabIndex={0} role="region" aria-label={label}>
      <code className="json-doc-code">
        {tokens.map((t, idx) =>
          t.kind === 'ws' ? (
            <Fragment key={idx}>{t.text}</Fragment>
          ) : (
            <span key={idx} className={classForToken(t.kind)}>
              {t.text}
            </span>
          )
        )}
      </code>
    </pre>
  );
}

export function stringifyPayload(value: unknown): string {
  try {
    return JSON.stringify(value ?? null, null, 2);
  } catch {
    return String(value);
  }
}
