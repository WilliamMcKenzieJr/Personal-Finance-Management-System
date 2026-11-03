import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { formatDate, label, money, useApi } from '../util.js';

export default function Dashboard() {
  const { data, error, reload } = useApi('/dashboard');
  const [actionError, setActionError] = useState('');
  const [busyId, setBusyId] = useState(null);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">Loading…</p>;

  const { month } = data;
  const limit = Number(month.spendingLimit);
  const usedPct = limit > 0 ? Math.min(100, (Number(month.spent) / limit) * 100) : month.spent > 0 ? 100 : 0;
  const maxCategory = Math.max(1, ...Object.values(month.spentByCategory).map(Number));

  const pay = async (id) => {
    if (busyId) return;
    setBusyId(id);
    setActionError('');
    try {
      await api(`/bills/${id}/pay`, { method: 'POST' });
      await reload();
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="stack-lg">
      <h1>Dashboard</h1>

      <div className="stats">
        <div className="card stat">
          <span className="muted">Total money</span>
          <strong className={Number(data.balance) < 0 ? 'negative' : ''}>{money(data.balance)}</strong>
        </div>
        <div className="card stat">
          <span className="muted">All income to date</span>
          <strong>{money(data.totalIncome)}</strong>
        </div>
        <div className="card stat">
          <span className="muted">All costs to date</span>
          <strong>{money(data.totalCosts)}</strong>
        </div>
      </div>

      <section className="card">
        <h2>This month</h2>
        {month.overLimit && (
          <p className="warning">
            Your costs are above your spending limit and are cutting into the {Number(month.savingsPercent)}% you set
            aside for savings.
          </p>
        )}
        <div className="month-grid">
          <div>
            <span className="muted">Income</span>
            <div>{money(month.income)}</div>
          </div>
          <div>
            <span className="muted">Spent</span>
            <div>{money(month.spent)}</div>
          </div>
          <div>
            <span className="muted">Savings ({Number(month.savingsPercent)}%)</span>
            <div>{money(month.savingsTarget)}</div>
          </div>
          <div>
            <span className="muted">Left before limit</span>
            <div className={Number(month.remainingBeforeLimit) < 0 ? 'negative' : ''}>
              {money(month.remainingBeforeLimit)}
            </div>
          </div>
        </div>
        <div className="meter" title={`${usedPct.toFixed(0)}% of spending limit`}>
          <div className={`meter-fill ${month.overLimit ? 'over' : ''}`} style={{ width: `${usedPct}%` }} />
        </div>
        {Number(month.savingsPercent) === 0 && (
          <p className="muted small">
            Set a savings percentage on your <Link to="/app/profile">profile</Link> to get warnings when spending gets
            too high.
          </p>
        )}

        <h3>Spending by category</h3>
        <div className="bars">
          {Object.entries(month.spentByCategory).map(([category, amount]) => (
            <div key={category} className="bar-row">
              <span>{label(category)}</span>
              <div className="bar">
                <div style={{ width: `${(Number(amount) / maxCategory) * 100}%` }} />
              </div>
              <span className="num">{money(amount)}</span>
            </div>
          ))}
        </div>
      </section>

      <div className="two-col">
        <section className="card">
          <h2>Bills due in the next 2 weeks</h2>
          {actionError && <p className="error">{actionError}</p>}
          {data.upcomingBills.length === 0 ? (
            <p className="muted">Nothing due soon.</p>
          ) : (
            <ul className="list">
              {data.upcomingBills.map((b) => (
                <li key={b.id}>
                  <div>
                    <strong>{b.label}</strong>
                    <div className={b.overdue ? 'negative small' : 'muted small'}>
                      {b.overdue ? 'Overdue since ' : 'Due '}
                      {formatDate(b.nextDueDate)}
                    </div>
                  </div>
                  <span className="num">{money(b.amountDue)}</span>
                  {b.canPay ? (
                    <button className="small-button" disabled={busyId === b.id} onClick={() => pay(b.id)}>
                      Mark paid
                    </button>
                  ) : (
                    <span className="pill success">Paid</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card">
          <h2>Goals</h2>
          {data.goals.length === 0 ? (
            <p className="muted">
              No goals yet. <Link to="/app/goals">Create one</Link>.
            </p>
          ) : (
            <ul className="list">
              {data.goals.map((g) => (
                <li key={g.id} className="goal-mini">
                  <div className="grow">
                    <strong>{g.name}</strong>
                    {g.availableToPurchase && <span className="pill success">Ready to buy</span>}
                    <div className="meter thin">
                      <div className="meter-fill" style={{ width: `${g.progressPercent}%` }} />
                    </div>
                  </div>
                  <span className="num">
                    {money(g.savedAmount)} / {money(g.targetAmount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
