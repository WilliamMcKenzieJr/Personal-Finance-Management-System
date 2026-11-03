import { useState } from 'react';
import { api } from '../api.js';
import { money } from '../util.js';

const newItem = () => ({ key: crypto.randomUUID(), name: '', cost: '' });

/** "What if I buy this?" — shows the effect of one or more purchases without saving them. */
export default function Planner() {
  const [items, setItems] = useState([newItem()]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const update = (key, field, value) =>
    setItems(items.map((i) => (i.key === key ? { ...i, [field]: value } : i)));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      setResult(
        await api('/planning', {
          method: 'POST',
          body: { items: items.map(({ name, cost }) => ({ name, cost })) },
        }),
      );
    } catch (err) {
      setError(err.message);
    }
  };

  const tone = !result ? '' : !result.affordable ? 'error' : result.exceedsSavingsLimit ? 'warning' : 'success-box';

  return (
    <div className="stack-lg">
      <h1>Purchase planner</h1>
      <p className="muted">Add one or more things you're thinking of buying to see how they'd affect your money.</p>

      <form className="card stack" onSubmit={submit}>
        {items.map((item, index) => (
          <div key={item.key} className="row align-end">
            <label>
              Item {index + 1}
              <input required value={item.name} onChange={(e) => update(item.key, 'name', e.target.value)} />
            </label>
            <label>
              Cost
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={item.cost}
                onChange={(e) => update(item.key, 'cost', e.target.value)}
              />
            </label>
            <button
              type="button"
              className="secondary"
              disabled={items.length === 1}
              onClick={() => setItems(items.filter((i) => i.key !== item.key))}
            >
              Remove
            </button>
          </div>
        ))}
        <div className="actions">
          <button type="button" className="secondary" onClick={() => setItems([...items, newItem()])}>
            Add another item
          </button>
          <button type="submit">See the effect</button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>

      {result && (
        <section className="card stack">
          <p className={tone}>{result.message}</p>
          <div className="stats">
            <div className="stat">
              <span className="muted">Money now</span>
              <strong>{money(result.currentBalance)}</strong>
            </div>
            <div className="stat">
              <span className="muted">Total cost</span>
              <strong>{money(result.totalCost)}</strong>
            </div>
            <div className="stat">
              <span className="muted">Money after</span>
              <strong className={Number(result.balanceAfter) < 0 ? 'negative' : ''}>{money(result.balanceAfter)}</strong>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Cost</th>
                  <th>Money left after</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((line, i) => (
                  <tr key={i}>
                    <td>{line.name}</td>
                    <td className="num">{money(line.cost)}</td>
                    <td className={`num ${Number(line.balanceAfter) < 0 ? 'negative' : ''}`}>{money(line.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <h3>Next 30 days</h3>
          <ul className="plain">
            <li>Bills due: {money(result.billsDueNext30Days)}</li>
            <li>Income expected: {money(result.incomeNext30Days)}</li>
            <li>
              Projected money in 30 days:{' '}
              <strong className={Number(result.projectedIn30Days) < 0 ? 'negative' : ''}>
                {money(result.projectedIn30Days)}
              </strong>
            </li>
          </ul>
          <h3>This month's spending limit</h3>
          <ul className="plain">
            <li>Spent so far: {money(result.monthSpentNow)}</li>
            <li>After these purchases: {money(result.monthSpentAfter)}</li>
            <li>Limit (income minus savings): {money(result.spendingLimit)}</li>
          </ul>
        </section>
      )}
    </div>
  );
}
