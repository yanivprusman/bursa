'use client';

import { useEffect, useRef, useState } from 'react';
import type { Order, Paper, Side, Summary } from './api';
import type { AccountState } from './hooks';
import * as F from './format';
import { Logo, Num, Segmented } from './parts';

type Props = {
  paper: Paper;
  side: Side;
  /** The last price the page shows, agorot — for the estimate only; the server prices the order itself. */
  last: number;
  /** The paper is trading right now, so a market order fills at once rather than at the next opening. */
  liveNow: boolean;
  account: AccountState;
  onClose: () => void;
};

/** The broker's commission on a deal of `gross` agorot, by the rules the server sent. */
export function feeOf(rules: Summary['rules'], gross: number) {
  return Math.max(rules.feeMin, Math.ceil(gross * rules.feeRate));
}

/** The most units `cash` buys at `price`, fee included. */
function maxBuy(rules: Summary['rules'], cash: number, price: number): number {
  if (price <= 0) return 0;
  let q = Math.floor(cash / (price * (1 + rules.feeRate)));
  while (q > 0 && Math.round(q * price) + feeOf(rules, Math.round(q * price)) > cash) q--;
  return Math.max(0, q);
}

/**
 * A market order: buy or sell, how many. Everything the owner types is the quantity —
 * the price is the exchange's, read by the server when the order arrives.
 */
export function TradeDialog({ paper, side: initialSide, last, liveNow, account, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [side, setSide] = useState<Side>(initialSide);
  const [qtyText, setQtyText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<Order | null>(null);

  useEffect(() => {
    ref.current?.showModal();
  }, []);

  const a = account.data;
  if (!a) return null;

  const held = a.positions.find((p) => p.paper.id === paper.id)?.qty ?? 0;
  const selling = a.pending.reduce((s, o) => s + (o.side === 'sell' && o.paper.id === paper.id ? o.qty : 0), 0);
  const canSell = held - selling;
  const max = side === 'buy' ? maxBuy(a.rules, a.available, last) : canSell;

  const qty = /^\d+$/.test(qtyText.trim()) ? Number(qtyText.trim()) : null;
  const gross = qty ? Math.round(qty * last) : 0;
  const fee = qty ? feeOf(a.rules, gross) : 0;
  const total = side === 'buy' ? gross + fee : gross - fee;
  const tooMany = qty !== null && qty > max;
  const bad = qtyText.trim() !== '' && (qty === null || qty <= 0);
  const canSend = qty !== null && qty > 0 && !tooMany && !busy;
  const verb = side === 'buy' ? 'קנייה' : 'מכירה';

  const send = async () => {
    if (!canSend) return;
    setBusy(true);
    setError(null);
    try {
      setDone(await account.order(side, paper.id, qty));
    } catch (e) {
      setError((e as Error).message);
    }
    setBusy(false);
  };

  return (
    <dialog ref={ref} className="dialog trade" onClose={onClose} onClick={(e) => e.target === ref.current && onClose()}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (done) onClose();
          else send();
        }}
      >
        <div className="trade-head">
          <Logo kind="security" companyId={paper.companyId} size={40} />
          <div className="grow">
            <h2>{paper.name}</h2>
            <span className="practice-tag">חשבון תרגול · כסף מדומה, מחירים אמיתיים</span>
          </div>
        </div>

        {done ? (
          <Outcome order={done} account={a} />
        ) : (
          <>
            {/* Selling is offered only for something held; there is no short selling. */}
            {held > 0 && (
              <Segmented
                id="trade-side"
                value={side}
                onChange={(s) => {
                  setSide(s);
                  setError(null);
                }}
                options={[
                  { value: 'buy', label: 'קנייה' },
                  { value: 'sell', label: 'מכירה' },
                ]}
              />
            )}

            <div className="trade-price">
              <span className="muted">{liveNow ? 'מחיר עכשיו' : 'שער אחרון'}</span>
              <Num className="strong">{F.trimmed(last)}</Num>
              <span className="muted">אג&apos;</span>
            </div>

            <label>
              כמות
              <div className="qty-row">
                <input data-id="trade-qty" inputMode="numeric" value={qtyText} onChange={(e) => setQtyText(e.target.value)} className={bad || tooMany ? 'bad' : ''} autoFocus placeholder="0" />
                <button type="button" className="btn" data-id="trade-max" disabled={max <= 0} onClick={() => setQtyText(String(max))}>
                  {side === 'buy' ? 'כמה שאפשר' : 'הכול'}
                </button>
              </div>
              <small className={bad || tooMany ? 'bad' : ''}>
                {bad ? 'מספר שלם גדול מאפס' : side === 'buy' ? <>אפשר לקנות עד <span className="num">{F.trimmed(max, 0)}</span> במזומן הפנוי</> : <>בתיק <span className="num">{F.trimmed(canSell, 0)}</span> יחידות למכירה</>}
              </small>
            </label>

            <div className="trade-sum">
              <div>
                <span>שווי העסקה</span>
                <Num>{F.shekels(gross / 100)}</Num>
              </div>
              <div>
                <span>עמלה ({F.trimmed(a.rules.feeRate * 100, 2)}%, לפחות {F.shekels(a.rules.feeMin / 100)})</span>
                <Num>{F.shekels(fee / 100)}</Num>
              </div>
              <div className="total">
                <span>{side === 'buy' ? 'סה״כ לתשלום' : 'סה״כ תקבלו'}</span>
                <Num>{F.shekels(total / 100)}</Num>
              </div>
              <div className="after">
                <span>מזומן פנוי אחרי</span>
                <Num>{F.shekels((a.available + (side === 'buy' ? -total : total)) / 100)}</Num>
              </div>
            </div>

            <p className={`trade-when ${liveNow ? 'now' : ''}`}>
              {liveNow
                ? 'הנייר נסחר עכשיו — הפקודה תבוצע מיד, בשער שבבורסה ברגע השליחה.'
                : 'הנייר לא נסחר כרגע — הפקודה תמתין ותבוצע בשער הפתיחה של יום המסחר הבא. הסכום הוא הערכה לפי השער האחרון.'}
            </p>
          </>
        )}

        {error && <div className="note bad">{error}</div>}

        <div className="dialog-actions">
          {done ? (
            <button type="submit" className="btn primary" data-id="trade-done">
              סגירה
            </button>
          ) : (
            <>
              <button type="submit" className={`btn primary ${side === 'sell' ? 'sell' : ''}`} data-id="trade-send" disabled={!canSend}>
                {busy ? 'שולח…' : qty ? `${verb} של ${F.trimmed(qty, 0)}` : verb}
              </button>
              <button type="button" className="btn" data-id="trade-cancel" onClick={onClose}>
                ביטול
              </button>
            </>
          )}
        </div>
      </form>
    </dialog>
  );
}

