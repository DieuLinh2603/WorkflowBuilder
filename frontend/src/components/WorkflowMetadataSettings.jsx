import { useEffect, useState } from 'react';
import { Boxes, ListChecks, Pencil, Plus, Power } from 'lucide-react';
import { apiError, apiFetch } from '../api';

const EMPTY_MODULE = { code: '', name: '', description: '', sortOrder: 0 };
const EMPTY_TYPE = { code: '', name: '', description: '', sortOrder: 0, recommendedStepTypes: [], checklistText: '' };
const STEP_TYPES = ['APPROVAL', 'REVIEW', 'ASSIGNMENT', 'NOTIFICATION', 'SYSTEM_ACTION', 'END'];

export default function WorkflowMetadataSettings() {
  const [tab, setTab] = useState('modules');
  const [modules, setModules] = useState([]);
  const [types, setTypes] = useState([]);
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    const [moduleResponse, typeResponse] = await Promise.all([
      apiFetch('/api/metadata/modules/all'), apiFetch('/api/metadata/workflow-types/all'),
    ]);
    if (!moduleResponse.ok || !typeResponse.ok) throw new Error(await apiError(!moduleResponse.ok ? moduleResponse : typeResponse, 'Không thể tải danh mục nghiệp vụ'));
    setModules(await moduleResponse.json()); setTypes(await typeResponse.json());
  };
  useEffect(() => { load().catch(e => setError(e.message)); }, []);

  const startCreate = () => setEditing(tab === 'modules' ? { kind: 'modules', original: null, data: EMPTY_MODULE } : { kind: 'types', original: null, data: EMPTY_TYPE });
  const startEdit = item => setEditing({ kind: tab, original: item.code, data: tab === 'modules' ? { ...item } : { ...item, checklistText: (item.checklist || []).join('\n') } });
  const save = async event => {
    event.preventDefault(); setBusy(true); setError('');
    const isModule = editing.kind === 'modules';
    const base = isModule ? '/api/metadata/modules' : '/api/metadata/workflow-types';
    const payload = isModule ? editing.data : { ...editing.data, checklist: editing.data.checklistText.split('\n').map(x => x.trim()).filter(Boolean) };
    delete payload.active; delete payload.checklistText;
    const response = await apiFetch(editing.original ? `${base}/${editing.original}` : base, { method: editing.original ? 'PUT' : 'POST', body: JSON.stringify(payload) });
    if (!response.ok) setError(await apiError(response, 'Không thể lưu danh mục'));
    else { setEditing(null); await load(); }
    setBusy(false);
  };
  const toggle = async item => {
    setBusy(true); setError('');
    const base = tab === 'modules' ? '/api/metadata/modules' : '/api/metadata/workflow-types';
    const response = await apiFetch(`${base}/${item.code}/status?active=${!item.active}`, { method: 'PATCH' });
    if (!response.ok) setError(await apiError(response, 'Không thể đổi trạng thái danh mục')); else await load();
    setBusy(false);
  };

  const rows = tab === 'modules' ? modules : types;
  return <section className="overflow-hidden rounded-xl border border-grayBorder bg-white shadow-sm">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-grayBorder px-6 py-5">
      <div className="flex items-center gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-50 text-orange-500"><Boxes size={20}/></span><div><h2 className="font-bold text-slate-800">Danh mục nghiệp vụ</h2><p className="mt-1 text-xs text-gray-500">Quản lý module phân quyền và loại workflow dùng để hướng dẫn thiết kế.</p></div></div>
      <button type="button" onClick={startCreate} className="btn-primary flex items-center gap-2 px-4"><Plus size={15}/> Thêm danh mục</button>
    </div>
    {error && <p className="mx-6 mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
    <div className="flex gap-2 px-6 pt-5"><Tab active={tab === 'modules'} onClick={() => { setTab('modules'); setEditing(null); }} icon={Boxes}>Module</Tab><Tab active={tab === 'types'} onClick={() => { setTab('types'); setEditing(null); }} icon={ListChecks}>Loại Workflow</Tab></div>
    <div className="grid gap-5 p-6 lg:grid-cols-[1fr_360px]">
      <div className="overflow-hidden rounded-xl border border-slate-200"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-[11px] uppercase text-gray-500"><tr><th className="px-4 py-3">Tên / mã</th><th className="px-4 py-3">Mô tả</th><th className="px-4 py-3">Thứ tự</th><th className="px-4 py-3">Trạng thái</th><th className="w-24"></th></tr></thead><tbody className="divide-y divide-slate-100">{rows.map(item => <tr key={item.code}><td className="px-4 py-3"><p className="font-semibold text-slate-700">{item.name}</p><code className="text-[11px] text-gray-400">{item.code}</code></td><td className="max-w-xs px-4 py-3 text-xs text-gray-500">{item.description || '—'}</td><td className="px-4 py-3 text-gray-500">{item.sortOrder}</td><td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-[10px] font-bold ${item.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>{item.active ? 'Hoạt động' : 'Đã ngừng'}</span></td><td><div className="flex"><button type="button" onClick={() => startEdit(item)} className="p-2 text-gray-400 hover:text-orange-600" title="Chỉnh sửa"><Pencil size={15}/></button><button type="button" disabled={busy} onClick={() => toggle(item)} className="p-2 text-gray-400 hover:text-orange-600" title={item.active ? 'Ngừng hoạt động' : 'Kích hoạt'}><Power size={15}/></button></div></td></tr>)}</tbody></table></div>
      {editing ? <MetadataForm editing={editing} setEditing={setEditing} busy={busy} onSubmit={save}/> : <div className="flex min-h-56 items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-gray-400">Chọn một mục để sửa hoặc bấm “Thêm danh mục”.<br/>Mã chỉ được đặt khi tạo và không thể thay đổi.</div>}
    </div>
  </section>;
}

