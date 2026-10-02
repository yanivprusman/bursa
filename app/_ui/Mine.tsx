'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { refKey, type Overview, type Quote, type Tracked } from './api';
import type { ListState, Selection } from './hooks';
import * as F from './format';
import { Chip, Logo, Num, SkeletonRows, tone } from './parts';
import { holdingValue, totals, type HoldingValue } from './portfolio';

/** One colour per holding in the "what the portfolio is made of" bar; the tail shares the last. */
const SLICES = ['#f2b84b', '#6fa8ff', '#3dd68c', '#e58ccb', '#9b8cff', '#7c8aa3'];

/** Below this many papers the pane still has room, and uses it to suggest a few more. */
const SUGGEST_BELOW = 4;

type Props = {
  list: ListState;
  quotes: Map<string, Quote>;
  missing: Map<string, string>;
  quotesError: string | null;
  market: Overview | null;
  selected: Selection;
  onPick: (s: Selection) => void;
  onEditHolding: (t: Tracked) => void;
};

/** The owner's side of the workspace: what the portfolio is worth, the holdings, the watchlist. */
export function Mine({ list, quotes, missing, quotesError, market, selected, onPick, onEditHolding }: Props) {
  if (list.signedIn === null) {
    return (
      <>
        <h2 className="pane-title">שלי</h2>
        {list.error ? <div className="note bad">{list.error}</div> : <SkeletonRows n={5} />}
      </>
    );
  }
  if (!list.signedIn) return <SignIn list={list} />;

  const holdings = list.items.filter((x) => (x.qty ?? 0) > 0);
  const watching = list.items.filter((x) => !((x.qty ?? 0) > 0));
  // A holding counts toward the total only once its price is known.
  const valued = holdings.flatMap((t) => {
    const q = quotes.get(refKey(t));
    return q ? [{ t, v: holdingValue(t.qty ?? 0, t.avgCost, q.last, q.base) }] : [];
  });
  const isSel = (t: Tracked) => selected.kind === t.kind && selected.id === t.id;

  const suggestions = market
    ? [
        ...market.indices.slice(0, 3).map((i) => ({ kind: 'index' as const, id: i.id, name: i.name, type: 'מדד', companyId: null, last: i.last, changePct: i.changePct, unit: 'points' })),
        ...market.movers.active.slice(0, 5).map((m) => ({ kind: 'security' as const, id: m.id, name: m.name, type: 'מניות', companyId: m.companyId, last: m.last, changePct: m.changePct, unit: 'agorot' })),
      ]
        .filter((s) => !list.find(s.kind, s.id))
        .slice(0, 6)
    : [];

  return (
    <>
      {(list.error || quotesError) && <div className="note bad">{list.error ?? `${quotesError} — מוצגים המחירים האחרונים`}</div>}

      {list.items.length === 0 && (
        <div className="hero">
          <h2>הרשימה שלך</h2>
          <p>הוסיפו ניירות למעקב מההצעות שכאן או מהחיפוש. הזינו כמה אתם מחזיקים — וכאן יופיעו שווי התיק והשינוי היומי בשקלים. הרשימה משותפת לטלפון ולמחשב.</p>
        </div>
      )}

      {holdings.length > 0 && <TotalsCard valued={valued} unpriced={holdings.length - valued.length} />}

      {holdings.length > 0 && (
        <>
          <h2 className="pane-title spaced">התיק שלי</h2>
          <div className="rows">
            {holdings.map((t) => {
              const q = quotes.get(refKey(t));
              const v = q ? holdingValue(t.qty ?? 0, t.avgCost, q.last, q.base) : null;
              return (
                <ListRow key={refKey(t)} t={t} sel={isSel(t)} companyId={t.companyId ?? q?.companyId} onPick={onPick} actions={<RowAction id={`edit-${refKey(t)}`} label="עריכת ההחזקה" onClick={() => onEditHolding(t)}>✎</RowAction>}>
                  <span className="grow">
                    <span className="name">{t.name}</span>
                    {q ? (
                      <span className="sub">
                        <Num>
                          {F.trimmed(t.qty ?? 0, 4)} × {F.trimmed(q.last)}
                        </Num>{' '}
                        <Num className={tone(q.changePct)}>{q.changePct == null ? '' : F.pct(q.changePct)}</Num>
                      </span>
                    ) : (
                      <span className="sub">{missing.get(refKey(t)) ?? 'ממתין למחיר…'}</span>
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
                </ListRow>
              );
            })}
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
                <ListRow
                  key={refKey(t)}
                  t={t}
                  sel={isSel(t)}
                  companyId={t.companyId ?? q?.companyId}
                  onPick={onPick}
                  actions={
                    <>
                      {t.kind === 'security' && <RowAction id={`hold-${refKey(t)}`} label="הוספת החזקה" onClick={() => onEditHolding(t)}>₪</RowAction>}
                      <RowAction id={`unfollow-${refKey(t)}`} label="הסרה מהמעקב" onClick={() => list.unfollow(t).catch(() => undefined)}>×</RowAction>
                    </>
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
                </ListRow>
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

      <div className="pane-foot">
        <button type="button" className="link" data-id="sign-out" onClick={() => list.signOut()}>
          יציאה
        </button>
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
function ListRow({ t, sel, companyId, onPick, actions, children }: { t: Tracked; sel: boolean; companyId: string | null | undefined; onPick: (s: Selection) => void; actions: ReactNode; children: ReactNode }) {
  return (
    <div className={`row has-actions ${sel ? 'sel' : ''}`}>
      <button type="button" className="row-main" data-id={`mine-${refKey(t)}`} onClick={() => onPick({ kind: t.kind, id: t.id, name: t.name })}>
        <Logo kind={t.kind} companyId={companyId} />
        {children}
      </button>
      <span className="row-actions">{actions}</span>
    </div>
  );
}

/** The number the owner opened the app for: what it is all worth, and what today did to it. */
function TotalsCard({ valued, unpriced }: { valued: Array<{ t: Tracked; v: HoldingValue }>; unpriced: number }) {
  const t = totals(valued.map((x) => x.v));
  const slices = [...valued].sort((a, b) => b.v.value - a.v.value);
  const named = slices.slice(0, SLICES.length - 1);
  const rest = slices.slice(SLICES.length - 1);
  return (
    <div className="hero" data-id="portfolio-total">
      <div className="hero-label">שווי התיק</div>
      <Num flashOn={t.value} className="hero-num">
        {F.shekels(t.value)}
      </Num>
      <div className="hero-line">
        <span>היום</span>
        <Num className={`strong ${tone(t.dayChange)}`}>{F.signedShekels(t.dayChange)}</Num>
        {t.dayChangePct != null && <Num className={tone(t.dayChange)}>({F.pct(t.dayChangePct)})</Num>}
      </div>
      {t.gain != null && (
        <div className="hero-line">
          <span>מאז הקנייה{t.gainIsPartial ? '*' : ''}</span>
          <Num className={`strong ${tone(t.gain)}`}>{F.signedShekels(t.gain)}</Num>
          {t.gainPct != null && <Num className={tone(t.gain)}>({F.pct(t.gainPct)})</Num>}
        </div>
      )}
      {/* What the portfolio is made of — worth showing once there is more than one thing in it. */}
      {slices.length > 1 && t.value > 0 && (
        <>
          <div className="slices">
            {slices.map((s, i) => (s.v.value > 0 ? <span key={refKey(s.t)} style={{ flexGrow: s.v.value, background: SLICES[Math.min(i, SLICES.length - 1)] }} /> : null))}
          </div>
          {named.map((s, i) => (
            <div className="slice-row" key={refKey(s.t)}>
              <i style={{ background: SLICES[i] }} />
              <span className="grow">{s.t.name}</span>
              <Num>{F.fixed((s.v.value / t.value) * 100, 1)}%</Num>
            </div>
          ))}
          {rest.length > 0 && (
            <div className="slice-row">
              <i style={{ background: SLICES[SLICES.length - 1] }} />
              <span className="grow">עוד {rest.length}</span>
              <Num>{F.fixed((rest.reduce((s, x) => s + x.v.value, 0) / t.value) * 100, 1)}%</Num>
            </div>
          )}
        </>
      )}
      {(t.gainIsPartial || unpriced > 0) && (
        <p className="hero-note">{[t.gainIsPartial ? '* רק החזקות שהוזן להן מחיר קנייה' : null, unpriced > 0 ? `${unpriced} החזקות בלי מחיר עדכני אינן בסכום` : null].filter(Boolean).join(' · ')}</p>
      )}
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
        <p>רשימת המעקב וההחזקות שלך פרטיות, ומשותפות לטלפון ולמחשב. כדי לראות אותן כאן נכנסים פעם אחת עם קוד הגישה.</p>
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

/** Enter or change how much of a paper is held. Saving also follows it. */
export function HoldingDialog({ item, lastPrice, list, onClose }: { item: Tracked | (Selection & { symbol?: string | null; type?: string | null; companyId?: string | null }); lastPrice: number | null; list: ListState; onClose: () => void }) {
  const existing = list.find(item.kind, item.id);
  const ref = useRef<HTMLDialogElement>(null);
  const [qtyText, setQtyText] = useState(existing?.qty ? String(existing.qty) : '');
  const [costText, setCostText] = useState(existing?.avgCost ? String(existing.avgCost) : '');
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    ref.current?.showModal();
  }, []);

  const qty = F.parse(qtyText);
  const cost = F.parse(costText);
  const qtyBad = qtyText.trim() !== '' && (qty === null || qty <= 0);
  const costBad = costText.trim() !== '' && (cost === null || cost <= 0);
  const canSave = qty !== null && qty > 0 && !costBad;
  const held = (existing?.qty ?? 0) > 0;

  const done = (p: Promise<void>) => p.then(onClose, (e: Error) => setError(e.message));

  return (
    <dialog ref={ref} className="dialog" onClose={onClose} onClick={(e) => e.target === ref.current && onClose()}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (canSave) done(list.hold({ ...item, name: existing?.name ?? item.name }, qty, costText.trim() === '' ? null : cost));
        }}
      >
        <h2>{item.name}</h2>
        <p className="muted">ההחזקה נשמרת בשרת שלך ומוצגת גם בטלפון.</p>
        <label>
          כמות
          <input data-id="holding-qty" inputMode="decimal" value={qtyText} onChange={(e) => setQtyText(e.target.value)} className={qtyBad ? 'bad' : ''} autoFocus />
          <small className={qtyBad ? 'bad' : ''}>{qtyBad ? 'מספר גדול מאפס' : 'יחידות; באג"ח — ערך נקוב בשקלים'}</small>
        </label>
        <label>
          מחיר קנייה ממוצע, באגורות
          <input data-id="holding-cost" inputMode="decimal" value={costText} onChange={(e) => setCostText(e.target.value)} className={costBad ? 'bad' : ''} />
          <small className={costBad ? 'bad' : ''}>
            {costBad ? 'מספר גדול מאפס, או להשאיר ריק' : lastPrice != null ? <>לא חובה. המחיר עכשיו: <span className="num">{F.trimmed(lastPrice)}</span> אג&apos;</> : 'לא חובה — בלעדיו יוצג שווי בלי רווח והפסד'}
          </small>
        </label>
        {qty !== null && qty > 0 && lastPrice != null && (
          <p>
            שווי לפי המחיר עכשיו: <span className="num strong">{F.shekels((qty * lastPrice) / 100)}</span>
          </p>
        )}
        {error && <div className="note bad">{error}</div>}
        <div className="dialog-actions">
          <button type="submit" className="btn primary" data-id="holding-save" disabled={!canSave}>
            שמירה
          </button>
          <button type="button" className="btn" data-id="holding-cancel" onClick={onClose}>
            ביטול
          </button>
          <span className="grow" />
          {held &&
            (confirming ? (
              // Removing a holding throws away numbers the owner typed, so it asks first.
              <button type="button" className="btn danger" data-id="holding-clear-confirm" onClick={() => done(list.clearHolding(item))}>
                למחוק — בטוח
              </button>
            ) : (
              <button type="button" className="btn ghost-danger" data-id="holding-clear" onClick={() => setConfirming(true)}>
                מחיקת ההחזקה
              </button>
            ))}
        </div>
      </form>
    </dialog>
  );
}
