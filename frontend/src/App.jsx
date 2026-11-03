import { Navigate, Route, Routes } from 'react-router-dom';
import { isAdmin, useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Home from './pages/Home.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Bills from './pages/Bills.jsx';
import Income from './pages/Income.jsx';
import Costs from './pages/Costs.jsx';
import Goals from './pages/Goals.jsx';
import Planner from './pages/Planner.jsx';
import Query from './pages/Query.jsx';
import Statements from './pages/Statements.jsx';
import Notifications from './pages/Notifications.jsx';
import Profile from './pages/Profile.jsx';
import Admin from './pages/Admin.jsx';

/** Everything except the home page requires a signed-in profile. */
function RequireAuth({ children, adminOnly = false }) {
  const { user, loading } = useAuth();
  if (loading) return <p className="center muted">Loading…</p>;
  if (!user) return <Navigate to="/" replace />;
  if (adminOnly && !isAdmin(user)) return <Navigate to="/app" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route
        path="/app"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="bills" element={<Bills />} />
        <Route path="income" element={<Income />} />
        <Route path="costs" element={<Costs />} />
        <Route path="goals" element={<Goals />} />
        <Route path="planner" element={<Planner />} />
        <Route path="query" element={<Query />} />
        <Route path="statements" element={<Statements />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route
          path="admin"
          element={
            <RequireAuth adminOnly>
              <Admin />
            </RequireAuth>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
