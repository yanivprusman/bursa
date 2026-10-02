'use client';

import { useState } from 'react';
import type { IndexRow, Mover, Overview } from './api';
import { useResource, type Resource, type Selection } from './hooks';
import * as F from './format';
import { Chip, Logo, Num, Segmented, SkeletonRows, Spark } from './parts';

type Tab = 'gainers' | 'losers' | 'active';

/** The market side of the workspace: the headline indices, the day's mood, the movers. */
export function Market({ market, selected, onPick }: { market: Resource<Overview>; selected: Selection; onPick: (s: Selection) => void }) {
  const [tab, setTab] = useState<Tab>('gainers');
  const [all, setAll] = useState(false);
  const m = market.data;

  if (!m) {
    return (
      <>
        <h2 className="pane-title">שוק</h2>
        {market.error ? (
          <div className="note bad">
            {market.error}{' '}
            <button type="button" className="link" data-id="retry-market" onClick={market.refresh}>
              נסו שוב
            </button>
          </div>
        ) : (
          <SkeletonRows n={9} />
        )}
      </>
    );
  }

  const b = m.breadth;
  const up = b?.gainers ?? 0;
  const down = b?.decliners ?? 0;
  const same = b?.unchanged ?? 0;
  const movers: Mover[] = m.movers[tab];

  return (
    <>
      <div className="pane-head">
        <h2 className="pane-title">מדדים</h2>
        <button type="button" className="link" data-id="toggle-all-indices" onClick={() => setAll((v) => !v)}>
          {all ? 'רק המובילים' : 'כל המדדים'}
        </button>
      </div>
      {all ? (
        <AllIndices selected={selected} onPick={onPick} live={m.open} />
      ) : (
        <div className="rows">
          {m.indices.map((i) => (
            <button
              type="button"
              key={i.id}
              className={`row ${selected.kind === 'index' && selected.id === i.id ? 'sel' : ''}`}
              data-id={`index-${i.id}`}
              onClick={() => onPick({ kind: 'index', id: i.id, name: i.name })}
            >
              <span className="grow name">{i.name}</span>
              <Spark values={i.spark} />
              <Num flashOn={i.last} className="strong">
                {F.fixed(i.last, 2)}
              </Num>
              <Chip pct={i.changePct} />
            </button>
          ))}
        </div>
      )}

      {up + down + same > 0 && (
        <>
          <h2 className="pane-title spaced">ת&quot;א-125 היום</h2>
          <div className="breadth">
            <div className="breadth-nums">
              <span className="down">
                <Num className="big">{down}</Num> ירדו
              </span>
              {same > 0 && <span className="muted">{same} ללא שינוי</span>}
              <span className="up">
                עלו <Num className="big">{up}</Num>
              </span>
            </div>
            <div className="breadth-bar">
              {down > 0 && <span className="down" style={{ flexGrow: down }} />}
              {same > 0 && <span className="flat" style={{ flexGrow: same }} />}
              {up > 0 && <span className="up" style={{ flexGrow: up }} />}
            </div>
          </div>
        </>
      )}

      <h2 className="pane-title spaced">בולטות בת&quot;א-125</h2>
      <Segmented
        id="movers"
        value={tab}
        onChange={setTab}
        options={[
          { value: 'gainers', label: 'עולות' },
          { value: 'losers', label: 'יורדות' },
          { value: 'active', label: 'מחזור גבוה' },
        ]}
      />
      <div className="rows">
        {movers.map((x) => (
          <button
            type="button"
            key={x.id}
            className={`row ${selected.kind === 'security' && selected.id === x.id ? 'sel' : ''}`}
            data-id={`mover-${x.id}`}
            onClick={() => onPick({ kind: 'security', id: x.id, name: x.name })}
          >
            <Logo kind="security" companyId={x.companyId} />
            <span className="grow">
              <span className="name">{x.name}</span>
              {/* On the "most traded" list the turnover is the point; it arrives in thousands of shekels. */}
              {x.turnover != null && <span className="sub">מחזור {F.bigShekels(x.turnover * 1000)}</span>}
            </span>
            <Num flashOn={x.last} className="strong">
              {F.trimmed(x.last)}
            </Num>
            <Chip pct={x.changePct} />
          </button>
        ))}
      </div>

      {m.turnovers.length > 0 && (
        <>
          <h2 className="pane-title spaced">מחזורי המסחר</h2>
          <div className="facts">
            {m.turnovers.map((t) => (
              <div className="fact" key={t.name}>
                <span className="label">{t.name}</span>
                {/* Turnovers arrive in thousands of shekels. */}
                <span className="value">{F.bigShekels(t.value * 1000)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </>
  );
}

/** Every index the exchange publishes, grouped the way the exchange groups them. */
function AllIndices({ selected, onPick, live }: { selected: Selection; onPick: (s: Selection) => void; live: boolean }) {
  const res = useResource<{ indices: IndexRow[] }>('/api/indices', live);
  if (!res.data) return res.error ? <div className="note bad">{res.error}</div> : <SkeletonRows n={10} />;
  const groups = new Map<string, IndexRow[]>();
  for (const row of res.data.indices) groups.set(row.category, [...(groups.get(row.category) ?? []), row]);
  return (
    <>
      {[...groups].map(([category, rows]) => (
        <div key={category}>
          <h3 className="group-title">{category}</h3>
          <div className="rows">
            {rows.map((i) => (
              <button
                type="button"
                key={i.id}
                className={`row tight ${selected.kind === 'index' && selected.id === i.id ? 'sel' : ''}`}
                data-id={`index-${i.id}`}
                onClick={() => onPick({ kind: 'index', id: i.id, name: i.name })}
              >
                <span className="grow name">{i.name}</span>
                <Num flashOn={i.last} className="strong">
                  {F.fixed(i.last, 2)}
                </Num>
                <Chip pct={i.changePct} />
              </button>
            ))}
          </div>
        </div>
      ))}
    </>
  );
}
