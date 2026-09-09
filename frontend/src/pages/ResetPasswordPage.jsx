import { useState } from 'react';
import { CheckCircle2, Eye, EyeOff } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { apiError, apiFetch } from '../api';
import { AuthShell, ErrorBox } from './ForgotPasswordPage';

export default function ResetPasswordPage() {
  const [params] = useSearchParams(), token = params.get('token') || '';
  const [values, setValues] = useState({ newPassword: '', confirmPassword: '' }), [error, setError] = useState(''), [done, setDone] = useState(false), [loading, setLoading] = useState(false);
  const submit = async event => {
    event.preventDefault(); setError('');
    if (!token) { setError('Liên kết đặt lại mật khẩu không hợp lệ.'); return; }
    if (values.newPassword.length < 6) { setError('Mật khẩu phải có ít nhất 6 ký tự.'); return; }
    if (values.newPassword !== values.confirmPassword) { setError('Xác nhận mật khẩu không khớp.'); return; }
    setLoading(true);
    const response = await apiFetch('/api/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, ...values }) });
    if (!response.ok) setError(await apiError(response, 'Không thể đặt lại mật khẩu'));
    else setDone(true);
    setLoading(false);
  };
  return <AuthShell title="Đặt lại mật khẩu" subtitle="Tạo mật khẩu mới cho tài khoản WorkflowBuilder">
    {done ? <div className="space-y-5 text-center"><CheckCircle2 size={48} className="mx-auto text-emerald-500"/><div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">Đặt lại mật khẩu thành công.</div><Link to="/login" className="btn-primary block w-full py-3">Đăng nhập bằng mật khẩu mới</Link></div> : <form onSubmit={submit} className="space-y-5">{error && <ErrorBox>{error}</ErrorBox>}<Password label="Mật khẩu mới" value={values.newPassword} onChange={value => setValues(current => ({ ...current, newPassword: value }))}/><Password label="Xác nhận mật khẩu" value={values.confirmPassword} onChange={value => setValues(current => ({ ...current, confirmPassword: value }))}/><button disabled={loading} className="btn-primary w-full py-3 disabled:opacity-60">{loading ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}</button></form>}
  </AuthShell>;
}
function Password({ label, value, onChange }) { const [show, setShow] = useState(false); return <label className="block"><span className="mb-1.5 block text-sm font-semibold text-slate-700">{label}</span><span className="relative block"><input required type={show ? 'text' : 'password'} autoComplete="new-password" value={value} onChange={event => onChange(event.target.value)} className="input-field pr-11" placeholder="Tối thiểu 6 ký tự"/><button type="button" onClick={() => setShow(value => !value)} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-gray-400 hover:text-orange-600" aria-label={show ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}>{show ? <EyeOff size={17}/> : <Eye size={17}/>}</button></span></label>; }
