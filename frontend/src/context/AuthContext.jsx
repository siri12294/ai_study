import React, { createContext, useContext, useEffect, useState } from 'react';
import { api, setToken, getStoredToken } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem('asc_user');
    if (stored && getStoredToken()) {
      setUser(JSON.parse(stored));
    }
    setLoading(false);
  }, []);

  function applySession(auth) {
    setToken(auth.token);
    const u = { id: auth.userId, email: auth.email, displayName: auth.displayName, role: auth.role };
    localStorage.setItem('asc_user', JSON.stringify(u));
    setUser(u);
  }

  async function login(email, password) {
    const auth = await api.login({ email, password });
    applySession(auth);
  }

  async function register(email, displayName, password) {
    const auth = await api.register({ email, displayName, password });
    applySession(auth);
  }

  function logout() {
    setToken(null);
    localStorage.removeItem('asc_user');
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
