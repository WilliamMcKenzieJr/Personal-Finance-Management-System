import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { formatDateTime, label, useApi } from '../util.js';

export default function Profile() {
  const { user, setUser } = useAuth();
  const { data: requests, reload } = useApi('/me/change-requests');
  const [percent, setPercent] = useState(String(Number(user.savingsPercent)));
  const [change, setChange] = useState({ firstName: '', lastName: '', email: '', note: '' });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });
  const [status, setStatus] = useState({});

  const run = async (key, fn) => {
    setStatus({ [key]: { pending: true } });
    try {
      await fn();
      setStatus({ [key]: { ok: 'Saved.' } });
    } catch (err) {
      setStatus({ [key]: { error: err.message } });
    }
  };

  const Status = ({ name }) =>
    status[name]?.error ? (
      <p className="error">{status[name].error}</p>
    ) : status[name]?.ok ? (
      <p className="success-text">{status[name].ok}</p>
    ) : null;

  const hasPending = requests?.some((r) => r.status === 'PENDING');

  return (
    <div className="stack-lg">
      <h1>Your profile</h1>

      <section className="card">
        <dl className="details">
          <dt>Name</dt>
          <dd>
            {user.firstName} {user.lastName}
          </dd>
          <dt>Username</dt>
          <dd>{user.username}</dd>
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Role</dt>
          <dd>{label(user.role)}</dd>
        </dl>
      </section>

      <form
        className="card stack"
        onSubmit={(e) => {
          e.preventDefault();
          run('savings', async () => setUser(await api('/me/savings', { method: 'PUT', body: { percent } })));
        }}
      >
        <h2>Monthly savings</h2>
        <p className="muted small">
          The share of each month's income you want to keep. If your costs rise above the rest, you'll be warned.
        </p>
        <div className="inline-form">
          <input
            type="number"
            min="0"
            max="100"
            step="0.5"
            value={percent}
            onChange={(e) => setPercent(e.target.value)}
            aria-label="Savings percentage"
          />
          <span>% of income</span>
          <button type="submit">Save</button>
        </div>
        <Status name="savings" />
      </form>

      <form
        className="card stack"
        onSubmit={(e) => {
          e.preventDefault();
          run('change', async () => {
            await api('/me/change-requests', { method: 'POST', body: change });
            setChange({ firstName: '', lastName: '', email: '', note: '' });
            reload();
          });
        }}
      >
        <h2>Request a profile change</h2>
        <p className="muted small">An administrator reviews profile changes. Fill in only what you want to change.</p>
        {hasPending ? (
          <p className="muted">You have a request waiting for review.</p>
        ) : (
          <>
            <div className="row">
              <label>
                New first name
                <input value={change.firstName} onChange={(e) => setChange({ ...change, firstName: e.target.value })} />
              </label>
              <label>
                New last name
                <input value={change.lastName} onChange={(e) => setChange({ ...change, lastName: e.target.value })} />
              </label>
            </div>
            <label>
              New email
              <input type="email" value={change.email} onChange={(e) => setChange({ ...change, email: e.target.value })} />
            </label>
            <label>
              Note for the administrator <span className="muted">(optional)</span>
              <textarea rows={2} value={change.note} onChange={(e) => setChange({ ...change, note: e.target.value })} />
            </label>
            <div className="actions">
              <button type="submit">Send request</button>
            </div>
          </>
        )}
        <Status name="change" />
        {requests?.length > 0 && (
          <ul className="list">
            {requests.map((r) => (
              <li key={r.id}>
                <div className="grow">
                  <div>
                    {[r.firstName && `First name → ${r.firstName}`, r.lastName && `Last name → ${r.lastName}`,
                      r.email && `Email → ${r.email}`].filter(Boolean).join(', ')}
                  </div>
                  <div className="muted small">
                    Sent {formatDateTime(r.createdAt)}
                    {r.reviewNote && ` · "${r.reviewNote}"`}
                  </div>
                </div>
                <span className={`pill status-${r.status.toLowerCase()}`}>{label(r.status)}</span>
              </li>
            ))}
          </ul>
        )}
      </form>

      <form
        className="card stack"
        onSubmit={(e) => {
          e.preventDefault();
          run('password', async () => {
            await api('/me/password', { method: 'PUT', body: passwords });
            setPasswords({ currentPassword: '', newPassword: '' });
          });
        }}
      >
        <h2>Change password</h2>
        <div className="row">
          <label>
            Current password
            <input
              type="password"
              required
              autoComplete="current-password"
              value={passwords.currentPassword}
              onChange={(e) => setPasswords({ ...passwords, currentPassword: e.target.value })}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={passwords.newPassword}
              onChange={(e) => setPasswords({ ...passwords, newPassword: e.target.value })}
            />
          </label>
        </div>
        <div className="actions">
          <button type="submit">Change password</button>
        </div>
        <Status name="password" />
      </form>
    </div>
  );
}
