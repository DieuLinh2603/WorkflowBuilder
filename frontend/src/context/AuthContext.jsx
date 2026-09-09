import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('wf_user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = (userData, token) => {
    setUser(userData);
    localStorage.setItem('wf_user', JSON.stringify(userData));
    if (token) localStorage.setItem('wf_token', token);
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('wf_user');
    localStorage.removeItem('wf_token');
  };

  useEffect(() => {
    const handleUnauthorized = () => setUser(null);
    window.addEventListener('wf:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('wf:unauthorized', handleUnauthorized);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user, hasRole: (role) => !!user?.systemRoles?.includes(role) }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
