import { useEffect, useMemo, useState } from 'react';
import { Edit2, Eye, EyeOff, Lock, UserPlus, X } from 'lucide-react';
import { apiFetch } from '../api';

const EMPTY_FORM = { email: '', password: '', newPassword: '', displayName: '', jobTitle: '', managerId: '', systemRoles: [], moduleCodes: [] };
const ROLES = [
  { value: 'ADMIN', label: 'Admin', desc: 'Toàn quyền quản trị hệ thống' },
  { value: 'WORKFLOW_OWNER', label: 'Workflow Owner', desc: 'Tạo, chỉnh sửa và publish workflow' },
  { value: 'EDITOR', label: 'Editor', desc: 'Được chỉnh sửa workflow nhưng không được xóa hoặc publish' },
  { value: 'VIEWER', label: 'Viewer', desc: 'Xem workflow, tạo yêu cầu và xử lý task được giao' },
];

export default function UserFormPanel({ isOpen, onClose, mode = 'create', initialData, onSave, fetchDropdownUsers }) {
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [managers, setManagers] = useState([]);
  const [managerQuery, setManagerQuery] = useState('');
  const [managerOpen, setManagerOpen] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [modules, setModules] = useState([]);

  useEffect(() => {
    if (!isOpen) return;
    setFormData(mode === 'edit' && initialData ? {
      ...EMPTY_FORM, email: initialData.email || '', displayName: initialData.displayName || '',
      jobTitle: initialData.jobTitle || '', managerId: initialData.managerId || '', systemRoles: initialData.systemRoles || [], moduleCodes: initialData.moduleCodes || [],
    } : EMPTY_FORM);
    setFieldErrors({}); setFormError(''); setSaving(false); setManagerQuery(''); setManagerOpen(false);
    fetchDropdownUsers(mode === 'edit' ? initialData?.id : null).then(setManagers).catch(() => setManagers([]));
    apiFetch('/api/metadata/modules/all').then(response => response.ok ? response.json() : []).then(setModules).catch(() => setModules([]));
  }, [isOpen, mode, initialData, fetchDropdownUsers]);

  const selectedManager = managers.find(manager => manager.id === formData.managerId)
    || (formData.managerId && initialData?.managerName
      ? { id: formData.managerId, displayName: initialData.managerName }
      : null);
  const filteredManagers = useMemo(() => managers.filter(manager => manager.displayName?.toLocaleLowerCase('vi').includes(managerQuery.toLocaleLowerCase('vi'))), [managers, managerQuery]);
  if (!isOpen) return null;

  const updateField = (field, value) => {
    setFormData(current => ({ ...current, [field]: value }));
    setFieldErrors(current => ({ ...current, [field]: '' }));
    setFormError('');
  };
  const toggleRole = role => updateField('systemRoles', formData.systemRoles.includes(role) ? formData.systemRoles.filter(item => item !== role) : [...formData.systemRoles, role]);
  const toggleModule = code => updateField('moduleCodes', formData.moduleCodes.includes(code) ? formData.moduleCodes.filter(item => item !== code) : [...formData.moduleCodes, code]);

  const submit = async event => {
    event.preventDefault();
    const errors = validate(formData, mode);
    if (Object.keys(errors).length) { setFieldErrors(errors); return; }
    setSaving(true); setFormError('');
    try { await onSave(formData); }
    catch (error) {
      const backendErrors = translateFieldErrors(error.fieldErrors || {});
      setFieldErrors(backendErrors);
      setFormError(Object.keys(backendErrors).length ? 'Vui lòng kiểm tra lại các trường được đánh dấu.' : error.message || 'Không thể lưu User.');
    } finally { setSaving(false); }
  };

  return <>
    <div className="fixed inset-0 z-40 bg-slate-900/45 backdrop-blur-[1px]" onMouseDown={() => !saving && onClose()}/>
    <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
      <form onSubmit={submit} className="pointer-events-auto flex max-h-[calc(100vh-48px)] w-full max-w-[720px] flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl">
        <header className="flex items-center justify-between border-b border-gray-200 bg-orange-50/50 px-8 py-5">
          <div className="flex items-center gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-full bg-orange-100 text-orange-600">{mode === 'create' ? <UserPlus size={18}/> : <Edit2 size={18}/>}</span><div><h2 className="text-lg font-bold text-slate-800">{mode === 'create' ? 'Thêm User mới' : 'Sửa User'}</h2><p className="text-xs text-gray-500">{mode === 'create' ? 'Tạo tài khoản và cấp quyền ban đầu' : 'Cập nhật thông tin tài khoản'}</p></div></div>
          <button type="button" disabled={saving} onClick={onClose} className="rounded-lg p-2 text-gray-400 hover:bg-white hover:text-slate-700 disabled:opacity-50" aria-label="Đóng"><X size={20}/></button>
        </header>

        <div className="flex-1 space-y-7 overflow-auto px-8 py-6">
          {formError && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-600">{formError}</div>}
          <section>
            <h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-gray-500">Thông tin cơ bản</h3>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Email" required error={fieldErrors.email} className="sm:col-span-2">
                <div className="relative">{mode === 'edit' && <Lock size={14} className="absolute right-3 top-3 text-gray-400"/>}<input type="email" disabled={mode === 'edit'} autoComplete="email" value={formData.email} onChange={event => updateField('email', event.target.value)} placeholder="user@company.com" className={inputClass(fieldErrors.email, mode === 'edit')}/></div>
                {mode === 'edit' && <p className="mt-1 text-[11px] text-gray-400">Email không thể thay đổi sau khi tạo.</p>}
              </Field>
              {mode === 'create' && <Field label="Mật khẩu ban đầu" required error={fieldErrors.password} className="sm:col-span-2"><PasswordInput value={formData.password} onChange={value => updateField('password', value)} placeholder="Tối thiểu 6 ký tự" error={fieldErrors.password}/></Field>}
              <Field label="Tên hiển thị" required error={fieldErrors.displayName}><input value={formData.displayName} onChange={event => updateField('displayName', event.target.value)} placeholder="Nguyễn Văn A" className={inputClass(fieldErrors.displayName)}/></Field>
              <Field label="Chức danh" required error={fieldErrors.jobTitle}><input value={formData.jobTitle} onChange={event => updateField('jobTitle', event.target.value)} placeholder="Nhập chức danh" className={inputClass(fieldErrors.jobTitle)}/></Field>
              {mode === 'edit' && <Field label="Đặt mật khẩu mới" hint="Không bắt buộc" error={fieldErrors.newPassword} className="sm:col-span-2"><PasswordInput value={formData.newPassword} onChange={value => updateField('newPassword', value)} placeholder="Để trống nếu không thay đổi" error={fieldErrors.newPassword}/></Field>}
              <Field label="Quản lý trực tiếp" className="relative sm:col-span-2">
                <button type="button" onClick={() => setManagerOpen(open => !open)} className="flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-left text-sm"><span className={selectedManager ? 'text-slate-700' : 'text-gray-400'}>{selectedManager?.displayName || 'Chọn quản lý...'}</span><span className="text-gray-400">⌄</span></button>
                {managerOpen && <div className="absolute z-20 mt-1 w-full overflow-hidden rounded-lg border border-gray-200 bg-white shadow-xl"><div className="border-b border-gray-100 p-2"><input value={managerQuery} onChange={event => setManagerQuery(event.target.value)} placeholder="Tìm quản lý..." className="w-full rounded-md border border-gray-200 px-3 py-2 text-sm outline-none focus:border-orange-400"/></div><div className="max-h-48 overflow-auto"><button type="button" onClick={() => { updateField('managerId', ''); setManagerOpen(false); }} className="block w-full px-3 py-2 text-left text-sm text-gray-500 hover:bg-slate-50">— Không chọn —</button>{filteredManagers.map(manager => <button type="button" key={manager.id} onClick={() => { updateField('managerId', manager.id); setManagerOpen(false); }} className="block w-full px-3 py-2 text-left hover:bg-orange-50"><span className="block text-sm font-medium text-slate-700">{manager.displayName}</span><span className="text-[11px] text-gray-400">{manager.jobTitle || manager.email}</span></button>)}</div></div>}
              </Field>
            </div>
          </section>

          <section><h3 className="mb-4 text-xs font-bold uppercase tracking-wider text-gray-500">Role hệ thống <span className="text-red-500">*</span></h3><div className="space-y-3">{ROLES.map(role => { const selected = formData.systemRoles.includes(role.value); return <label key={role.value} className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors ${selected ? 'border-orange-400 bg-orange-50/50' : 'border-gray-200 hover:border-gray-300'}`}><input type="checkbox" checked={selected} onChange={() => toggleRole(role.value)} className="mt-1 accent-orange-500"/><span><span className={`block text-sm font-semibold ${selected ? 'text-orange-700' : 'text-slate-800'}`}>{role.label}</span><span className="mt-0.5 block text-xs text-gray-500">{role.desc}</span></span></label>; })}</div>{fieldErrors.systemRoles && <span className="mt-2 block text-xs font-medium text-red-500">{fieldErrors.systemRoles}</span>}</section>

          <section><h3 className="mb-2 text-xs font-bold uppercase tracking-wider text-gray-500">Module nghiệp vụ {!formData.systemRoles.includes('ADMIN') && <span className="text-red-500">*</span>}</h3><p className="mb-4 text-xs text-gray-500">User chỉ xem và quản lý workflow thuộc các module được cấp. Admin có quyền toàn hệ thống.</p><div className="grid gap-3 sm:grid-cols-2">{modules.map(item => { const selected = formData.moduleCodes.includes(item.code); return <label key={item.code} className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${selected ? 'border-orange-400 bg-orange-50/50' : 'border-gray-200'} ${!item.active ? 'opacity-60' : ''}`}><input type="checkbox" disabled={!item.active && !selected} checked={selected} onChange={() => toggleModule(item.code)} className="mt-1 accent-orange-500"/><span><span className="block text-sm font-semibold text-slate-700">{item.name}</span><span className="text-[11px] text-gray-400">{item.code}{!item.active ? ' · Đã ngừng' : ''}</span></span></label>; })}</div>{fieldErrors.moduleCodes && <span className="mt-2 block text-xs font-medium text-red-500">{fieldErrors.moduleCodes}</span>}</section>
        </div>

        <footer className="flex justify-end gap-3 border-t border-gray-200 bg-slate-50/60 px-8 py-5"><button type="button" disabled={saving} onClick={onClose} className="rounded-lg border border-gray-300 bg-white px-5 py-2.5 text-sm font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-50">Hủy</button><button type="submit" disabled={saving} className="min-w-[160px] rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-60">{saving ? 'Đang lưu...' : mode === 'create' ? 'Lưu User' : 'Cập nhật User'}</button></footer>
      </form>
    </div>
  </>;
}

