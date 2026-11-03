import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, getToken, setToken } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(getToken()));

  useEffect(() => {
    if (!getToken()) return;
    api('/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const onSignedOut = () => setUser(null);
    window.addEventListener('fintrack:signed-out', onSignedOut);
    return () => window.removeEventListener('fintrack:signed-out', onSignedOut);
  }, []);

  const signIn = useCallback(async (username, password) => {
    const res = await api('/auth/login', { method: 'POST', body: { username, password } });
    setToken(res.token);
    setUser(res.user);
  }, []);

  const register = useCallback(async (details) => {
    const res = await api('/auth/register', { method: 'POST', body: details });
    setToken(res.token);
    setUser(res.user);
  }, []);

  const signOut = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const value = { user, setUser, loading, signIn, register, signOut };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

export const isAdmin = (user) => user?.role === 'ADMIN' || user?.role === 'OWNER';
