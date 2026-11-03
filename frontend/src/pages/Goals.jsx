import { useState } from 'react';
import { api } from '../api.js';
import { formatDate, money, today, useApi } from '../util.js';

const EMPTY = { name: '', targetAmount: '', monthlyContribution: '', startDate: today() };

export default function Goals() {
  const { data: goals, error, reload } = useApi('/goals');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState('');
  const [topUps, setTopUps] = useState({});

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  const run = async (fn) => {
    setFormError('');
    try {
      await fn();
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  const submit = (e) => {
    e.preventDefault();
    run(async () => {
      if (editingId) await api(`/goals/${editingId}`, { method: 'PUT', body: form });
      else await api('/goals', { method: 'POST', body: form });
      setForm(EMPTY);
      setEditingId(null);
    });
  };

  const contribute = (g) =>
    run(async () => {
      await api(`/goals/${g.id}/contribute`, { method: 'POST', body: { amount: topUps[g.id] } });
      setTopUps({ ...topUps, [g.id]: '' });
    });

  const remove = (g) => {
    if (window.confirm(`Delete the goal "${g.name}"?`)) run(() => api(`/goals/${g.id}`, { method: 'DELETE' }));
  };

  return (
    <div className="stack-lg">
      <h1>Savings goals</h1>

      <form className="card stack" onSubmit={submit}>
        <h2>{editingId ? 'Edit goal' : 'New goal'}</h2>
        <p className="muted small">
          Choose what you're saving for and how much to put aside each month. You'll get a notification when you've saved
          enough to buy it.
        </p>
        <div className="row">
          <label>
            What are you saving for?
            <input required value={form.name} onChange={set('name')} placeholder="e.g. New laptop" />
          </label>
          <label>
            Price
            <input required type="number" min="0.01" step="0.01" value={form.targetAmount} onChange={set('targetAmount')} />
          </label>
        </div>
        <div className="row">
          <label>
            Save each month
            <input
              required
              type="number"
              min="0"
              step="0.01"
              value={form.monthlyContribution}
              onChange={set('monthlyContribution')}
            />
          </label>
          <label>
            Plan starts
            <input type="date" value={form.startDate} onChange={set('startDate')} />
          </label>
        </div>
        {formError && <p className="error">{formError}</p>}
        <div className="actions">
          <button type="submit">{editingId ? 'Save changes' : 'Create goal'}</button>
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

      {error && <p className="error">{error}</p>}
      {goals?.length === 0 && <p className="muted">No goals yet.</p>}
      <div className="goal-grid">
        {goals?.map((g) => (
          <div key={g.id} className="card stack">
            <div className="section-head">
              <h3>{g.name}</h3>
              {g.availableToPurchase && <span className="pill success">Ready to buy</span>}
            </div>
            <div className="meter">
              <div className="meter-fill" style={{ width: `${g.progressPercent}%` }} />
            </div>
            <div className="small">
              {money(g.savedAmount)} of {money(g.targetAmount)} ({g.progressPercent}%)
            </div>
            <div className="muted small">
              {money(g.monthlyContribution)} a month since {formatDate(g.startDate)}
              {Number(g.extraSaved) > 0 && `, plus ${money(g.extraSaved)} in top-ups`}
            </div>
            {!g.availableToPurchase && (
              <div className="small">
                {g.projectedDate ? `On track for ${formatDate(g.projectedDate)}` : 'Add a monthly amount to get a date.'}
              </div>
            )}
            <div className="inline-form">
              <input
                type="number"
                min="0.01"
                step="0.01"
                placeholder="Top-up amount"
                value={topUps[g.id] ?? ''}
                onChange={(e) => setTopUps({ ...topUps, [g.id]: e.target.value })}
              />
              <button className="small-button" disabled={!topUps[g.id]} onClick={() => contribute(g)}>
                Add
              </button>
            </div>
            <div className="actions">
              <button
                className="small-button secondary"
                onClick={() => {
                  setEditingId(g.id);
                  setForm({
                    name: g.name,
                    targetAmount: g.targetAmount,
                    monthlyContribution: g.monthlyContribution,
                    startDate: g.startDate,
                  });
                }}
              >
                Edit
              </button>
              <button className="small-button danger" onClick={() => remove(g)}>
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
