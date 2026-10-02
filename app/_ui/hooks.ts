'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, getJson, parseKey, postJson, refKey, type Kind, type List, type Ref, type Tracked } from './api';

/** How often a visible page re-reads live numbers while the exchange is trading. */
const LIVE_MS = 30_000;

export type Resource<T> = {
  data: T | null;
  /** The path `data` came from. While a new path loads, the old data stays on screen under its old path. */
  dataPath: string | null;
  error: string | null;
  loading: boolean;
  refresh: () => void;
};

/**
 * One GET endpoint as state. It loads when the path changes, again whenever the tab
 * becomes visible, and every LIVE_MS while `live` is true — but never in a hidden tab.
 */
export function useResource<T>(path: string | null, live = false): Resource<T> {
  const [state, setState] = useState<{ data: T | null; dataPath: string | null; error: string | null }>({
    data: null,
    dataPath: null,
    error: null,
  });
  // Which path the screen wants now, so a slow answer for the previous one is dropped.
  const wanted = useRef(path);
  useEffect(() => {
    wanted.current = path;
  }, [path]);

  const load = useCallback(() => {
    if (path === null) return;
    getJson<T>(path).then(
      (data) => {
        if (wanted.current === path) setState({ data, dataPath: path, error: null });
      },
      (e: Error) => {
        if (wanted.current === path) setState((s) => ({ ...s, error: e.message }));
      },
    );
  }, [path]);

  useEffect(() => {
    load();
    const onVisible = () => {
      if (!document.hidden) load();
    };
    document.addEventListener('visibilitychange', onVisible);
    const timer = live ? setInterval(() => !document.hidden && load(), LIVE_MS) : null;
    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      if (timer) clearInterval(timer);
    };
  }, [load, live]);

  // Loading is what follows from the rest: a path is wanted and its answer is not here yet.
  return { ...state, loading: path !== null && state.dataPath !== path && state.error === null, refresh: load };
}

/** "up" / "down" for about a second after `value` changes — the flash of a quote board. */
export function useFlash(value: number | null | undefined): 'up' | 'down' | null {
  const previous = useRef(value);
  const [dir, setDir] = useState<'up' | 'down' | null>(null);
  useEffect(() => {
    const before = previous.current;
    previous.current = value;
    if (value == null || before == null || value === before) return;
    setDir(value > before ? 'up' : 'down');
    const t = setTimeout(() => setDir(null), 1100);
    return () => clearTimeout(t);
  }, [value]);
  return dir;
}

export type Selection = Ref & { name: string };

/**
 * Which paper the middle pane shows. It lives in the address bar (`?p=s629014`), so the
 * back button walks through what was looked at and a link opens on the same paper.
 */
export function useSelection(fallback: Selection): [Selection, (s: Selection) => void] {
  const [sel, setSel] = useState<Selection>(fallback);

  useEffect(() => {
    const read = () => {
      const ref = parseKey(new URLSearchParams(window.location.search).get('p'));
      const name = (window.history.state as { name?: string } | null)?.name;
      setSel(ref ? { ...ref, name: name ?? '' } : fallback);
    };
    read();
    window.addEventListener('popstate', read);
    return () => window.removeEventListener('popstate', read);
    // The fallback is a constant of the page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = useCallback((s: Selection) => {
    const url = new URL(window.location.href);
    url.searchParams.set('p', refKey(s));
    url.searchParams.delete('signin');
    window.history.pushState({ name: s.name }, '', url);
    setSel(s);
  }, []);

  return [sel, select];
}

export type ListState = {
  /** null until the server has said whether this browser is signed in. */
  signedIn: boolean | null;
  configured: boolean;
  items: Tracked[];
  error: string | null;
  find: (kind: Kind, id: string) => Tracked | undefined;
  follow: (item: Selection & { symbol?: string | null; type?: string | null; companyId?: string | null }) => Promise<void>;
  unfollow: (ref: Ref) => Promise<void>;
  hold: (item: Selection & { symbol?: string | null; type?: string | null; companyId?: string | null }, qty: number, avgCost: number | null) => Promise<void>;
  clearHolding: (ref: Ref) => Promise<void>;
  signIn: (code: string) => Promise<string | null>;
  signOut: () => Promise<void>;
};

/** The owner's list (shared with the phone) and whether this browser may see it. */
export function useList(): ListState {
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [configured, setConfigured] = useState(true);
  const [items, setItems] = useState<Tracked[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadList = useCallback(async () => {
    try {
      const list = await getJson<List>('/api/list');
      setItems(list.items);
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setSignedIn(false);
      else setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    let alive = true;
    getJson<{ signedIn: boolean; configured: boolean }>('/api/session').then(
      (s) => {
        if (!alive) return;
        setSignedIn(s.signedIn);
        setConfigured(s.configured);
        if (s.signedIn) loadList();
      },
      (e: Error) => alive && setError(e.message),
    );
    return () => {
      alive = false;
    };
  }, [loadList]);

  // The phone can change the list too; pick that up without a reload.
  useEffect(() => {
    if (!signedIn) return;
    const tick = () => !document.hidden && loadList();
    const timer = setInterval(tick, LIVE_MS);
    document.addEventListener('visibilitychange', tick);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [signedIn, loadList]);

  const run = useCallback(async (op: unknown) => {
    try {
      const list = await postJson<List>('/api/list', op);
      setItems(list.items);
      setError(null);
    } catch (e) {
      setError(`השינוי לא נשמר — ${(e as Error).message}`);
      throw e;
    }
  }, []);

  const asItem = (s: Selection & { symbol?: string | null; type?: string | null; companyId?: string | null }) => ({
    kind: s.kind,
    id: s.id,
    name: s.name,
    symbol: s.symbol ?? null,
    type: s.type ?? null,
    companyId: s.companyId ?? null,
  });

  return {
    signedIn,
    configured,
    items,
    error,
    find: (kind, id) => items.find((x) => x.kind === kind && x.id === id),
    follow: (item) => run({ op: 'follow', item: asItem(item) }),
    unfollow: (ref) => run({ op: 'unfollow', kind: ref.kind, id: ref.id }),
    hold: (item, qty, avgCost) => run({ op: 'hold', item: asItem(item), qty, avgCost }),
    clearHolding: (ref) => run({ op: 'clearHolding', kind: ref.kind, id: ref.id }),
    signIn: async (code) => {
      try {
        await postJson('/api/session', { code });
      } catch (e) {
        return e instanceof ApiError && e.status === 401 ? 'הקוד שגוי' : (e as Error).message;
      }
      setSignedIn(true);
      await loadList();
      return null;
    },
    signOut: async () => {
      await getJson('/api/session', { method: 'DELETE' });
      setSignedIn(false);
      setItems([]);
    },
  };
}
