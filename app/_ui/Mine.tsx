'use client';

import { useState, type ReactNode } from 'react';
import { refKey, type Order, type Overview, type Position, type Quote, type Summary, type Trade } from './api';
import type { AccountState, ListState, Selection } from './hooks';
import * as F from './format';
import { Chip, Logo, Num, SkeletonRows, tone } from './parts';
import { holdingValue, totals, type HoldingValue } from './portfolio';

const sKey = (id: string) => `s${id}`;

/** One colour per holding in the "what the portfolio is made of" bar; the tail shares the last. */
const SLICES = ['#f2b84b', '#6fa8ff', '#3dd68c', '#e58ccb', '#9b8cff', '#7c8aa3'];

/** Below this many papers the pane still has room, and uses it to suggest a few more. */
const SUGGEST_BELOW = 4;

type Props = {
  list: ListState;
  account: AccountState;
  quotes: Map<string, Quote>;
  missing: Map<string, string>;
  quotesError: string | null;
  market: Overview | null;
  selected: Selection;
  onPick: (s: Selection) => void;
};

/** The owner's side of the workspace: the practice account, the holdings, waiting orders, the watchlist. */
export function Mine({ list, account, quotes, missing, quotesError, market, selected, onPick }: Props) {
  if (list.signedIn === null) {
    return (
      <>
        <h2 className="pane-title">שלי</h2>
        {list.error ? <div className="note bad">{list.error}</div> : <SkeletonRows n={5} />}
      </>
    );
  }
  if (!list.signedIn) return <SignIn list={list} />;

  const acc = account.data;
  const holdings = acc?.positions ?? [];
  const heldIds = new Set(holdings.map((p) => p.paper.id));
  const watching = list.items.filter((x) => !(x.kind === 'security' && heldIds.has(x.id)));
  // A holding counts toward the total only once its price is known.
  const valued = holdings.flatMap((p) => {
    const q = quotes.get(sKey(p.paper.id));
    return q ? [{ p, v: holdingValue(p.qty, p.avgCost, q.last, q.base) }] : [];
  });
  const isSel = (kind: string, id: string) => selected.kind === kind && selected.id === id;

  const suggestions = market
    ? [
        ...market.indices.slice(0, 3).map((i) => ({ kind: 'index' as const, id: i.id, name: i.name, type: 'מדד', companyId: null, last: i.last, changePct: i.changePct, unit: 'points' })),
        ...market.movers.active.slice(0, 5).map((m) => ({ kind: 'security' as const, id: m.id, name: m.name, type: 'מניות', companyId: m.companyId, last: m.last, changePct: m.changePct, unit: 'agorot' })),
      ]
        .filter((s) => !list.find(s.kind, s.id) && !(s.kind === 'security' && heldIds.has(s.id)))
        .slice(0, 6)
    : [];

  return (
    <>
      {(list.error || account.error || quotesError) && <div className="note bad">{list.error ?? account.error ?? `${quotesError} — מוצגים המחירים האחרונים`}</div>}

      {acc ? <AccountCard acc={acc} valued={valued} unpriced={holdings.length - valued.length} /> : !account.error && <SkeletonRows n={2} />}

      {acc && holdings.length === 0 && acc.pending.length === 0 && (
        <p className="pane-note first-steps">
          יש לכם <Num>{F.shekels(acc.cash / 100)}</Num> מדומים לתרגול. פתחו נייר — מניה, קרן סל או אג&quot;ח — ולחצו &quot;קנייה&quot;. הפקודה מתבצעת בשער האמיתי של הבורסה.
        </p>
      )}

      {holdings.length > 0 && (
        <>
          <h2 className="pane-title spaced">התיק שלי</h2>
          <div className="rows">
            {holdings.map((p) => {
              const q = quotes.get(sKey(p.paper.id));
              const v = q ? holdingValue(p.qty, p.avgCost, q.last, q.base) : null;
              return (
                <PaperRow key={p.paper.id} kind="security" id={p.paper.id} name={p.paper.name} sel={isSel('security', p.paper.id)} companyId={p.paper.companyId} onPick={onPick}>
                  <span className="grow">
                    <span className="name">{p.paper.name}</span>
                    {q ? (
                      <span className="sub">
                        <Num>
                          {F.trimmed(p.qty, 0)} × {F.trimmed(q.last)}
                        </Num>{' '}
                        <Num className={tone(v?.gain)}>{v?.gainPct == null ? '' : F.pct(v.gainPct)}</Num>
                      </span>
                    ) : (
                      <span className="sub">{missing.get(sKey(p.paper.id)) ?? 'ממתין למחיר…'}</span>
                    )}
                  </span>
                  {v && (
                    <span className="end">
                      <Num flashOn={v.value} className="strong">
                        {F.shekels(v.value)}
                      </Num>
                      {v.dayChange != null && <Num className={`small ${tone(v.dayChange)}`}>{F.signedShekels(v.dayChange)}</Num>}
                    </span>
                  )}
                </PaperRow>
              );
            })}
          </div>
        </>
      )}

      {acc && acc.pending.length > 0 && (
        <>
          <h2 className="pane-title spaced">פקודות ממתינות</h2>
          <p className="pane-note">יבוצעו בשער הפתיחה של יום המסחר הבא</p>
          <div className="rows">
            {acc.pending.map((o) => (
              <PendingRow key={o.id} o={o} sel={isSel('security', o.paper.id)} onPick={onPick} onCancel={() => account.cancel(o.id).catch(() => undefined)} />
            ))}
          </div>
        </>
      )}

      {watching.length > 0 && (
        <>
          <h2 className="pane-title spaced">במעקב</h2>
          <div className="rows">
            {watching.map((t) => {
              const q = quotes.get(refKey(t));
              return (
                <PaperRow
                  key={refKey(t)}
                  kind={t.kind}
                  id={t.id}
                  name={t.name}
                  sel={isSel(t.kind, t.id)}
                  companyId={t.companyId ?? q?.companyId}
                  onPick={onPick}
                  actions={
                    <RowAction id={`unfollow-${refKey(t)}`} label="הסרה מהמעקב" onClick={() => list.unfollow(t).catch(() => undefined)}>
                      ×
                    </RowAction>
                  }
                >
                  <span className="grow">
                    <span className="name">{t.name}</span>
                    <span className="sub">{q ? t.type : (missing.get(refKey(t)) ?? 'ממתין למחיר…')}</span>
                  </span>
                  {q && (
                    <>
                      <Num flashOn={q.last} className="strong">
                        {F.price(q.last, q.unit)}
                      </Num>
                      <Chip pct={q.changePct} />
                    </>
                  )}
                </PaperRow>
              );
            })}
          </div>
        </>
      )}

      {list.items.length < SUGGEST_BELOW && suggestions.length > 0 && (
        <>
          <h2 className="pane-title spaced">{list.items.length === 0 ? 'התחילו מכאן' : 'אולי גם אלה'}</h2>
          <p className="pane-note">המדדים המובילים והמניות הנסחרות ביותר היום</p>
          <div className="rows">
            {suggestions.map((s) => (
              <div className="row" key={refKey(s)}>
                <button type="button" className="row-main" data-id={`suggest-open-${refKey(s)}`} onClick={() => onPick(s)}>
                  <Logo kind={s.kind} companyId={s.companyId} />
                  <span className="grow">
                    <span className="name">{s.name}</span>
                    <span className="sub">
                      <Num>{F.price(s.last, s.unit)}</Num> <Num className={tone(s.changePct)}>{s.changePct == null ? '' : F.pct(s.changePct)}</Num>
                    </span>
                  </span>
                </button>
                <button type="button" className="round" data-id={`suggest-${refKey(s)}`} aria-label="הוספה למעקב" title="הוספה למעקב" onClick={() => list.follow(s).catch(() => undefined)}>
                  +
                </button>
              </div>
            ))}
          </div>
        </>
      )}

      {acc && (acc.trades.length > 0 || acc.orders.length > 0) && <Activity acc={acc} onPick={onPick} />}

      <div className="pane-foot">
        <button type="button" className="link" data-id="sign-out" onClick={() => list.signOut()}>
          יציאה
        </button>
        {acc && <StartOver acc={acc} account={account} />}
      </div>
    </>
  );
}

