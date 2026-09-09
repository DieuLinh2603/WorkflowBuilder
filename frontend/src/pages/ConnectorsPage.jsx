import { useEffect, useState } from 'react';
import { Database, Eye, Pencil, Plus, ShieldCheck, Trash2, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';
import { SearchDropdown, Toggle } from '../components/panels/StepPanelShared';

const empty = { name: '', description: '', connectorType: 'REST', baseUrl: '', jdbcUrl: '', username: '', password: '', token: '', allowedHosts: '', active: true };

export default function ConnectorsPage() {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState(empty);
  const [users, setUsers] = useState([]);
  const [grants, setGrants] = useState([]);
  const [userQuery, setUserQuery] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [viewingId, setViewingId] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = async () => {
    const [connectorResponse, userResponse] = await Promise.all([apiFetch('/api/connectors'), apiFetch('/api/users/active')]);
    if (connectorResponse.ok) setRows(await connectorResponse.json());
    if (userResponse.ok) setUsers(await userResponse.json());
  };
  useEffect(() => { load(); }, []);

  const resetForm = () => { setForm(empty); setGrants([]); setUserQuery(''); setEditingId(null); setError(''); };
  const edit = connector => {
    const config = connector.config || {};
    setForm({
      ...empty,
      name: connector.name || '', description: connector.description || '', connectorType: connector.connectorType,
      baseUrl: config.baseUrl || '', jdbcUrl: config.jdbcUrl || '',
      allowedHosts: Array.isArray(config.allowedHosts) ? config.allowedHosts.join(', ') : '', active: connector.active !== false,
    });
    setGrants((connector.grantedUserIds || []).map(String));
    setUserQuery('');
    setEditingId(connector.id); setViewingId(null); setError(''); setMessage('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  const save = async event => {
    event.preventDefault(); setError(''); setMessage('');
    const config = form.connectorType === 'REST'
      ? { baseUrl: form.baseUrl, allowedHosts: form.allowedHosts.split(',').map(value => value.trim()).filter(Boolean) }
      : { jdbcUrl: form.jdbcUrl };
    const body = { name: form.name, description: form.description, connectorType: form.connectorType, config, grantedUserIds: grants, active: form.active };
    if (form.connectorType === 'REST' && form.token) body.credentials = { authType: 'BEARER', token: form.token };
    if (form.connectorType === 'POSTGRESQL' && (form.username || form.password)) body.credentials = { username: form.username, password: form.password };
    if (!editingId && !body.credentials) body.credentials = form.connectorType === 'REST' ? { authType: 'NONE' } : { username: '', password: '' };
    const response = await apiFetch(editingId ? `/api/connectors/${editingId}` : '/api/connectors', { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(body) });
    if (!response.ok) return setError(await apiError(response));
    const notice = editingId ? 'Đã cập nhật connector.' : 'Đã tạo connector.';
    resetForm(); setMessage(notice); await load();
  };
  const test = async id => {
    setError(''); setMessage('');
    const response = await apiFetch(`/api/connectors/${id}/test`, { method: 'POST' });
    if (!response.ok) setError(await apiError(response)); else setMessage('Kết nối thành công.');
  };
  const remove = async id => {
    if (!window.confirm('Xóa connector này? Pipeline đang sử dụng connector có thể không chạy được.')) return;
    const response = await apiFetch(`/api/connectors/${id}`, { method: 'DELETE' });
    if (!response.ok) return setError(await apiError(response));
    if (editingId === id) resetForm();
    setMessage('Đã xóa connector.'); await load();
  };

  return <div className="space-y-6">
    <header><h1 className="text-2xl font-bold text-slate-800">Connector Management</h1><p className="text-sm text-gray-500">Quản lý kết nối dữ liệu. Credential được mã hóa AES-GCM và không bao giờ trả lại trình duyệt.</p></header>
    {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>}
    {message && <div className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700">{message}</div>}
    <form onSubmit={save} className="rounded-xl border border-grayBorder bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between"><h2 className="flex items-center gap-2 font-bold">{editingId ? <Pencil size={18}/> : <Plus size={18}/>} {editingId ? 'Chỉnh sửa connector' : 'Tạo connector'}</h2>{editingId && <button type="button" onClick={resetForm} className="flex items-center gap-1 text-xs text-gray-500"><X size={15}/>Hủy sửa</button>}</div>
      <div className="grid gap-3 md:grid-cols-2">
        <input className="input-field" placeholder="Tên connector" value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} required/>
        <select className="input-field" value={form.connectorType} onChange={event => setForm({ ...form, connectorType: event.target.value })}><option>REST</option><option>POSTGRESQL</option></select>
        <textarea className="input-field min-h-20 md:col-span-2" placeholder="Mô tả mục đích và dữ liệu của connector" value={form.description} onChange={event => setForm({ ...form, description: event.target.value })}/>
        {form.connectorType === 'REST' ? <>
          <input className="input-field" placeholder="https://api.example.com/items" value={form.baseUrl} onChange={event => setForm({ ...form, baseUrl: event.target.value })} required/>
          <input className="input-field" placeholder="Tên miền được phép, ví dụ: api.example.com" value={form.allowedHosts} onChange={event => setForm({ ...form, allowedHosts: event.target.value })}/>
          <input className="input-field md:col-span-2" type="password" placeholder={editingId ? 'Bearer token mới (để trống để giữ token hiện tại)' : 'Bearer token (nếu có)'} value={form.token} onChange={event => setForm({ ...form, token: event.target.value })}/>
        </> : <>
          <input className="input-field md:col-span-2" placeholder="jdbc:postgresql://host:5432/database" value={form.jdbcUrl} onChange={event => setForm({ ...form, jdbcUrl: event.target.value })} required/>
          <input className="input-field" placeholder={editingId ? 'Username mới (để trống để giữ nguyên)' : 'Username'} value={form.username} onChange={event => setForm({ ...form, username: event.target.value })}/>
          <input className="input-field" type="password" placeholder={editingId ? 'Password mới (để trống để giữ nguyên)' : 'Password'} value={form.password} onChange={event => setForm({ ...form, password: event.target.value })}/>
        </>}
        <UserGrantSelector users={users} grants={grants} setGrants={setGrants} query={userQuery} setQuery={setUserQuery} />
        <div className="md:col-span-2 rounded-lg border border-grayBorder bg-slate-50/60 px-4 py-3">
          <Toggle checked={form.active} onChange={active => setForm({ ...form, active })} label="Đang hoạt động" />
          <p className="mt-1 pl-[54px] text-[11px] text-gray-500">Cho phép pipeline sử dụng connector này.</p>
        </div>
      </div>
      <button className="btn-primary mt-4">{editingId ? 'Lưu thay đổi' : 'Lưu connector'}</button>
    </form>
    <section className="grid gap-3 md:grid-cols-2">{rows.map(connector => {
      const config = connector.config || {};
      const grantedNames = users.filter(user => (connector.grantedUserIds || []).includes(user.id)).map(user => user.displayName);
      return <article key={connector.id} className="rounded-xl border border-grayBorder bg-white p-4">
        <div className="flex items-start justify-between gap-3"><div className="flex min-w-0 gap-3"><span className="rounded-lg bg-blue-50 p-2 text-blue-600"><Database size={20}/></span><div className="min-w-0"><h3 className="font-bold">{connector.name}</h3><p className="text-xs text-gray-500">{connector.connectorType} · {connector.active ? 'Đang hoạt động' : 'Đã tắt'} · {connector.credentialConfigured ? 'Đã có credential' : 'Không credential'}</p><p className="mt-2 text-sm text-slate-600">{connector.description || 'Chưa có mô tả.'}</p></div></div><button onClick={() => remove(connector.id)} title="Xóa" className="text-gray-400 hover:text-red-500"><Trash2 size={17}/></button></div>
        {viewingId === connector.id && <div className="mt-4 rounded-lg bg-slate-50 p-3 text-xs text-slate-600"><p><b>Địa chỉ:</b> {config.baseUrl || config.jdbcUrl || '—'}</p>{config.allowedHosts?.length > 0 && <p className="mt-1"><b>Allowed hosts:</b> {config.allowedHosts.join(', ')}</p>}<p className="mt-1"><b>Được cấp quyền:</b> {grantedNames.join(', ') || 'Chỉ Admin/người tạo'}</p><p className="mt-1 text-gray-400">Credential được ẩn vì lý do bảo mật.</p></div>}
        <div className="mt-4 flex flex-wrap gap-2"><button onClick={() => setViewingId(current => current === connector.id ? null : connector.id)} className="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold"><Eye size={15}/>{viewingId === connector.id ? 'Thu gọn' : 'Xem'}</button><button onClick={() => edit(connector)} className="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold"><Pencil size={15}/>Chỉnh sửa</button><button onClick={() => test(connector.id)} className="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold"><ShieldCheck size={15}/>Kiểm tra kết nối</button></div>
      </article>;
    })}</section>
  </div>;
}

function UserGrantSelector({ users, grants, setGrants, query, setQuery }) {
  const selectedIds = new Set(grants.map(String));
  const selectedUsers = users.filter(user => selectedIds.has(String(user.id)));
  const availableUsers = users
    .filter(user => user.active !== false && !selectedIds.has(String(user.id)))
    .map(user => ({
      id: String(user.id),
      value: user.email,
      label: user.displayName,
      description: `${user.email}${user.jobTitle ? ` · ${user.jobTitle}` : ''}`,
    }));
  const addUser = item => {
    setGrants(current => current.some(id => String(id) === item.id) ? current : [...current, item.id]);
    setQuery('');
  };
  const removeUser = id => setGrants(current => current.filter(value => String(value) !== String(id)));

  return <div className="md:col-span-2 rounded-xl border border-grayBorder bg-slate-50/40 p-4">
    <div className="mb-3 flex items-start justify-between gap-3">
      <div>
        <p className="text-sm font-semibold text-slate-700">Người được phép sử dụng</p>
        <p className="mt-0.5 text-[11px] text-gray-500">Tìm và chọn các tài khoản được dùng connector trong pipeline.</p>
      </div>
      {!!selectedUsers.length && <button type="button" onClick={() => setGrants([])} className="shrink-0 text-xs font-medium text-gray-500 hover:text-red-500">Bỏ chọn tất cả</button>}
    </div>
    {!!selectedUsers.length && <div className="mb-3 flex flex-wrap gap-2">
      {selectedUsers.map(user => <span key={user.id} className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-orange-200 bg-orange-50 px-3 py-1.5 text-xs font-medium text-orange-700">
        <span className="truncate">{user.displayName}</span>
        <button type="button" onClick={() => removeUser(user.id)} aria-label={`Bỏ quyền của ${user.displayName}`} className="rounded-full p-0.5 hover:bg-orange-100"><X size={12}/></button>
      </span>)}
    </div>}
    <SearchDropdown value={query} onInput={setQuery} onSelect={addUser} items={availableUsers} placeholder="Tìm theo tên, email hoặc chức danh..." />
    <p className={`mt-2 text-[11px] ${selectedUsers.length ? 'text-gray-500' : 'text-amber-700'}`}>
      {selectedUsers.length ? `Đã cấp quyền cho ${selectedUsers.length} người.` : 'Chưa chọn tài khoản — chỉ Admin và người tạo connector có quyền sử dụng.'}
    </p>
  </div>;
}
