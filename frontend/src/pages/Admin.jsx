import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { formatDateTime, label, useApi } from '../util.js';

const TABS = [
  ['users', 'Users'],
  ['requests', 'Profile requests'],
  ['support', 'Support requests'],
  ['audit', 'Profile change log'],
  ['logins', 'Sign-in log'],
];

const LOGIN_OUTCOMES = ['SUCCESS', 'WRONG_PASSWORD', 'UNKNOWN_USER'];

/**
 * Profile administration. Admins manage members; the owner also manages admins and decides
 * who is an admin. Financial records are never shown here.
 */
export default function Admin() {
  const { user: me } = useAuth();
  const [tab, setTab] = useState('users');
  const users = useApi('/admin/users');
  const requests = useApi('/admin/change-requests?status=PENDING');
  const support = useApi('/admin/support-requests?status=OPEN');
  const [error, setError] = useState('');

  const isOwner = me.role === 'OWNER';
  const canManage = (u) =>
    isOwner ? u.role !== 'OWNER' || u.id === me.id : me.role === 'ADMIN' && u.role === 'MEMBER';

  const run = async (fn) => {
    setError('');
    try {
      await fn();
      users.reload();
      requests.reload();
      support.reload();
    } catch (err) {
      setError(err.message);
    }
  };

  const unlock = (userId, username) => {
    if (window.confirm(`Unlock ${username}'s account?`)) {
      run(() => api(`/admin/users/${userId}/unlock`, { method: 'POST' }));
    }
  };

  const count = (list) => (list?.length ? ` (${list.length})` : '');

  return (
    <div className="stack-lg">
      <h1>Administration</h1>
      <div className="tabs admin-tabs">
        {TABS.map(([key, text]) => (
          <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>
            {text}
            {key === 'requests' && count(requests.data)}
            {key === 'support' && count(support.data)}
          </button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}

      {tab === 'users' && <UsersTab users={users} me={me} isOwner={isOwner} canManage={canManage} run={run} unlock={unlock} />}
      {tab === 'requests' && <RequestsTab requests={requests} run={run} />}
      {tab === 'support' && <SupportTab support={support} run={run} unlock={unlock} />}
      {tab === 'audit' && <AuditTab />}
      {tab === 'logins' && <LoginsTab />}
    </div>
  );
}

function LockPill({ status }) {
  if (status === 'LOCKED') return <span className="pill status-rejected">Locked</span>;
  if (status === 'TEMPORARY') return <span className="pill status-pending">Locked 10 min</span>;
  return null;
}

function UsersTab({ users, me, isOwner, canManage, run, unlock }) {
  const [editing, setEditing] = useState(null);

  const saveEdit = (e) => {
    e.preventDefault();
    const { id, firstName, lastName, email } = editing;
    run(async () => {
      await api(`/admin/users/${id}`, { method: 'PUT', body: { firstName, lastName, email } });
      setEditing(null);
    });
  };

  const remove = (u) => {
    if (window.confirm(`Delete ${u.username}'s account and all of their data? This can't be undone.`)) {
      run(() => api(`/admin/users/${u.id}`, { method: 'DELETE' }));
    }
  };

  const setRole = (u, role) => run(() => api(`/owner/users/${u.id}/role`, { method: 'PUT', body: { role } }));

  const lock = (u) => {
    const note = window.prompt(
      `Lock ${u.username}'s account? They'll be signed out and can't sign in until unlocked. Reason (optional):`,
      '',
    );
    if (note === null) return;
    run(() => api(`/admin/users/${u.id}/lock`, { method: 'POST', body: { note } }));
  };
  const canLock = (u) => canManage(u) && u.id !== me.id && u.role !== 'OWNER' && u.lockStatus !== 'LOCKED';

  return (
    <>
      {editing && (
        <form className="card stack" onSubmit={saveEdit}>
          <h2>Edit {editing.username}</h2>
          <div className="row">
            <label>
              First name
              <input required value={editing.firstName} onChange={(e) => setEditing({ ...editing, firstName: e.target.value })} />
            </label>
            <label>
              Last name
              <input required value={editing.lastName} onChange={(e) => setEditing({ ...editing, lastName: e.target.value })} />
            </label>
            <label>
              Email
              <input
                required
                type="email"
                value={editing.email}
                onChange={(e) => setEditing({ ...editing, email: e.target.value })}
              />
            </label>
          </div>
          <div className="actions">
            <button type="submit">Save</button>
            <button type="button" className="secondary" onClick={() => setEditing(null)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <section className="card">
        {users.error && <p className="error">{users.error}</p>}
        {users.data && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Username</th>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.data.map((u) => (
                  <tr key={u.id}>
                    <td className="num">{u.id}</td>
                    <td>
                      {u.username} <LockPill status={u.lockStatus} />
                    </td>
                    <td>
                      {u.firstName} {u.lastName}
                    </td>
                    <td>{u.email}</td>
                    <td>
                      <span className={`pill role-${u.role.toLowerCase()}`}>{label(u.role)}</span>
                    </td>
                    <td className="actions">
                      {u.lockStatus !== 'NONE' && canManage(u) && (
                        <button className="small-button" onClick={() => unlock(u.id, u.username)}>
                          Unlock
                        </button>
                      )}
                      {canLock(u) && (
                        <button className="small-button danger" onClick={() => lock(u)}>
                          Lock
                        </button>
                      )}
                      {isOwner && u.role === 'MEMBER' && (
                        <button className="small-button" onClick={() => setRole(u, 'ADMIN')}>
                          Make admin
                        </button>
                      )}
                      {isOwner && u.role === 'ADMIN' && (
                        <button className="small-button secondary" onClick={() => setRole(u, 'MEMBER')}>
                          Remove admin
                        </button>
                      )}
                      {canManage(u) && (
                        <button className="small-button secondary" onClick={() => setEditing(u)}>
                          Edit
                        </button>
                      )}
                      {canManage(u) && u.id !== me.id && (
                        <button className="small-button danger" onClick={() => remove(u)}>
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function RequestsTab({ requests, run }) {
  const review = (r, approve) => {
    const note = window.prompt(`${approve ? 'Approve' : 'Reject'} this request? Optional note to ${r.username}:`, '');
    if (note === null) return;
    run(() => api(`/admin/change-requests/${r.id}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: { note } }));
  };

  return (
    <section className="card">
      {requests.data?.length === 0 && <p className="muted">No pending requests.</p>}
      {requests.data?.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>User</th>
                <th>Requested changes</th>
                <th>Note</th>
                <th>Sent</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {requests.data.map((r) => (
                <tr key={r.id}>
                  <td>{r.username}</td>
                  <td>
                    {r.firstName && <div>First name: {r.currentFirstName} → {r.firstName}</div>}
                    {r.lastName && <div>Last name: {r.currentLastName} → {r.lastName}</div>}
                    {r.email && <div>Email: {r.currentEmail} → {r.email}</div>}
                  </td>
                  <td>{r.note}</td>
                  <td>{formatDateTime(r.createdAt)}</td>
                  <td className="actions">
                    <button className="small-button" onClick={() => review(r, true)}>
                      Approve
                    </button>
                    <button className="small-button danger" onClick={() => review(r, false)}>
                      Reject
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SupportTab({ support, run, unlock }) {
  return (
    <section className="card">
      {support.data?.length === 0 && <p className="muted">No open support requests.</p>}
      {support.data?.length > 0 && (
        <ul className="list">
          {support.data.map((s) => (
            <li key={s.id} className="support-item">
              <div className="grow">
                <div>
                  <strong>{s.username}</strong> <span className="muted">&lt;{s.email}&gt;</span>{' '}
                  {s.matchingUserId == null ? (
                    <span className="pill">No such account</span>
                  ) : (
                    <LockPill status={s.matchingUserLockStatus} />
                  )}
                </div>
                <div className="pre-wrap">{s.message}</div>
                <div className="muted small">
                  {formatDateTime(s.createdAt)} · from {s.ipAddress}
                </div>
              </div>
              <div className="actions">
                {s.matchingUserId != null && s.matchingUserLockStatus !== 'NONE' && (
                  <button className="small-button" onClick={() => unlock(s.matchingUserId, s.username)}>
                    Unlock account
                  </button>
                )}
                <button
                  className="small-button secondary"
                  onClick={() => run(() => api(`/admin/support-requests/${s.id}/resolve`, { method: 'POST' }))}
                >
                  Mark resolved
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AuditTab() {
  const [username, setUsername] = useState('');
  const [query, setQuery] = useState('');
  const { data, error } = useApi(`/admin/audit-log${query ? `?username=${encodeURIComponent(query)}` : ''}`);

  return (
    <section className="card stack">
      <form
        className="inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(username.trim());
        }}
      >
        <input placeholder="Filter by username" value={username} onChange={(e) => setUsername(e.target.value)} />
        <button type="submit">Filter</button>
      </form>
      {error && <p className="error">{error}</p>}
      {data?.length === 0 && <p className="muted">No entries.</p>}
      {data?.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>User</th>
                <th>Change</th>
                <th>Details</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {data.map((a) => (
                <tr key={a.id}>
                  <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                  <td>
                    {a.targetUsername} <span className="muted small">#{a.targetUserId}</span>
                  </td>
                  <td>{label(a.action)}</td>
                  <td>{a.details}</td>
                  <td>{a.actorUsername ?? <span className="muted">system</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function LoginsTab() {
  const [filters, setFilters] = useState({ username: '', outcome: '' });
  const [query, setQuery] = useState('');
  const { data, error, reload } = useApi(`/admin/login-attempts${query}`);

  const apply = (e) => {
    e.preventDefault();
    const qs = new URLSearchParams();
    if (filters.username.trim()) qs.set('username', filters.username.trim());
    if (filters.outcome) qs.set('outcome', filters.outcome);
    setQuery(qs.toString() ? `?${qs}` : '');
  };

  return (
    <section className="card stack">
      <form className="inline-form wrap" onSubmit={apply}>
        <input
          placeholder="Filter by username"
          value={filters.username}
          onChange={(e) => setFilters({ ...filters, username: e.target.value })}
        />
        <select value={filters.outcome} onChange={(e) => setFilters({ ...filters, outcome: e.target.value })}>
          <option value="">All results</option>
          {LOGIN_OUTCOMES.map((o) => (
            <option key={o} value={o}>
              {label(o)}
            </option>
          ))}
        </select>
        <button type="submit">Filter</button>
        <button type="button" className="secondary" onClick={reload}>
          Refresh
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      {data?.length === 0 && <p className="muted">No sign-in attempts.</p>}
      {data?.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Username entered</th>
                <th>Result</th>
                <th>IP address</th>
                <th>Details</th>
                <th>Browser</th>
              </tr>
            </thead>
            <tbody>
              {data.map((a) => (
                <tr key={a.id}>
                  <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                  <td>{a.usernameEntered}</td>
                  <td>
                    <span className={`pill outcome-${a.outcome.toLowerCase()}`}>{label(a.outcome)}</span>
                  </td>
                  <td className="nowrap">{a.ipAddress}</td>
                  <td>{a.detail}</td>
                  <td className="muted small ua" title={a.userAgent}>
                    {a.userAgent}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