function RowAction({ id, label, onClick, children }: { id: string; label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" className="row-action" data-id={id} aria-label={label} title={label} onClick={onClick}>
      {children}
    </button>
  );
}

/** A list row: the main part opens the paper, the small buttons after it act on the list. */
function PaperRow({ kind, id, name, sel, companyId, onPick, actions, children }: { kind: 'security' | 'index'; id: string; name: string; sel: boolean; companyId: string | null | undefined; onPick: (s: Selection) => void; actions?: ReactNode; children: ReactNode }) {
  const key = (kind === 'index' ? 'i' : 's') + id;
  return (
    <div className={`row ${actions ? 'has-actions' : ''} ${sel ? 'sel' : ''}`}>
      <button type="button" className="row-main" data-id={`mine-${key}`} onClick={() => onPick({ kind, id, name })}>
        <Logo kind={kind} companyId={companyId} />
        {children}
      </button>
      {actions && <span className="row-actions">{actions}</span>}
    </div>
  );
}

function PendingRow({ o, sel, onPick, onCancel }: { o: Order; sel: boolean; onPick: (s: Selection) => void; onCancel: () => void }) {
  return (
    <PaperRow
      kind="security"
      id={o.paper.id}
      name={o.paper.name}
      sel={sel}
      companyId={o.paper.companyId}
      onPick={onPick}
      actions={
        <RowAction id={`cancel-order-${o.id}`} label="ביטול הפקודה" onClick={onCancel}>
          ×
        </RowAction>
      }
    >
      <span className="grow">
        <span className="name">{o.paper.name}</span>
        <span className="sub">
          <span className={`side ${o.side}`}>{o.side === 'buy' ? 'קנייה' : 'מכירה'}</span> <Num>{F.trimmed(o.qty, 0)}</Num> · בפתיחה, מ-<Num>{F.dayMonth(o.fillFrom)}</Num>
        </span>
      </span>
      {o.side === 'buy' && (
        <span className="end">
          <Num className="small muted">≈{F.shekels(o.reserve / 100)}</Num>
        </span>
      )}
    </PaperRow>
  );
}

