'use client';

import { useMemo, useState } from 'react';
import { refKey, type Overview, type Quote, type Quotes, type Tracked } from './api';
import { useList, useResource, useSelection, type Selection } from './hooks';
import * as F from './format';
import { Detail } from './Detail';
import { Market } from './Market';
import { HoldingDialog, Mine } from './Mine';
import { Search } from './Search';
import { Num, Status, tone } from './parts';

/** What the middle pane shows before anything is chosen: the exchange's flagship index. */
const START: Selection = { kind: 'index', id: '142', name: 'ת"א-35' };

type Editable = Tracked | (Selection & { symbol?: string | null; type?: string | null; companyId?: string | null });

/**
 * The desktop app: one screen, three panes. The owner's list on the right, the chosen
 * paper in the middle, the market on the left — and the ticker running across the top.
 */
export function Workspace() {
  const [sel, select] = useSelection(START);
  const list = useList();
  const market = useResource<Overview>('/api/market', true);
  const open = market.data?.open ?? false;

  const ids = list.items.map(refKey).join(',');
  const quotesRes = useResource<Quotes>(list.signedIn && ids ? `/api/quotes?ids=${ids}` : null, open);
  // Quotes are kept by key across list changes, so following a new paper does not blank the rest.
  const [seen, setSeen] = useState(new Map<string, Quote>());
  const fresh = quotesRes.data?.quotes;
  if (fresh && fresh.some((q) => seen.get(refKey(q)) !== q)) setSeen(new Map([...seen, ...fresh.map((q) => [refKey(q), q] as const)]));
  const missing = useMemo(() => new Map((quotesRes.data?.missing ?? []).map((m) => [refKey(m), m.error])), [quotesRes.data]);

  const [editing, setEditing] = useState<Editable | null>(null);
  // The name comes with the click; after a reload only the id is in the address bar.
  const known = sel.name || list.find(sel.kind, sel.id)?.name || '';
  const selected = { ...sel, name: known };

  return (
    <div className="app">
      <header className="top">
        <button type="button" className="brand" data-id="home" onClick={() => select(START)}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/icon.svg" alt="" />
          בורסה
        </button>
        <Search onPick={select} list={list} />
        {market.data && <Status open={market.data.open} tradeDate={market.data.tradeDate} tradeTime={market.data.tradeTime} />}
      </header>

      {market.data && market.data.tape.length > 0 && (
        <div className="tape" dir="ltr" aria-label="מדדים">
          {/* Two copies end to end, moved by exactly one copy's width, so the loop has no seam. */}
          <div className="tape-track">
            {[0, 1].map((copy) => (
              <div className="tape-set" key={copy} aria-hidden={copy === 1}>
                {market.data!.tape.map((t) => (
                  <button type="button" key={t.id} data-id={`tape-${t.id}`} tabIndex={copy === 1 ? -1 : 0} onClick={() => select({ kind: 'index', id: t.id, name: t.name })}>
                    <bdi>{t.name}</bdi>
                    <Num className="strong">{F.fixed(t.last, 2)}</Num>
                    <Num className={tone(t.changePct)}>{t.changePct == null ? '' : `${t.changePct > 0 ? '▲' : t.changePct < 0 ? '▼' : ''}${F.fixed(Math.abs(t.changePct), 2)}%`}</Num>
                  </button>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      <main className="panes">
        <aside className="pane mine" aria-label="הרשימה שלי">
          <Mine list={list} quotes={seen} missing={missing} quotesError={quotesRes.error} market={market.data} selected={selected} onPick={select} onEditHolding={setEditing} />
        </aside>
        <section className="pane detail" aria-label="פרטי הנייר">
          <Detail key={refKey(sel)} sel={selected} live={open} list={list} onPick={select} onEditHolding={setEditing} />
        </section>
        <aside className="pane market" aria-label="שוק">
          <Market market={market} selected={selected} onPick={select} />
          <p className="source">הנתונים מאתר הבורסה לניירות ערך בתל אביב. מחירי ניירות הערך באגורות, המדדים בנקודות.</p>
        </aside>
      </main>

      {editing && <HoldingDialog key={refKey(editing)} item={editing} lastPrice={seen.get(refKey(editing))?.last ?? null} list={list} onClose={() => setEditing(null)} />}
    </div>
  );
}
