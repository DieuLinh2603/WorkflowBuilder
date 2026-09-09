import { useState } from 'react';
import { Eye, EyeOff, KeyRound, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';

export default function ChangePasswordModal({ open, onClose, onChanged }) {
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [error, setError] = useState(''), [saving, setSaving] = useState(false);
  if (!open) return null;
  const update = (field, value) => { setValues(current => ({ ...current, [field]: value })); setError(''); };
  const submit = async event => {
    event.preventDefault();
    if (values.newPassword.length < 6) { setError('Mật khẩu mới phải có ít nhất 6 ký tự.'); return; }
    if (values.newPassword !== values.confirmPassword) { setError('Xác nhận mật khẩu không khớp.'); return; }
    setSaving(true);
    const response = await apiFetch('/api/auth/change-password', { method: 'POST', body: JSON.stringify(values) });
    if (!response.ok) { setError(await apiError(response, 'Không thể đổi mật khẩu')); setSaving(false); return; }
    window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.' } }));
    onChanged();
  };
  return <div className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/50 p-4" onMouseDown={event => event.target === event.currentTarget && !saving && onClose()}>
    <form onSubmit={submit} className="w-full max-w-[480px] overflow-hidden rounded-2xl bg-white shadow-2xl">
      <header className="flex items-center gap-3 border-b border-slate-100 px-6 py-5"><span className="flex h-11 w-11 items-center justify-center rounded-full bg-orange-50 text-orange-500"><KeyRound size={21}/></span><div className="flex-1"><h2 className="text-lg font-bold text-slate-800">Đổi mật khẩu</h2><p className="text-xs text-gray-500">Bạn sẽ cần đăng nhập lại sau khi thay đổi</p></div><button type="button" disabled={saving} onClick={onClose} className="rounded-lg p-2 text-gray-400 hover:bg-slate-100"><X size={18}/></button></header>
      <div className="space-y-4 px-6 py-5">{error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}<PasswordField label="Mật khẩu hiện tại" value={values.currentPassword} onChange={value => update('currentPassword', value)} autoComplete="current-password"/><PasswordField label="Mật khẩu mới" value={values.newPassword} onChange={value => update('newPassword', value)} hint="Tối thiểu 6 ký tự"/><PasswordField label="Xác nhận mật khẩu mới" value={values.confirmPassword} onChange={value => update('confirmPassword', value)}/></div>
      <footer className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4"><button type="button" disabled={saving} onClick={onClose} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600">Hủy</button><button type="submit" disabled={saving || !values.currentPassword || !values.newPassword || !values.confirmPassword} className="rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-orange-600 disabled:opacity-50">{saving ? 'Đang đổi...' : 'Đổi mật khẩu'}</button></footer>
    </form>
  </div>;
}

function PasswordField({ label, value, onChange, hint, autoComplete = 'new-password' }) { const [visible, setVisible] = useState(false); return <label className="block"><span className="mb-1.5 block text-sm font-semibold text-slate-700">{label}</span><span className="relative block"><input required type={visible ? 'text' : 'password'} autoComplete={autoComplete} value={value} onChange={event => onChange(event.target.value)} className="input-field pr-11" placeholder={hint || 'Nhập mật khẩu'}/><button type="button" onClick={() => setVisible(show => !show)} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-gray-400 hover:text-orange-600" aria-label={visible ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}>{visible ? <EyeOff size={17}/> : <Eye size={17}/>}</button></span>{hint && <span className="mt-1 block text-[11px] text-gray-400">{hint}</span>}</label>; }