/** What became of the order, in the words of a broker's confirmation. */
function Outcome({ order, account }: { order: Order; account: Summary }) {
  const trade = account.trades.find((t) => t.orderId === order.id);
  const verb = order.side === 'buy' ? 'נקנו' : 'נמכרו';
  if (order.status === 'filled' && trade) {
    return (
      <div className="outcome ok" data-id="trade-outcome">
        <strong>בוצע</strong>
        <p>
          {verb} <Num>{F.trimmed(trade.qty, 0)}</Num> יחידות בשער <Num>{F.trimmed(trade.price)}</Num> אג&apos;
          {trade.how === 'open' ? ' (שער הפתיחה)' : ''}.
        </p>
        <p className="muted">
          {order.side === 'buy' ? 'שולם' : 'התקבל'} <Num>{F.shekels((order.side === 'buy' ? trade.gross + trade.fee : trade.gross - trade.fee) / 100)}</Num>, כולל עמלה של <Num>{F.shekels(trade.fee / 100)}</Num>. במזומן עכשיו: <Num>{F.shekels(account.cash / 100)}</Num>.
        </p>
      </div>
    );
  }
  if (order.status === 'pending') {
    return (
      <div className="outcome wait" data-id="trade-outcome">
        <strong>הפקודה נקלטה וממתינה</strong>
        <p>
          היא תבוצע בשער הפתיחה של יום המסחר הראשון מ-<Num>{F.date(order.fillFrom)}</Num>. אפשר לבטל אותה עד אז ברשימה &quot;פקודות ממתינות&quot;.
        </p>
      </div>
    );
  }
  return (
    <div className="outcome bad" data-id="trade-outcome">
      <strong>הפקודה נדחתה</strong>
      <p>{order.reason}</p>
    </div>
  );
}
