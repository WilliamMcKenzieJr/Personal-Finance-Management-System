import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { isAdmin, useAuth } from '../auth.jsx';
import { label } from '../util.js';

const LINKS = [
  ['/app', 'Dashboard'],
  ['/app/bills', 'Bills'],
  ['/app/income', 'Income'],
  ['/app/costs', 'Costs'],
  ['/app/goals', 'Goals'],
  ['/app/planner', 'Planner'],
  ['/app/query', 'Search'],
  ['/app/statements', 'Bank statements'],
];

export default function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);

  // Refresh the unread badge on every page change.
  useEffect(() => {
    setMenuOpen(false);
    api('/notifications')
      .then((list) => setUnread(list.filter((n) => !n.read).length))
      .catch(() => {});
  }, [location.pathname]);

  const handleSignOut = () => {
    signOut();
    navigate('/');
  };

  return (
    <div className="shell">
      <header className="topbar">
        <button className="menu-button" onClick={() => setMenuOpen((o) => !o)} aria-label="Menu">
          ☰
        </button>
        <span className="brand">FinTrack</span>
        <div className="topbar-right">
          <NavLink to="/app/notifications" className="bell">
            Notifications{unread > 0 && <span className="badge">{unread}</span>}
          </NavLink>
          <NavLink to="/app/profile" className="who">
            {user.firstName} <span className="role-tag">{label(user.role)}</span>
          </NavLink>
          <button className="link-button" onClick={handleSignOut}>
            Sign out
          </button>
        </div>
      </header>
      <nav className={`sidebar ${menuOpen ? 'open' : ''}`}>
        {LINKS.map(([to, text]) => (
          <NavLink key={to} to={to} end={to === '/app'}>
            {text}
          </NavLink>
        ))}
        <NavLink to="/app/profile">Profile</NavLink>
        {isAdmin(user) && <NavLink to="/app/admin">Administration</NavLink>}
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
