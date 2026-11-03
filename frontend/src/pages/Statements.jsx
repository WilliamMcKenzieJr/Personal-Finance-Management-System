import { useState } from 'react';
import { api } from '../api.js';
import { EXPENSE_CATEGORIES, formatDate, formatDateTime, label, money, useApi } from '../util.js';

/** Upload a CSV bank statement, review the transactions, then import the ones you keep. */
export default function Statements() {
  const { data: history, reload } = useApi('/statements');
  const [preview, setPreview] = useState(null);
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const upload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    setMessage('');
    setBusy(true);
    const form = new FormData();
    form.append('file', file);
    try {
      const res = await api('/statements/preview', { method: 'POST', form });
      setPreview(res);
      setRows(res.rows.map((r, i) => ({ ...r, key: i, include: true })));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const update = (key, field, value) => setRows(rows.map((r) => (r.key === key ? { ...r, [field]: value } : r)));

  const included = rows.filter((r) => r.include);
  const missingCategory = included.some((r) => r.kind === 'EXPENSE' && !r.category);

  const importRows = async () => {
    setError('');
    setBusy(true);
    try {
      const res = await api('/statements/import', {
        method: 'POST',
        body: {
          fileName: preview.fileName,
          rows: included.map(({ date, description, amount, kind, category }) => ({
            date,
            description,
            amount,
            kind,
            category: kind === 'EXPENSE' ? category : null,
          })),
        },
      });
      setMessage(`Imported ${res.expensesImported} costs and ${res.incomesImported} income entries.`);
      setPreview(null);
      setRows([]);
      reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack-lg">
      <h1>Bank statements</h1>

      <section className="card stack">
        <h2>Upload a statement</h2>
        <p className="muted small">
          Export your statement from online banking as a CSV file. It needs a date, a description and either an Amount
          column (negative for money out) or separate Debit and Credit columns. You'll review every transaction before
          it's saved.
        </p>
        <label className="file-input">
          <input type="file" accept=".csv,text/csv" onChange={upload} disabled={busy} />
        </label>
        {message && <p className="success-box">{message}</p>}
        {error && <p className="error">{error}</p>}
      </section>

      {preview && (
        <section className="card stack">
          <h2>Review {preview.fileName}</h2>
          {preview.skippedLines.length > 0 && (
            <p className="warning small">
              {preview.skippedLines.length} line(s) couldn't be read and were skipped.
            </p>
          )}
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Import</th>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Type</th>
                  <th>Category</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.key} className={r.include ? '' : 'faded'}>
                    <td>
                      <input
                        type="checkbox"
                        checked={r.include}
                        onChange={(e) => update(r.key, 'include', e.target.checked)}
                        aria-label="Include this transaction"
                      />
                    </td>
                    <td>{formatDate(r.date)}</td>
                    <td>{r.description}</td>
                    <td className="num">{money(r.amount)}</td>
                    <td>
                      <select value={r.kind} onChange={(e) => update(r.key, 'kind', e.target.value)}>
                        <option value="EXPENSE">Cost</option>
                        <option value="INCOME">Income</option>
                      </select>
                    </td>
                    <td>
                      {r.kind === 'EXPENSE' && (
                        <select
                          value={r.category ?? ''}
                          onChange={(e) => update(r.key, 'category', e.target.value || null)}
                          className={r.include && !r.category ? 'needs-value' : ''}
                        >
                          <option value="">Choose…</option>
                          {EXPENSE_CATEGORIES.map((c) => (
                            <option key={c} value={c}>
                              {label(c)}
                            </option>
                          ))}
                        </select>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {missingCategory && <p className="muted small">Choose a category for every cost you're importing.</p>}
          <div className="actions">
            <button onClick={importRows} disabled={busy || included.length === 0 || missingCategory}>
              Import {included.length} transaction{included.length === 1 ? '' : 's'}
            </button>
            <button className="secondary" onClick={() => setPreview(null)}>
              Cancel
            </button>
          </div>
        </section>
      )}

      <section className="card">
        <h2>Past uploads</h2>
        {history?.length === 0 && <p className="muted">No statements uploaded yet.</p>}
        {history?.length > 0 && (
          <ul className="list">
            {history.map((s) => (
              <li key={s.id}>
                <div>
                  <strong>{s.fileName}</strong>
                  <div className="muted small">{formatDateTime(s.uploadedAt)}</div>
                </div>
                <span className="small">
                  {s.expensesImported} costs, {s.incomesImported} income
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