/** The broker's statement: trades and the orders that did not fill, newest first. */
function Activity({ acc, onPick }: { acc: Summary; onPick: (s: Selection) => void }) {
  const [all, setAll] = useState(false);
  type Line = { at: string; trade?: Trade; order?: Order };
  const lines: Line[] = [
    ...acc.trades.map((t) => ({ at: t.at, trade: t })),
    ...acc.orders.filter((o) => o.status !== 'filled').map((o) => ({ at: o.closedAt ?? o.placedAt, order: o })),
  ].sort((a, b) => b.at.localeCompare(a.at));
  const shown = all ? lines : lines.slice(0, 6);
  return (
    <>
      <h2 className="pane-title spaced">פעולות אחרונות</h2>
      <div className="rows activity">
        {shown.map((l) => {
          const paper = (l.trade ?? l.order)!.paper;
          const side = (l.trade ?? l.order)!.side;
          return (
            <button type="button" key={l.trade?.id ?? l.order!.id} className="row-main act" data-id={`activity-${l.trade?.id ?? l.order!.id}`} onClick={() => onPick({ kind: 'security', id: paper.id, name: paper.name })}>
              <span className="grow">
                <span className="name">
                  <span className={`side ${side}`}>{side === 'buy' ? 'קנייה' : 'מכירה'}</span> {paper.name}
                </span>
                <span className="sub">
                  {l.trade ? (
                    <>
                      <Num>
                        {F.trimmed(l.trade.qty, 0)} × {F.trimmed(l.trade.price)}
                      </Num>
                      {l.trade.how === 'open' ? ' · שער פתיחה' : ''} · <Num>{stamp(l.at)}</Num>
                    </>
                  ) : (
                    <>
                      {l.order!.status === 'cancelled' ? 'בוטלה' : `נדחתה — ${l.order!.reason}`} · <Num>{stamp(l.at)}</Num>
                    </>
                  )}
                </span>
              </span>
              {l.trade && (
                <Num className={`small ${l.trade.side === 'buy' ? '' : 'up'}`}>
                  {l.trade.side === 'buy' ? '-' : '+'}
                  {F.shekels((l.trade.side === 'buy' ? l.trade.gross + l.trade.fee : l.trade.gross - l.trade.fee) / 100)}
                </Num>
              )}
            </button>
          );
        })}
      </div>
      {lines.length > 6 && (
        <button type="button" className="link more" data-id="activity-all" onClick={() => setAll(!all)}>
          {all ? 'פחות' : `כל ${lines.length} הפעולות`}
        </button>
      )}
    </>
  );
}

/** "2.10 14:27" in Israel time. */
function stamp(iso: string): string {
  const d = new Date(iso);
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Jerusalem', day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(d);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${get('day')}.${get('month')} ${get('hour')}:${get('minute')}`;
}

/** Back to the starting cash. The old account is kept on the server, but it is still a big step — so it asks. */
function StartOver({ acc, account }: { acc: Summary; account: AccountState }) {
  const [asking, setAsking] = useState(false);
  if (acc.trades.length === 0 && acc.pending.length === 0) return null;
  return asking ? (
    <span className="start-over">
      <span className="muted">למחוק את כל העסקאות ולהתחיל שוב מ-{F.shekels(acc.startCash / 100)}?</span>
      <button type="button" className="btn danger" data-id="start-over-confirm" onClick={() => account.reset().then(() => setAsking(false), () => setAsking(false))}>
        כן, מההתחלה
      </button>
      <button type="button" className="btn" data-id="start-over-cancel" onClick={() => setAsking(false)}>
        לא
      </button>
    </span>
  ) : (
    <button type="button" className="link" data-id="start-over" onClick={() => setAsking(true)}>
      התחלה מחדש
    </button>
  );
}

