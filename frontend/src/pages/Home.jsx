import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';

const EMPTY_REGISTER = { username: '', email: '', password: '', firstName: '', lastName: '' };

/** Lets a locked-out user ask an administrator for help. */
function SupportForm({ initialUsername, onClose }) {
  const [form, setForm] = useState({ username: initialUsername, email: '', message: '' });
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await api('/auth/support', { method: 'POST', body: form });
      setSent(true);
    } catch (err) {
      setError(err.message);
    }
  };

  if (sent) {
    return (
      <div className="stack">
        <p className="success-box">Your request was sent. An administrator will review it and unlock your account.</p>
        <button className="secondary" onClick={onClose}>
          Back to sign in
        </button>
      </div>
    );
  }

  return (
    <form className="stack" onSubmit={submit}>
      <h2>Request support</h2>
      <p className="muted small">Tell an administrator what happened. They'll contact you at the email you give.</p>
      <label>
        Username
        <input required value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
      </label>
      <label>
        Email
        <input required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
      </label>
      <label>
        Message
        <textarea
          required
          rows={4}
          maxLength={1000}
          value={form.message}
          onChange={(e) => setForm({ ...form, message: e.target.value })}
        />
      </label>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="submit">Send request</button>
        <button type="button" className="secondary" onClick={onClose}>
          Cancel
        </button>
      </div>
    </form>
  );
}

/** Public landing page with sign-in and registration. */
export default function Home() {
  const { user, signIn, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [login, setLogin] = useState({ username: '', password: '' });
  const [details, setDetails] = useState(EMPTY_REGISTER);
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setErrorCode('');
    setBusy(true);
    try {
      if (mode === 'login') await signIn(login.username, login.password);
      else await register(details);
      navigate('/app');
    } catch (err) {
      setError(err.message);
      setErrorCode(err.code ?? '');
    } finally {
      setBusy(false);
    }
  };

  const field = (state, setState, name) => ({
    value: state[name],
    onChange: (e) => setState({ ...state, [name]: e.target.value }),
  });

  return (
    <div className="home">
      <section className="hero">
        <h1>FinTrack</h1>
        <p>
          Track your bills, income and spending in one place. Get reminders before bills are due, keep a share of every
          paycheck for savings, and see how a purchase would affect your money before you make it.
        </p>
        <ul className="features">
          <li>Bill reminders and down payments</li>
          <li>Recurring and one-off income</li>
          <li>Savings limit warnings</li>
          <li>Purchase planner and savings goals</li>
          <li>Bank statement import</li>
        </ul>
      </section>

      <section className="card auth-card">
        {user ? (
          <>
            <h2>Welcome back, {user.firstName}</h2>
            <Link className="button" to="/app">
              Go to your dashboard
            </Link>
          </>
        ) : mode === 'support' ? (
          <SupportForm
            initialUsername={login.username}
            onClose={() => {
              setMode('login');
              setError('');
              setErrorCode('');
            }}
          />
        ) : (
          <>
            <div className="tabs">
              <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
                Sign in
              </button>
              <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
                Register
              </button>
            </div>
            <form onSubmit={submit} className="stack">
              {mode === 'login' ? (
                <>
                  <label>
                    Username
                    <input required autoComplete="username" {...field(login, setLogin, 'username')} />
                  </label>
                  <label>
                    Password
                    <input
                      required
                      type="password"
                      autoComplete="current-password"
                      {...field(login, setLogin, 'password')}
                    />
                  </label>
                </>
              ) : (
                <>
                  <div className="row">
                    <label>
                      First name
                      <input required {...field(details, setDetails, 'firstName')} />
                    </label>
                    <label>
                      Last name
                      <input required {...field(details, setDetails, 'lastName')} />
                    </label>
                  </div>
                  <label>
                    Username
                    <input required minLength={3} autoComplete="username" {...field(details, setDetails, 'username')} />
                  </label>
                  <label>
                    Email
                    <input required type="email" autoComplete="email" {...field(details, setDetails, 'email')} />
                  </label>
                  <label>
                    Password <span className="muted">(at least 8 characters)</span>
                    <input
                      required
                      type="password"
                      minLength={8}
                      autoComplete="new-password"
                      {...field(details, setDetails, 'password')}
                    />
                  </label>
                </>
              )}
              {error && (
                <p className={errorCode === 'TEMPORARILY_LOCKED' ? 'warning' : 'error'} role="alert">
                  {error}
                </p>
              )}
              {errorCode === 'ACCOUNT_LOCKED' && (
                <button type="button" className="secondary" onClick={() => setMode('support')}>
                  Request support
                </button>
              )}
              <button type="submit" disabled={busy}>
                {mode === 'login' ? 'Sign in' : 'Create account'}
              </button>
              {mode === 'login' && errorCode !== 'ACCOUNT_LOCKED' && (
                <button type="button" className="link-button small" onClick={() => setMode('support')}>
                  Locked out or need help? Request support
                </button>
              )}
            </form>
          </>
        )}
      </section>
    </div>
  );
}
