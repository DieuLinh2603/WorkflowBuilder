import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Clock3, Mail, MessageSquare, Search, Trash2, UserPlus, Users, UsersRound, Webhook } from 'lucide-react';
import { apiError, apiFetch } from '../api';
import WorkflowMetadataSettings from '../components/WorkflowMetadataSettings';

export default function SettingsPage() {
  const [hours, setHours] = useState(24), [groups, setGroups] = useState([]), [users, setUsers] = useState([]);
  const [selectedId, setSelectedId] = useState(''), [selectedGroup, setSelectedGroup] = useState(null);
  const [groupName, setGroupName] = useState(''), [query, setQuery] = useState(''), [candidateId, setCandidateId] = useState('');
  const [teamsWebhook, setTeamsWebhook] = useState(''), [webhookUrl, setWebhookUrl] = useState('');
  const [delivery, setDelivery] = useState({
    teamsWebhookConfigured: false, teamsWebhookSource: 'NONE', teamsWebhookDisplay: '',
    webhookConfigured: false, webhookSource: 'NONE', webhookDisplay: '',
    smtpConfigured: false, smtpSource: 'NONE', smtpHost: '',
  });
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState('');

  const loadBase = async preferredId => {
    setLoading(true); setError('');
    const [settingResponse, endpointResponse, groupsResponse, usersResponse] = await Promise.all([
      apiFetch('/api/settings/deadline-reminder'), apiFetch('/api/settings/reminder-endpoints'),
      apiFetch('/api/groups'), apiFetch('/api/users/active'),
    ]);
    if (!settingResponse.ok || !endpointResponse.ok || !groupsResponse.ok || !usersResponse.ok) {
      const failed = [settingResponse, endpointResponse, groupsResponse, usersResponse].find(response => !response.ok);
      setError(await apiError(failed, 'Không thể tải cài đặt hệ thống')); setLoading(false); return;
    }
    const [setting, endpointStatus, groupRows, userRows] = await Promise.all([settingResponse.json(), endpointResponse.json(), groupsResponse.json(), usersResponse.json()]);
    setHours(setting.leadTimeHours); setDelivery(endpointStatus); setGroups(groupRows); setUsers(userRows);
    const nextId = preferredId || selectedId || groupRows[0]?.id || '';
    setSelectedId(groupRows.some(group => group.id === nextId) ? nextId : groupRows[0]?.id || '');
    setLoading(false);
  };
  const loadGroup = async id => {
    if (!id) { setSelectedGroup(null); return; }
    const response = await apiFetch(`/api/groups/${id}`);
    if (!response.ok) throw new Error(await apiError(response, 'Không thể tải thành viên Group'));
    setSelectedGroup(await response.json());
  };

  useEffect(() => { loadBase(); }, []);
  useEffect(() => {
    if (!selectedId) { setSelectedGroup(null); return; }
    let active = true;
    loadGroup(selectedId).catch(requestError => active && setError(requestError.message));
    return () => { active = false; };
  }, [selectedId]);

  const memberIds = useMemo(() => new Set((selectedGroup?.members || []).map(member => member.id)), [selectedGroup]);
  const candidates = useMemo(() => users.filter(user => !memberIds.has(user.id) && `${user.displayName} ${user.email} ${user.jobTitle || ''}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))), [users, memberIds, query]);

  const saveDeadline = async () => {
    setBusy(true); setError('');
    const response = await apiFetch('/api/settings/deadline-reminder', { method: 'PUT', body: JSON.stringify({ leadTimeHours: Number(hours) }), successMessage: 'Đã lưu thời gian nhắc deadline.' });
    if (!response.ok) setError(await apiError(response, 'Không thể lưu cài đặt deadline'));
    setBusy(false);
  };
  const saveEndpoints = async () => {
    const body = {};
    if (teamsWebhook.trim()) body['reminder.teams.webhookUrl'] = teamsWebhook.trim();
    if (webhookUrl.trim()) body['reminder.webhook.url'] = webhookUrl.trim();
    if (!Object.keys(body).length) return;
    setBusy(true); setError('');
    const response = await apiFetch('/api/settings/reminder-endpoints', {
      method: 'PUT', body: JSON.stringify(body), successMessage: 'Đã lưu endpoint reminder.',
    });
    if (!response.ok) setError(await apiError(response, 'Không thể lưu endpoint reminder'));
    else { setDelivery(await response.json()); setTeamsWebhook(''); setWebhookUrl(''); }
    setBusy(false);
  };
  const clearEndpoint = async key => {
    setBusy(true); setError('');
    const response = await apiFetch('/api/settings/reminder-endpoints', {
      method: 'PUT', body: JSON.stringify({ [key]: '' }), successMessage: 'Đã xóa endpoint reminder.',
    });
    if (!response.ok) setError(await apiError(response, 'Không thể xóa endpoint reminder'));
    else setDelivery(await response.json());
    setBusy(false);
  };
  const createGroup = async event => {
    event.preventDefault(); if (!groupName.trim()) return;
    setBusy(true); setError('');
    const response = await apiFetch('/api/groups', { method: 'POST', body: JSON.stringify({ name: groupName.trim() }), successMessage: 'Đã tạo Group mới.' });
    if (!response.ok) setError(await apiError(response, 'Không thể tạo Group'));
    else { const created = await response.json(); setGroupName(''); await loadBase(created.id); }
    setBusy(false);
  };
  const addMember = async () => {
    if (!selectedId || !candidateId) return;
    setBusy(true); setError('');
    const response = await apiFetch(`/api/groups/${selectedId}/members/${candidateId}`, { method: 'PUT', successMessage: 'Đã thêm User vào Group.' });
    if (!response.ok) setError(await apiError(response, 'Không thể thêm thành viên'));
    else { setCandidateId(''); setQuery(''); await Promise.all([loadBase(selectedId), loadGroup(selectedId)]); }
    setBusy(false);
  };
  const removeMember = async member => {
    setBusy(true); setError('');
    const response = await apiFetch(`/api/groups/${selectedId}/members/${member.id}`, { method: 'DELETE', successMessage: `Đã xóa ${member.displayName} khỏi Group.` });
    if (!response.ok) setError(await apiError(response, 'Không thể xóa thành viên'));
    else await Promise.all([loadBase(selectedId), loadGroup(selectedId)]);
    setBusy(false);
  };

  return <div className="space-y-6">
    <header><h1 className="text-2xl font-bold text-slate-800">Cài đặt hệ thống</h1><p className="mt-1 text-sm text-gray-500">Cấu hình deadline và nhóm người dùng phục vụ workflow</p></header>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

    <section className="max-w-2xl rounded-xl border border-grayBorder bg-white p-5 shadow-sm">
      <div className="flex items-start gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-500"><Clock3 size={20}/></span><div className="flex-1"><h2 className="font-bold text-slate-800">Nhắc gần deadline</h2><p className="mt-1 text-xs text-gray-500">Mốc nhắc chung áp dụng cho task có deadline trong toàn hệ thống.</p><div className="mt-4 flex max-w-md gap-2"><div className="relative flex-1"><input type="number" min="1" max="720" value={hours} onChange={event => setHours(event.target.value)} className="input-field pr-12"/><span className="absolute right-3 top-2.5 text-sm text-gray-400">giờ</span></div><button disabled={busy} onClick={saveDeadline} className="btn-primary px-5 disabled:opacity-50">Lưu</button></div></div></div>
    </section>

    <section className="max-w-4xl rounded-xl border border-grayBorder bg-white p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-sky-50 text-sky-600"><Webhook size={20}/></span>
        <div className="min-w-0 flex-1">
          <h2 className="font-bold text-slate-800">Kênh gửi reminder</h2>
          <p className="mt-1 text-xs leading-5 text-gray-500">Endpoint được lưu dùng cho worker gửi nhắc deadline. URL bí mật chỉ hiển thị dưới dạng đã che.</p>

          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            <EndpointCard
              icon={MessageSquare} title="Microsoft Teams webhook"
              description="Dùng cho gửi reminder đến kênh Microsoft Teams."
              value={teamsWebhook} onChange={setTeamsWebhook}
              placeholder={delivery.teamsWebhookConfigured ? 'Đã cấu hình — nhập URL mới để thay đổi' : 'https://...webhook.office.com/...'}
              configured={delivery.teamsWebhookConfigured} source={delivery.teamsWebhookSource}
              display={delivery.teamsWebhookDisplay}
              onClear={delivery.teamsWebhookSource === 'SETTINGS' ? () => clearEndpoint('reminder.teams.webhookUrl') : null}
              busy={busy}
            />
            <EndpointCard
              icon={Webhook} title="Webhook chung"
              description="Endpoint nhận reminder từ worker của hệ thống."
              value={webhookUrl} onChange={setWebhookUrl}
              placeholder={delivery.webhookConfigured ? 'Đã cấu hình — nhập URL mới để thay đổi' : 'https://api.company.com/workflow-reminders'}
              configured={delivery.webhookConfigured} source={delivery.webhookSource}
              display={delivery.webhookDisplay}
              onClear={delivery.webhookSource === 'SETTINGS' ? () => clearEndpoint('reminder.webhook.url') : null}
              busy={busy}
            />
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex items-start gap-3"><Mail size={18} className="mt-0.5 text-slate-500"/><div><p className="text-sm font-bold text-slate-700">SMTP Email</p><p className="mt-1 text-xs text-gray-500">Cấu hình qua environment: <code>SPRING_MAIL_HOST</code>, <code>SPRING_MAIL_USERNAME</code> và <code>SPRING_MAIL_PASSWORD</code>.</p>{delivery.smtpHost && <p className="mt-1 text-[11px] text-gray-400">Mail host: {delivery.smtpHost}</p>}</div></div>
            <StatusBadge configured={delivery.smtpConfigured} source={delivery.smtpSource}/>
          </div>

          <div className="mt-4 flex justify-end"><button type="button" disabled={busy || (!teamsWebhook.trim() && !webhookUrl.trim())} onClick={saveEndpoints} className="btn-primary px-5 disabled:cursor-not-allowed disabled:opacity-50">Lưu endpoint</button></div>
        </div>
      </div>
    </section>

    <WorkflowMetadataSettings/>

    <section className="overflow-hidden rounded-xl border border-grayBorder bg-white shadow-sm">
      <div className="border-b border-grayBorder px-6 py-5"><div className="flex items-center gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-50 text-purple-500"><UsersRound size={20}/></span><div><h2 className="font-bold text-slate-800">Nhóm người thực hiện</h2><p className="mt-1 text-xs text-gray-500">Group là tập hợp User dùng để giao Assignment task hoặc nhận thông báo. Group không phải quản lý trực tiếp và không phải Role hệ thống.</p></div></div></div>
      <div className="grid min-h-[480px] lg:grid-cols-[320px_1fr]">
        <aside className="border-b border-grayBorder bg-slate-50/60 p-5 lg:border-b-0 lg:border-r">
          <form onSubmit={createGroup} className="flex gap-2"><input value={groupName} onChange={event => setGroupName(event.target.value)} className="input-field bg-white" placeholder="Tên Group mới"/><button disabled={busy || !groupName.trim()} className="btn-primary shrink-0 px-4 disabled:opacity-50">Thêm</button></form>
          <p className="mb-2 mt-5 text-[11px] font-bold uppercase tracking-wider text-gray-400">Danh sách Group</p>
          <div className="space-y-2">{loading ? <p className="py-8 text-center text-sm text-gray-400">Đang tải...</p> : groups.length ? groups.map(group => <button type="button" key={group.id} onClick={() => setSelectedId(group.id)} className={`flex w-full items-center gap-3 rounded-xl border px-3 py-3 text-left transition ${selectedId === group.id ? 'border-orange-300 bg-orange-50' : 'border-transparent bg-white hover:border-gray-200'}`}><span className={`flex h-9 w-9 items-center justify-center rounded-lg ${selectedId === group.id ? 'bg-orange-100 text-orange-600' : 'bg-slate-100 text-slate-500'}`}><Users size={17}/></span><span className="min-w-0 flex-1"><span className="block truncate text-sm font-semibold text-slate-700">{group.name}</span><span className="text-[11px] text-gray-400">{group.memberCount} thành viên</span></span></button>) : <p className="rounded-lg border border-dashed border-gray-300 px-3 py-8 text-center text-sm text-gray-400">Chưa có Group</p>}</div>
        </aside>

        <div className="p-6">{selectedGroup ? <>
          <div className="mb-5 flex items-start justify-between"><div><h3 className="text-lg font-bold text-slate-800">{selectedGroup.name}</h3><p className="mt-1 text-xs text-gray-500">{selectedGroup.memberCount} User sẽ nhận task khi Assignment Step được giao cho Group này.</p></div></div>
          <div className="mb-5 rounded-xl border border-grayBorder bg-slate-50 p-4"><p className="mb-3 text-xs font-bold uppercase text-gray-500">Thêm User vào Group</p><div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]"><div className="relative"><Search size={15} className="absolute left-3 top-3 text-gray-400"/><input value={query} onChange={event => { setQuery(event.target.value); setCandidateId(''); }} className="input-field bg-white pl-9" placeholder="Tìm tên, email..."/></div><select value={candidateId} onChange={event => setCandidateId(event.target.value)} className="input-field bg-white"><option value="">Chọn User ({candidates.length})</option>{candidates.map(user => <option key={user.id} value={user.id}>{user.displayName} — {user.email}</option>)}</select><button type="button" disabled={busy || !candidateId} onClick={addMember} className="btn-primary flex items-center justify-center gap-2 px-4 disabled:opacity-50"><UserPlus size={16}/> Thêm</button></div></div>
          <div className="overflow-hidden rounded-xl border border-grayBorder"><table className="w-full text-left"><thead><tr className="border-b border-grayBorder bg-slate-50 text-[11px] font-bold uppercase text-gray-500"><th className="px-4 py-3">Thành viên</th><th className="px-4 py-3">Email</th><th className="px-4 py-3">Chức danh</th><th className="w-16 px-4 py-3"></th></tr></thead><tbody className="divide-y divide-slate-100">{selectedGroup.members?.length ? selectedGroup.members.map(member => <tr key={member.id} className="text-sm"><td className="px-4 py-3 font-semibold text-slate-700">{member.displayName}</td><td className="px-4 py-3 text-gray-500">{member.email}</td><td className="px-4 py-3 text-gray-500">{member.jobTitle || '—'}</td><td className="px-4 py-3"><button type="button" disabled={busy} onClick={() => removeMember(member)} className="rounded-lg p-2 text-gray-400 hover:bg-red-50 hover:text-red-500 disabled:opacity-40" title="Xóa khỏi Group"><Trash2 size={16}/></button></td></tr>) : <tr><td colSpan="4" className="px-4 py-12 text-center text-sm text-gray-400">Group chưa có thành viên. Hãy chọn User ở phía trên để thêm.</td></tr>}</tbody></table></div>
        </> : <div className="flex h-full min-h-80 items-center justify-center text-center"><div><UsersRound size={42} className="mx-auto text-gray-300"/><p className="mt-3 text-sm font-medium text-gray-500">Tạo hoặc chọn một Group để quản lý thành viên</p></div></div>}</div>
      </div>
    </section>
  </div>;
}

function EndpointCard({ icon: Icon, title, description, value, onChange, placeholder, configured, source, display, onClear, busy }) {
  return <div className="rounded-xl border border-slate-200 p-4">
    <div className="flex items-start justify-between gap-3">
      <div className="flex items-start gap-2"><Icon size={17} className="mt-0.5 shrink-0 text-sky-600"/><div><p className="text-sm font-bold text-slate-700">{title}</p><p className="mt-1 text-[11px] leading-4 text-gray-500">{description}</p></div></div>
      <StatusBadge configured={configured} source={source}/>
    </div>
    {configured && <p className="mt-3 truncate rounded-md bg-slate-50 px-3 py-2 font-mono text-[11px] text-slate-500" title={display}>{display}</p>}
    <input type="password" autoComplete="new-password" value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} className="input-field mt-3"/>
    <div className="mt-2 flex min-h-6 items-center justify-between gap-2">
      <p className="text-[10px] text-gray-400">Bỏ trống để giữ nguyên endpoint hiện tại.</p>
      {onClear && <button type="button" disabled={busy} onClick={onClear} className="text-[11px] font-semibold text-red-500 hover:text-red-600 disabled:opacity-40">Xóa cấu hình</button>}
    </div>
  </div>;
}

function StatusBadge({ configured, source }) {
  return configured
    ? <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700"><CheckCircle2 size={11}/> Đã cấu hình{source === 'ENVIRONMENT' ? ' (ENV)' : ''}</span>
    : <span className="shrink-0 rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-500">Chưa cấu hình</span>;
}