/** The number the owner opened the app for: what the account is worth, and what the market did to it. */
function AccountCard({ acc, valued, unpriced }: { acc: Summary; valued: Array<{ p: Position; v: HoldingValue }>; unpriced: number }) {
  const t = totals(valued.map((x) => x.v));
  const cash = acc.cash / 100;
  const worth = cash + t.value;
  const start = acc.startCash / 100;
  const sinceStart = worth - start;
  const slices = [...valued].sort((a, b) => b.v.value - a.v.value);
  const named = slices.slice(0, SLICES.length - 2);
  const rest = slices.slice(SLICES.length - 2);
  const restValue = rest.reduce((s, x) => s + x.v.value, 0);
  return (
    <div className="hero" data-id="account-total">
      <div className="hero-top">
        <span className="hero-label">שווי החשבון</span>
        <span className="practice-tag on-hero">תרגול</span>
      </div>
      <Num flashOn={worth} className="hero-num">
        {F.shekels(worth)}
      </Num>
      <div className="hero-line">
        <span>מאז ההתחלה</span>
        <Num className={`strong ${tone(sinceStart)}`}>{F.signedShekels(sinceStart)}</Num>
        <Num className={tone(sinceStart)}>({F.pct((sinceStart / start) * 100)})</Num>
      </div>
      {valued.length > 0 && (
        <div className="hero-line">
          <span>היום</span>
          <Num className={`strong ${tone(t.dayChange)}`}>{F.signedShekels(t.dayChange)}</Num>
          {t.dayChangePct != null && <Num className={tone(t.dayChange)}>({F.pct(t.dayChangePct)})</Num>}
        </div>
      )}
      <div className="hero-line">
        <span>מזומן פנוי</span>
        <Num className="strong">{F.shekels(acc.available / 100)}</Num>
      </div>
      {acc.available !== acc.cash && (
        <p className="hero-aside">
          ועוד <Num>{F.shekels((acc.cash - acc.available) / 100)}</Num> שמורים לפקודות ממתינות
        </p>
      )}
      {/* What the account is made of — cash is a slice too. */}
      {slices.length > 0 && worth > 0 && (
        <>
          <div className="slices">
            {slices.map((s, i) => (s.v.value > 0 ? <span key={s.p.paper.id} style={{ flexGrow: s.v.value, background: SLICES[Math.min(i, SLICES.length - 2)] }} /> : null))}
            {cash > 0 && <span style={{ flexGrow: cash, background: SLICES[SLICES.length - 1] }} />}
          </div>
          {named.map((s, i) => (
            <div className="slice-row" key={s.p.paper.id}>
              <i style={{ background: SLICES[i] }} />
              <span className="grow">{s.p.paper.name}</span>
              <Num>{F.fixed((s.v.value / worth) * 100, 1)}%</Num>
            </div>
          ))}
          {rest.length > 0 && (
            <div className="slice-row">
              <i style={{ background: SLICES[SLICES.length - 2] }} />
              <span className="grow">עוד {rest.length}</span>
              <Num>{F.fixed((restValue / worth) * 100, 1)}%</Num>
            </div>
          )}
          <div className="slice-row">
            <i style={{ background: SLICES[SLICES.length - 1] }} />
            <span className="grow">מזומן</span>
            <Num>{F.fixed((cash / worth) * 100, 1)}%</Num>
          </div>
        </>
      )}
      {unpriced > 0 && <p className="hero-note">{unpriced} החזקות בלי מחיר עדכני אינן בסכום</p>}
    </div>
  );
}

/** The list is private; a browser shows it after one sign-in with the access code. */
function SignIn({ list }: { list: ListState }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const expired = typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('signin') === 'expired';
  return (
    <>
      <h2 className="pane-title">שלי</h2>
      <form
        className="signin"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError(await list.signIn(code));
          setBusy(false);
        }}
      >
        <p>חשבון המסחר ורשימת המעקב שלך פרטיים, ומשותפים לטלפון ולמחשב. כדי לראות אותם כאן נכנסים פעם אחת עם קוד הגישה.</p>
        {!list.configured && <div className="note bad">בשרת לא הוגדר קוד גישה (BURSA_API_TOKEN).</div>}
        {expired && !error && <div className="note bad">קישור הכניסה פג. אפשר להפיק חדש או להזין את הקוד.</div>}
        <input data-id="access-code" type="password" value={code} placeholder="קוד גישה" aria-label="קוד גישה" autoComplete="current-password" onChange={(e) => setCode(e.target.value)} />
        {error && <div className="note bad">{error}</div>}
        <button type="submit" className="btn primary" data-id="sign-in" disabled={busy || !code.trim()}>
          כניסה
        </button>
      </form>
    </>
  );
}
