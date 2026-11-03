import { useState } from 'react';
import { api } from '../api.js';
import { BILL_TYPES, RECURRENCES, formatDate, label, money, today, useApi } from '../util.js';

const EMPTY = {
  label: '',
  type: 'UTILITIES',
  amountDue: '',
  dueDate: today(),
  recurrence: 'MONTHLY',
  downPayment: '',
};

export default function Bills() {
  const { data: bills, error, reload } = useApi('/bills');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const set = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setFormError('');
    const body = {
      label: form.label,
      type: form.type,
      amountDue: form.amountDue,
      dueDate: form.dueDate,
      recurrence: form.recurrence,
      downPayment: form.downPayment !== '' ? form.downPayment : null,
    };
    try {
      if (editingId) await api(`/bills/${editingId}`, { method: 'PUT', body });
      else await api('/bills', { method: 'POST', body });
      setForm(EMPTY);
      setEditingId(null);
      reload();
    } catch (err) {
      setFormError(err.message);
    }
  };

  const edit = (b) => {
    setEditingId(b.id);
    setForm({
      label: b.label,
      type: b.type,
      amountDue: b.amountDue,
      dueDate: b.nextDueDate,
      recurrence: b.recurrence,
      downPayment: b.downPayment ?? '',
    });
  };

  /** One request per bill at a time, so repeated clicks can't stack up. */
  const action = async (b, path, method) => {
    if (busyId) return;
    setBusyId(b.id);
    setFormError('');
    try {
      await api(path, { method });
      await reload();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setBusyId(null);
    }
  };

  const undo = (b) => {
    if (window.confirm(`Undo the ${money(b.amountDue)} payment for "${b.label}" made ${formatDate(b.lastPaidDate)}?`)) {
      action(b, `/bills/${b.id}/undo-payment`, 'POST');
    }
  };

  const remove = (b) => {
    if (window.confirm(`Delete the bill "${b.label}"? Payments already made stay in your costs.`)) {
      action(b, `/bills/${b.id}`, 'DELETE');
    }
  };

  return (
    <div className="stack-lg">
      <h1>Bills</h1>

      <form className="card stack" onSubmit={submit}>
        <h2>{editingId ? 'Edit bill' : 'Add a bill'}</h2>
        <div className="row">
          <label>
            Label
            <input required value={form.label} onChange={set('label')} placeholder="e.g. Electricity" />
          </label>
          <label>
            Type
            <select value={form.type} onChange={set('type')}>
              {BILL_TYPES.map((t) => (
                <option key={t} value={t}>
                  {label(t)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="row">
          <label>
            Amount due
            <input required type="number" min="0.01" step="0.01" value={form.amountDue} onChange={set('amountDue')} />
          </label>
          <label>
            {editingId ? 'Next due date' : 'First due date'}
            <input required type="date" value={form.dueDate} onChange={set('dueDate')} />
          </label>
          <label>
            Repeats
            <select value={form.recurrence} onChange={set('recurrence')}>
              {RECURRENCES.map((r) => (
                <option key={r} value={r}>
                  {label(r)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Down payment <span className="muted">(optional)</span>
            <input type="number" min="0" step="0.01" value={form.downPayment} onChange={set('downPayment')} />
          </label>
        </div>
        <p className="muted small">A down payment is recorded once as a large-purchase cost.</p>
        {formError && <p className="error">{formError}</p>}
        <div className="actions">
          <button type="submit">{editingId ? 'Save changes' : 'Add bill'}</button>
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
        <h2>Your bills</h2>
        <p className="muted small">
          Each bill can be marked paid once per billing period. Made a mistake? Use Undo to reverse the last payment.
        </p>
        {error && <p className="error">{error}</p>}
        {bills?.length === 0 && <p className="muted">No bills yet.</p>}
        {bills?.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Label</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Next due</th>
                  <th>Repeats</th>
                  <th>Down payment</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {bills.map((b) => (
                  <tr key={b.id}>
                    <td>{b.label}</td>
                    <td>{label(b.type)}</td>
                    <td className="num">{money(b.amountDue)}</td>
                    <td className={b.overdue ? 'negative' : ''}>
                      {formatDate(b.nextDueDate)}
                      {b.overdue && ' (overdue)'}
                    </td>
                    <td>{label(b.recurrence)}</td>
                    <td className="num">{b.downPayment != null ? money(b.downPayment) : '—'}</td>
                    <td className="small">
                      {b.lastPaidDate ? (
                        <>
                          Paid {formatDate(b.lastPaidDate)}
                          {!b.canPay && <div className="muted">Next from {formatDate(b.payableFrom)}</div>}
                        </>
                      ) : (
                        <span className="muted">Not paid yet</span>
                      )}
                    </td>
                    <td className="actions">
                      <button
                        className="small-button"
                        disabled={!b.canPay || busyId === b.id}
                        title={b.canPay ? '' : `Already paid for this period. Next payment from ${formatDate(b.payableFrom)}.`}
                        onClick={() => action(b, `/bills/${b.id}/pay`, 'POST')}
                      >
                        {b.canPay ? 'Mark paid' : 'Paid ✓'}
                      </button>
                      {b.lastPaidDate && (
                        <button className="small-button secondary" disabled={busyId === b.id} onClick={() => undo(b)}>
                          Undo
                        </button>
                      )}
                      <button className="small-button secondary" disabled={busyId === b.id} onClick={() => edit(b)}>
                        Edit
                      </button>
                      <button className="small-button danger" disabled={busyId === b.id} onClick={() => remove(b)}>
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
