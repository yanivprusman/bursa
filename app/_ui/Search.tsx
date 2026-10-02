'use client';

import { useEffect, useRef, useState } from 'react';
import { getJson, refKey, type Hit } from './api';
import type { ListState, Selection } from './hooks';
import { Logo } from './parts';

/**
 * Search by Hebrew or English name, symbol, security number or ISIN. Results drop under
 * the field; arrows move, Enter opens, Escape closes. "/" focuses the field from anywhere.
 */
export function Search({ onPick, list, heldIds }: { onPick: (s: Selection) => void; list: ListState; heldIds: Set<string> }) {
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<Hit[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [cursor, setCursor] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const wrap = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const q = query.trim();
    if (!q) return;
    let alive = true;
    // Wait for a pause in the typing before asking the server.
    const t = setTimeout(() => {
      getJson<{ hits: Hit[] }>(`/api/search?q=${encodeURIComponent(q)}`).then(
        (r) => {
          if (!alive) return;
          setHits(r.hits);
          setCursor(0);
          setError(null);
        },
        (e: Error) => alive && setError(e.message),
      );
    }, 180);
    return () => {
      alive = false;
      clearTimeout(t);
    };
  }, [query]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target as HTMLElement | null)?.tagName ?? '');
      if (e.key === '/' && !typing) {
        e.preventDefault();
        input.current?.focus();
      }
    };
    const onDown = (e: PointerEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('pointerdown', onDown);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('pointerdown', onDown);
    };
  }, []);

  const pick = (h: Hit) => {
    onPick({ kind: h.kind, id: h.id, name: h.name });
    setOpen(false);
    setQuery('');
    setHits(null);
    input.current?.blur();
  };

  const showing = open && query.trim() !== '';

  return (
    <div className="search" ref={wrap}>
      <svg viewBox="0 0 24 24" className="glass" aria-hidden>
        <circle cx="11" cy="11" r="6.5" />
        <path d="M16 16l4.5 4.5" />
      </svg>
      <input
        ref={input}
        data-id="search-field"
        type="search"
        value={query}
        placeholder="חיפוש מניה, קרן סל, אג&quot;ח או מדד"
        aria-label="חיפוש נייר"
        autoComplete="off"
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          // An emptied field has no results; say so here rather than wait for the next answer.
          if (!e.target.value.trim()) {
            setHits(null);
            setError(null);
          }
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setOpen(false);
            input.current?.blur();
          } else if (hits?.length && e.key === 'ArrowDown') {
            e.preventDefault();
            setCursor((c) => Math.min(hits.length - 1, c + 1));
          } else if (hits?.length && e.key === 'ArrowUp') {
            e.preventDefault();
            setCursor((c) => Math.max(0, c - 1));
          } else if (hits?.length && e.key === 'Enter') {
            pick(hits[cursor]);
          }
        }}
      />
      <kbd aria-hidden>/</kbd>
      {showing && (
        <div className="results" role="listbox">
          {error && <div className="results-note">{error}</div>}
          {!error && hits === null && <div className="results-note">מחפש…</div>}
          {!error && hits?.length === 0 && <div className="results-note">לא נמצא נייר שמתאים ל&quot;{query.trim()}&quot;.</div>}
          {hits?.map((h, i) => {
            const mine = list.find(h.kind, h.id);
            return (
              <div key={refKey(h)} className={`result ${i === cursor ? 'cursor' : ''}`} role="option" aria-selected={i === cursor} onPointerEnter={() => setCursor(i)}>
                <button type="button" className="result-main" data-id={`result-${refKey(h)}`} onClick={() => pick(h)}>
                  <Logo kind={h.kind} companyId={h.companyId} size={32} />
                  <span className="grow">
                    <span className="name">{h.name}</span>
                    <span className="sub">
                      {[h.type, h.symbol && h.symbol !== h.name ? h.symbol : null].filter(Boolean).join(' · ')}
                      {' · '}
                      <span className="num">{h.id}</span>
                    </span>
                  </span>
                </button>
                {list.signedIn &&
                  (h.kind === 'security' && heldIds.has(h.id) ? (
                    <span className="in-portfolio">בתיק</span>
                  ) : (
                    <button
                      type="button"
                      className={`round ${mine ? 'on' : ''}`}
                      data-id={`${mine ? 'unfollow' : 'follow'}-${refKey(h)}`}
                      aria-label={mine ? 'הסרה מהמעקב' : 'הוספה למעקב'}
                      title={mine ? 'הסרה מהמעקב' : 'הוספה למעקב'}
                      onClick={() => (mine ? list.unfollow(h) : list.follow({ ...h })).catch(() => undefined)}
                    >
                      {mine ? '✓' : '+'}
                    </button>
                  ))}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
