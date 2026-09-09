import { useState } from 'react';
import { ArrowLeft, Mail, Share2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { apiError, apiFetch } from '../api';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState(''), [error, setError] = useState(''), [result, setResult] = useState(null), [loading, setLoading] = useState(false);
  const submit = async event => {
    event.preventDefault(); setError(''); setLoading(true);
    const response = await apiFetch('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email: email.trim() }) });
    if (!response.ok) setError(await apiError(response, 'Không thể gửi yêu cầu đặt lại mật khẩu'));
    else setResult(await response.json());
    setLoading(false);
  };
  return <AuthShell title="Quên mật khẩu" subtitle="Nhập email tài khoản để nhận liên kết đặt lại mật khẩu">
    {result ? <div className="space-y-4"><div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-700">{result.message}</div>{result.developmentResetUrl && <div className="rounded-xl border border-amber-200 bg-amber-50 p-4"><p className="text-xs leading-5 text-amber-700">SMTP chưa được cấu hình. Link dưới đây chỉ xuất hiện trong môi trường development.</p><a href={result.developmentResetUrl} className="mt-3 block rounded-lg bg-orange-500 px-4 py-2.5 text-center text-sm font-bold text-white hover:bg-orange-600">Đặt lại mật khẩu ngay</a></div>}<Link to="/login" className="flex items-center justify-center gap-2 text-sm font-semibold text-orange-600"><ArrowLeft size={15}/> Quay lại đăng nhập</Link></div> : <form onSubmit={submit} className="space-y-5">{error && <ErrorBox>{error}</ErrorBox>}<label className="block"><span className="mb-1.5 block text-sm font-semibold text-slate-700">Email</span><div className="relative"><Mail size={17} className="absolute left-3 top-3 text-gray-400"/><input required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} className="input-field pl-10" placeholder="user@company.com"/></div></label><button disabled={loading} className="btn-primary w-full py-3 disabled:opacity-60">{loading ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}</button><Link to="/login" className="flex items-center justify-center gap-2 text-sm font-semibold text-gray-500 hover:text-orange-600"><ArrowLeft size={15}/> Quay lại đăng nhập</Link></form>}
  </AuthShell>;
}

export function AuthShell({ title, subtitle, children }) { return <div className="flex min-h-screen items-center justify-center bg-grayLight p-4"><div className="w-full max-w-md rounded-2xl border border-grayBorder bg-white p-8 shadow-xl"><div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-xl bg-primary text-white shadow-md"><Share2 size={28}/></div><div className="mb-7 text-center"><h1 className="text-2xl font-bold text-slate-800">{title}</h1><p className="mt-2 text-sm leading-5 text-gray-500">{subtitle}</p></div>{children}</div></div>; }
export function ErrorBox({ children }) { return <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600">{children}</div>; }
