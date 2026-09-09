import { useState } from 'react';
import { Eye, EyeOff, Share2 } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim(), password })
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Email hoặc mật khẩu không đúng.');
        }
        if (response.status >= 500) {
          throw new Error('Không thể kết nối tới backend. Hãy kiểm tra backend đang chạy ở cổng 8081.');
        }

        const body = await response.json().catch(() => null);
        throw new Error(body?.message || 'Đăng nhập thất bại. Vui lòng thử lại.');
      }

      const data = await response.json();
      login({
        id: data.userId,
        email: data.email,
        displayName: data.displayName,
        systemRoles: data.systemRoles || []
      }, data.token);
      navigate('/dashboard');
    } catch (err) {
      const message = err instanceof TypeError
        ? 'Không thể kết nối tới backend. Hãy kiểm tra backend đang chạy ở cổng 8081.'
        : err.message;
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-grayLight flex flex-col justify-center items-center p-4">
      <div className="w-full max-w-md bg-white rounded-xl shadow-lg border border-grayBorder p-8">
        <div className="w-14 h-14 bg-primary rounded-xl flex items-center justify-center text-white mx-auto mb-4 shadow-md">
          <Share2 size={28} />
        </div>
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-slate-800">WorkflowBuilder</h1>
          <p className="text-gray-500 text-sm mt-1">Hệ thống quản lý quy trình doanh nghiệp</p>
        </div>

        {error && (
          <div role="alert" className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-5">
          <div>
            <label htmlFor="email" className="block text-sm font-semibold text-gray-700 mb-1.5">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="username@company.com"
              autoComplete="username"
              className="input-field"
              required
            />
          </div>

          <div>
            <div className="mb-1.5 flex items-center justify-between"><label htmlFor="password" className="block text-sm font-semibold text-gray-700">Mật khẩu</label><Link to="/forgot-password" className="text-xs font-semibold text-orange-600 hover:text-orange-700">Quên mật khẩu?</Link></div>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                autoComplete="current-password"
                className="input-field pr-10"
                required
              />
              <button
                type="button"
                aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                onClick={() => setShowPassword(!showPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full btn-primary py-3 text-base disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>
      </div>
    </div>
  );
}
