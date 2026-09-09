import { useEffect, useMemo, useState } from 'react';
import { Search, Trash2, UserPlus, Users, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';

export default function WorkflowEditorsModal({ workflow, onChanged, onClose }) {
  const [users, setUsers] = useState([]);
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');
  const editors = workflow?.editors || [];

  useEffect(() => {
    let active = true;
    apiFetch('/api/users/active', { toast: false })
      .then(async response => {
        if (!response.ok) throw new Error(await apiError(response, 'Không thể tải danh sách Editor'));
        const data = await response.json();
        if (active) setUsers(Array.isArray(data) ? data : []);
      })
      .catch(reason => active && setError(reason.message));
    return () => { active = false; };
  }, []);

  const available = useMemo(() => {
    const assignedIds = new Set(editors.map(editor => editor.id));
    const normalized = query.trim().toLocaleLowerCase('vi');
    return users.filter(user => user.active
      && user.id !== workflow.ownerId
      && user.systemRoles?.some(role => role === 'EDITOR' || role === 'VIEWER')
      && !assignedIds.has(user.id)
      && (!normalized || `${user.displayName} ${user.email} ${user.jobTitle || ''}`.toLocaleLowerCase('vi').includes(normalized)));
  }, [editors, query, users, workflow.ownerId]);

  const addEditor = async () => {
    if (!selectedId) return;
    setBusyId(selectedId);
    setError('');
    const response = await apiFetch(`/api/workflows/${workflow.id}/editors/${selectedId}`, {
      method: 'PUT', successMessage: 'Đã thêm Editor vào workflow.'
    });
    if (response.ok) {
      onChanged(await response.json());
      setSelectedId('');
      setQuery('');
    } else {
      setError(await apiError(response, 'Không thể thêm Editor'));
    }
    setBusyId('');
  };

  const removeEditor = async editor => {
    if (!window.confirm(`Gỡ quyền chỉnh sửa workflow của ${editor.displayName}?`)) return;
    setBusyId(editor.id);
    setError('');
    const response = await apiFetch(`/api/workflows/${workflow.id}/editors/${editor.id}`, {
      method: 'DELETE', successMessage: 'Đã gỡ Editor khỏi workflow.'
    });
    if (response.ok) onChanged(await response.json());
    else setError(await apiError(response, 'Không thể gỡ Editor'));
    setBusyId('');
  };

  return <div className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/45 p-4" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <div role="dialog" aria-modal="true" aria-labelledby="workflow-editors-title" className="w-full max-w-[620px] overflow-hidden rounded-2xl bg-white shadow-2xl">
      <header className="flex items-start gap-3 border-b border-slate-100 px-6 py-5">
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600"><Users size={20}/></span>
        <div className="min-w-0 flex-1">
          <h2 id="workflow-editors-title" className="text-lg font-bold text-slate-800">Quản lý Editor</h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">Owner vẫn có toàn quyền thiết kế. Tài khoản Editor hoặc Viewer được chọn sẽ có quyền Editor chỉ trên workflow Draft này.</p>
        </div>
        <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"><X size={18}/></button>
      </header>

      <div className="space-y-5 px-6 py-5">
        <section>
          <p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-slate-500">Người sở hữu</p>
          <div className="rounded-xl border border-orange-200 bg-orange-50 px-4 py-3">
            <p className="text-sm font-bold text-slate-800">{workflow.ownerName}</p>
            <p className="mt-1 text-xs text-orange-700">Thiết kế, quản trị và publish workflow</p>
          </div>
        </section>

        <section>
          <p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-slate-500">Editor đang được giao ({editors.length})</p>
          <div className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200">
            {editors.length ? editors.map(editor => <div key={editor.id} className="flex items-center gap-3 px-4 py-3">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-700">{initials(editor.displayName)}</span>
              <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-700">{editor.displayName}</p><p className="truncate text-xs text-slate-400">{editor.email}{editor.jobTitle ? ` · ${editor.jobTitle}` : ''}</p></div>
              <button type="button" disabled={busyId === editor.id} onClick={() => removeEditor(editor)} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-500 disabled:opacity-40" title="Gỡ Editor"><Trash2 size={16}/></button>
            </div>) : <p className="px-4 py-6 text-center text-sm text-slate-400">Workflow chưa được giao cho Editor nào.</p>}
          </div>
        </section>

        <section className="rounded-xl border border-dashed border-blue-200 bg-blue-50/40 p-4">
          <p className="mb-3 text-sm font-bold text-slate-700">Thêm Editor</p>
          <div className="relative mb-2"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"/><input value={query} onChange={event => { setQuery(event.target.value); setSelectedId(''); }} className="input-field pl-9 text-sm" placeholder="Tìm theo tên, email hoặc chức danh..."/></div>
          <div className="flex gap-2"><select value={selectedId} onChange={event => setSelectedId(event.target.value)} className="input-field flex-1 bg-white text-sm"><option value="">Chọn tài khoản Editor hoặc Viewer</option>{available.map(user => <option key={user.id} value={user.id}>{user.email} ({user.systemRoles?.includes('EDITOR') ? 'Editor' : 'Viewer'})</option>)}</select><button type="button" disabled={!selectedId || !!busyId} onClick={addEditor} className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-40"><UserPlus size={16}/>Thêm</button></div>
          {!available.length && <p className="mt-2 text-xs text-slate-400">Không còn tài khoản Editor hoặc Viewer phù hợp với tìm kiếm.</p>}
        </section>
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
      </div>
    </div>
  </div>;
}

function initials(name = '') {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return parts.length > 1 ? `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase() : (parts[0] || 'ED').slice(0, 2).toUpperCase();
}