function Field({ label, required, hint, error, className = '', children }) { return <label className={`block ${className}`}><span className="mb-1.5 block text-sm font-semibold text-gray-700">{label}{required && <span className="text-red-500"> *</span>}{hint && <span className="ml-1 font-normal text-gray-400">({hint})</span>}</span>{children}{error && <span className="mt-1 block text-xs font-medium text-red-500">{error}</span>}</label>; }
function PasswordInput({ value, onChange, placeholder, error }) { const [visible, setVisible] = useState(false); return <span className="relative block"><input type={visible ? 'text' : 'password'} autoComplete="new-password" value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} className={`${inputClass(error)} pr-11`}/><button type="button" onClick={() => setVisible(show => !show)} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-gray-400 hover:text-orange-600" aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>{visible ? <EyeOff size={17}/> : <Eye size={17}/>}</button></span>; }
function inputClass(error, disabled) { return `w-full rounded-lg border px-3 py-2.5 text-sm outline-none transition focus:ring-1 focus:ring-orange-400 ${error ? 'border-red-400 bg-red-50/30' : 'border-gray-300'} ${disabled ? 'cursor-not-allowed bg-gray-50 pr-9 text-gray-500' : 'bg-white'}`; }
function validate(data, mode) {
  const errors = {};
  if (mode === 'create' && !data.email.trim()) errors.email = 'Vui lòng nhập email.';
  else if (mode === 'create' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email.trim())) errors.email = 'Email không đúng định dạng.';
  if (!data.displayName.trim()) errors.displayName = 'Vui lòng nhập tên hiển thị.';
  if (!data.jobTitle.trim()) errors.jobTitle = 'Vui lòng nhập chức danh.';
  if (mode === 'create' && !data.password) errors.password = 'Vui lòng nhập mật khẩu ban đầu.';
  else if (mode === 'create' && data.password.length < 6) errors.password = 'Mật khẩu phải có ít nhất 6 ký tự.';
  if (mode === 'edit' && data.newPassword && data.newPassword.length < 6) errors.newPassword = 'Mật khẩu mới phải có ít nhất 6 ký tự.';
  if (!data.systemRoles.length) errors.systemRoles = 'Vui lòng chọn ít nhất một role.';
  if (!data.systemRoles.includes('ADMIN') && !data.moduleCodes.length) errors.moduleCodes = 'User không phải Admin phải thuộc ít nhất một module.';
  return errors;
}
function translateFieldErrors(errors) {
  return Object.fromEntries(Object.entries(errors).map(([field, message]) => {
    if (field === 'password' && /required/i.test(message)) return [field, 'Vui lòng nhập mật khẩu ban đầu.'];
    if (field === 'password' && /at least 6/i.test(message)) return [field, 'Mật khẩu phải có ít nhất 6 ký tự.'];
    if (field === 'email' && /format/i.test(message)) return [field, 'Email không đúng định dạng.'];
    if (field === 'email' && /required/i.test(message)) return [field, 'Vui lòng nhập email.'];
    if (field === 'displayName') return [field, 'Vui lòng nhập tên hiển thị.'];
    if (field === 'systemRoles') return [field, 'Vui lòng chọn ít nhất một role.'];
    return [field, message];
  }));
}
