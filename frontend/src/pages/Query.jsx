import { useState } from 'react';
import { api } from '../api.js';
import { EXPENSE_CATEGORIES, formatDate, label, money, today } from '../util.js';

const firstOfMonth = () => `${today().slice(0, 8)}01`;

/** Search costs, income or the overall total between two dates. */
export default function Query() {
  const [params, setParams] = useState({ type: 'TOTAL', from: firstOfMonth(), to: today(), category: '' });
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const set = (name) => (e) => setParams({ ...params, [name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const qs = new URLSearchParams({ type: params.type, from: params.from, to: params.to });
    if (params.type === 'COST' && params.category) qs.set('category', params.category);
    try {
      setResult(await api(`/query?${qs}`));
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="stack-lg">
      <h1>Search your money</h1>

      <form className="card row align-end" onSubmit={submit}>
        <label>
          Show
          <select value={params.type} onChange={set('type')}>
            <option value="TOTAL">Total amount of money</option>
            <option value="COST">Costs</option>
            <option value="INCOME">Income</option>
          </select>
        </label>
        <label>
          From
          <input type="date" required value={params.from} onChange={set('from')} />
        </label>
        <label>
          To
          <input type="date" required value={params.to} onChange={set('to')} />
        </label>
        {params.type === 'COST' && (
          <label>
            Category
            <select value={params.category} onChange={set('category')}>
              <option value="">All</option>
              {EXPENSE_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {label(c)}
                </option>
              ))}
            </select>
          </label>
        )}
        <button type="submit">Search</button>
      </form>
      {error && <p className="error">{error}</p>}

      {result && (
        <section className="card stack">
          <div className="stats">
            {result.type !== 'COST' && (
              <div className="stat">
                <span className="muted">Income</span>
                <strong>{money(result.incomeTotal)}</strong>
              </div>
            )}
            {result.type !== 'INCOME' && (
              <div className="stat">
                <span className="muted">Costs</span>
                <strong>{money(result.costTotal)}</strong>
              </div>
            )}
            {result.type === 'TOTAL' && (
              <>
                <div className="stat">
                  <span className="muted">Net for this period</span>
                  <strong className={Number(result.net) < 0 ? 'negative' : ''}>{money(result.net)}</strong>
                </div>
                <div className="stat">
                  <span className="muted">Total money on {formatDate(result.to)}</span>
                  <strong>{money(result.balanceAtEnd)}</strong>
                </div>
              </>
            )}
          </div>
          {result.entries.length === 0 ? (
            <p className="muted">Nothing in this period.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Category</th>
                    <th>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {result.entries.map((entry, i) => (
                    <tr key={i}>
                      <td>{formatDate(entry.date)}</td>
                      <td>{entry.label}</td>
                      <td>{label(entry.category)}</td>
                      <td className={`num ${Number(entry.amount) < 0 ? 'negative' : ''}`}>{money(entry.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
