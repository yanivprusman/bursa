'use client';

import { useMemo, useState } from 'react';
import { refKey, type Overview, type Paper, type Quote, type Quotes, type Side } from './api';
import { useAccount, useList, useResource, useSelection, type Selection } from './hooks';
import * as F from './format';
import { Detail } from './Detail';
import { Market } from './Market';
import { Mine } from './Mine';
import { Search } from './Search';
import { TradeDialog } from './Trade';
import { Num, Status, tone } from './parts';

/** What the middle pane shows before anything is chosen: the exchange's flagship index. */
const START: Selection = { kind: 'index', id: '142', name: 'ת"א-35' };

type Ticket = { paper: Paper; side: Side; last: number; liveNow: boolean };

/**
 * The desktop app: one screen, three panes. The owner's list on the right, the chosen
 * paper in the middle, the market on the left — and the ticker running across the top.
 */
export function Workspace() {
  const [sel, select] = useSelection(START);
  const list = useList();
  const account = useAccount(list.signedIn);
  const market = useResource<Overview>('/api/market', true);
  const open = market.data?.open ?? false;

  // Everything the right pane prices: what is followed, held, or waiting to be bought or sold.
  const ids = [...new Set([...list.items.map(refKey), ...(account.data?.positions ?? []).map((p) => `s${p.paper.id}`), ...(account.data?.pending ?? []).map((o) => `s${o.paper.id}`)])].join(',');
  const quotesRes = useResource<Quotes>(list.signedIn && ids ? `/api/quotes?ids=${ids}` : null, open);
  // Quotes are kept by key across list changes, so following a new paper does not blank the rest.
  const [seen, setSeen] = useState(new Map<string, Quote>());
  const fresh = quotesRes.data?.quotes;
  if (fresh && fresh.some((q) => seen.get(refKey(q)) !== q)) setSeen(new Map([...seen, ...fresh.map((q) => [refKey(q), q] as const)]));
  const missing = useMemo(() => new Map((quotesRes.data?.missing ?? []).map((m) => [refKey(m), m.error])), [quotesRes.data]);

  const [ticket, setTicket] = useState<Ticket | null>(null);
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
        <Search onPick={select} list={list} heldIds={new Set((account.data?.positions ?? []).map((p) => p.paper.id))} />
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
          <Mine list={list} account={account} quotes={seen} missing={missing} quotesError={quotesRes.error} market={market.data} selected={selected} onPick={select} />
        </aside>
        <section className="pane detail" aria-label="פרטי הנייר">
          <Detail key={refKey(sel)} sel={selected} live={open} list={list} account={account} onPick={select} onTrade={setTicket} />
        </section>
        <aside className="pane market" aria-label="שוק">
          <Market market={market} selected={selected} onPick={select} />
          <p className="source">הנתונים מאתר הבורסה לניירות ערך בתל אביב. מחירי ניירות הערך באגורות, המדדים בנקודות.</p>
        </aside>
      </main>

      {ticket && <TradeDialog key={`${ticket.paper.id}-${ticket.side}`} {...ticket} account={account} onClose={() => setTicket(null)} />}
    </div>
  );
}