function Tab({ active, onClick, icon: Icon, children }) { return <button type="button" onClick={onClick} className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold ${active ? 'bg-orange-100 text-orange-700' : 'bg-slate-100 text-gray-500'}`}><Icon size={15}/>{children}</button>; }

function MetadataForm({ editing, setEditing, busy, onSubmit }) {
  const data = editing.data; const update = (key, value) => setEditing(current => ({ ...current, data: { ...current.data, [key]: value } }));
  const isType = editing.kind === 'types';
  return <form onSubmit={onSubmit} className="space-y-3 rounded-xl border border-orange-200 bg-orange-50/30 p-4"><h3 className="font-bold text-slate-700">{editing.original ? 'Chỉnh sửa' : 'Thêm mới'} {isType ? 'loại workflow' : 'module'}</h3><label className="block text-xs font-semibold text-gray-600">Mã<input required disabled={!!editing.original} value={data.code} onChange={e => update('code', e.target.value.toUpperCase())} className="input-field mt-1 disabled:bg-slate-100" placeholder="VD: PROCUREMENT"/></label><label className="block text-xs font-semibold text-gray-600">Tên<input required value={data.name} onChange={e => update('name', e.target.value)} className="input-field mt-1"/></label><label className="block text-xs font-semibold text-gray-600">Mô tả<textarea rows="2" value={data.description || ''} onChange={e => update('description', e.target.value)} className="input-field mt-1 resize-none"/></label><label className="block text-xs font-semibold text-gray-600">Thứ tự<input type="number" value={data.sortOrder} onChange={e => update('sortOrder', Number(e.target.value))} className="input-field mt-1"/></label>{isType && <><fieldset><legend className="mb-2 text-xs font-semibold text-gray-600">Step đề xuất</legend><div className="flex flex-wrap gap-2">{STEP_TYPES.map(step => <label key={step} className="flex items-center gap-1 rounded bg-white px-2 py-1 text-[10px]"><input type="checkbox" checked={(data.recommendedStepTypes || []).includes(step)} onChange={() => update('recommendedStepTypes', (data.recommendedStepTypes || []).includes(step) ? data.recommendedStepTypes.filter(x => x !== step) : [...(data.recommendedStepTypes || []), step])}/>{step}</label>)}</div></fieldset><label className="block text-xs font-semibold text-gray-600">Checklist (mỗi dòng một mục)<textarea rows="4" value={data.checklistText || ''} onChange={e => update('checklistText', e.target.value)} className="input-field mt-1 resize-none"/></label></>}<div className="flex justify-end gap-2 pt-2"><button type="button" onClick={() => setEditing(null)} className="rounded-lg border bg-white px-4 py-2 text-sm">Hủy</button><button disabled={busy} className="btn-primary px-4 disabled:opacity-50">Lưu</button></div></form>;
}
