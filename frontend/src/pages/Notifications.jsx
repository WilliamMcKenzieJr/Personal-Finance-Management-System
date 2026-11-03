import { api } from '../api.js';
import { formatDateTime, label, useApi } from '../util.js';

export default function Notifications() {
  const { data: notifications, error, reload } = useApi('/notifications');

  const act = (path, method = 'POST') => api(path, { method }).then(reload);

  return (
    <div className="stack-lg">
      <div className="section-head">
        <h1>Notifications</h1>
        {notifications?.some((n) => !n.read) && (
          <button className="secondary" onClick={() => act('/notifications/read-all')}>
            Mark all as read
          </button>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      {notifications?.length === 0 && <p className="muted">You're all caught up.</p>}
      <ul className="list card">
        {notifications?.map((n) => (
          <li key={n.id} className={n.read ? 'faded' : 'unread'}>
            <div className="grow">
              <span className={`pill type-${n.type.toLowerCase()}`}>{label(n.type)}</span>
              <div>{n.message}</div>
              <div className="muted small">{formatDateTime(n.createdAt)}</div>
            </div>
            {!n.read && (
              <button className="small-button secondary" onClick={() => act(`/notifications/${n.id}/read`)}>
                Mark read
              </button>
            )}
            <button className="small-button danger" onClick={() => act(`/notifications/${n.id}`, 'DELETE')}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
