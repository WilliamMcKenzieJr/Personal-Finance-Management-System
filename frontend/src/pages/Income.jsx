import { useState } from 'react';
import { api } from '../api.js';
import { RECURRENCES, formatDate, label, money, today, useApi } from '../util.js';

const EMPTY = { source: '', amount: '', date: today(), recurring: false, frequency: 'MONTHLY', endDate: '' };

export default function Income() {
  const { data: incomes, error, reload } = useApi('/incomes');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState('');

  const set = (name) => (e) =>
    setForm({ ...form, [name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setFormError('');
    const body = {
      source: form.source,
      amount: form.amount,
      date: form.date,
      frequency: form.recurring ? form.frequency : null,
      endDate: form.recurring && form.endDate ? form.endDate : null,
    };
    try {
      if (editingId) await api(`/incomes/${editingId}`, { method: 'PUT', body });
      else await api('/incomes', { method: 'POST', body });
      setForm(EMPTY);
      setEditingId(null);
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  const edit = (i) => {
    setEditingId(i.id);
    setForm({
      source: i.source,
      amount: i.amount,
      date: i.date,
      recurring: i.recurring,
      frequency: i.frequency ?? 'MONTHLY',
      endDate: i.endDate ?? '',
    });
  };

  const remove = async (i) => {
    if (!window.confirm(`Delete "${i.source}"?`)) return;
    try {
      await api(`/incomes/${i.id}`, { method: 'DELETE' });
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  return (
    <div className="stack-lg">
      <h1>Income</h1>

      <form className="card stack" onSubmit={submit}>
        <h2>{editingId ? 'Edit income' : 'Add income'}</h2>
        <div className="row">
          <label>
            Source
            <input required value={form.source} onChange={set('source')} placeholder="e.g. Salary" />
          </label>
          <label>
            Amount
            <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={set('amount')} />
          </label>
          <label>
            {form.recurring ? 'First payment date' : 'Date received'}
            <input required type="date" value={form.date} onChange={set('date')} />
          </label>
        </div>
        <label className="check">
          <input type="checkbox" checked={form.recurring} onChange={set('recurring')} />
          This is recurring income
        </label>
        {form.recurring && (
          <div className="row">
            <label>
              Paid
              <select value={form.frequency} onChange={set('frequency')}>
                {RECURRENCES.map((r) => (
                  <option key={r} value={r}>
                    {label(r)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Last payment <span className="muted">(optional)</span>
              <input type="date" value={form.endDate} onChange={set('endDate')} />
            </label>
          </div>
        )}
        {formError && <p className="error">{formError}</p>}
        <div className="actions">
          <button type="submit">{editingId ? 'Save changes' : 'Add income'}</button>
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
        <h2>Your income</h2>
        {error && <p className="error">{error}</p>}
        {incomes?.length === 0 && <p className="muted">No income recorded yet.</p>}
        {incomes?.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Source</th>
                  <th>Amount</th>
                  <th>Date</th>
                  <th>Schedule</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {incomes.map((i) => (
                  <tr key={i.id}>
                    <td>{i.source}</td>
                    <td className="num">{money(i.amount)}</td>
                    <td>{formatDate(i.date)}</td>
                    <td>
                      {i.recurring ? `${label(i.frequency)}${i.endDate ? ` until ${formatDate(i.endDate)}` : ''}` : 'One-off'}
                    </td>
                    <td className="actions">
                      <button className="small-button secondary" onClick={() => edit(i)}>
                        Edit
                      </button>
                      <button className="small-button danger" onClick={() => remove(i)}>
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
