import { useState } from 'react';
import { api } from '../api.js';
import { EXPENSE_CATEGORIES, formatDate, label, money, today, useApi } from '../util.js';

const EMPTY = { description: '', amount: '', date: today(), category: 'GROCERIES' };

export default function Costs() {
  const { data: expenses, error, reload } = useApi('/expenses');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState('');
  const [warning, setWarning] = useState('');
  const [filter, setFilter] = useState('');

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setFormError('');
    try {
      const res = editingId
        ? await api(`/expenses/${editingId}`, { method: 'PUT', body: form })
        : await api('/expenses', { method: 'POST', body: form });
      setWarning(res.savingsWarning ?? '');
      setForm(EMPTY);
      setEditingId(null);
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  const edit = (x) => {
    setEditingId(x.id);
    setForm({ description: x.description, amount: x.amount, date: x.date, category: x.category });
  };

  const remove = async (x) => {
    if (!window.confirm(`Delete "${x.description}"?`)) return;
    try {
      await api(`/expenses/${x.id}`, { method: 'DELETE' });
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  const shown = (expenses ?? []).filter((x) => !filter || x.category === filter);

  return (
    <div className="stack-lg">
      <h1>Costs</h1>
      {warning && (
        <p className="warning" role="alert">
          {warning}
        </p>
      )}

      <form className="card stack" onSubmit={submit}>
        <h2>{editingId ? 'Edit cost' : 'Add a purchase'}</h2>
        <div className="row">
          <label>
            Description
            <input required value={form.description} onChange={set('description')} placeholder="e.g. Weekly shop" />
          </label>
          <label>
            Amount
            <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={set('amount')} />
          </label>
        </div>
        <div className="row">
          <label>
            Date of purchase
            <input required type="date" value={form.date} onChange={set('date')} />
          </label>
          <label>
            Category
            <select value={form.category} onChange={set('category')}>
              {EXPENSE_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {label(c)}
                </option>
              ))}
            </select>
          </label>
        </div>
        {formError && <p className="error">{formError}</p>}
        <div className="actions">
          <button type="submit">{editingId ? 'Save changes' : 'Add cost'}</button>
          {editingId && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditingId(null);
                setForm(EMPTY);
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>

      <section className="card">
        <div className="section-head">
          <h2>Your costs</h2>
          <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter by category">
            <option value="">All categories</option>
            {EXPENSE_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {label(c)}
              </option>
            ))}
          </select>
        </div>
        {error && <p className="error">{error}</p>}
        {expenses && shown.length === 0 && <p className="muted">No costs to show.</p>}
        {shown.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Category</th>
                  <th>Amount</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {shown.map((x) => (
                  <tr key={x.id}>
                    <td>{formatDate(x.date)}</td>
                    <td>{x.description}</td>
                    <td>{label(x.category)}</td>
                    <td className="num">{money(x.amount)}</td>
                    <td className="actions">
                      <button className="small-button secondary" onClick={() => edit(x)}>
                        Edit
                      </button>
                      <button className="small-button danger" onClick={() => remove(x)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
