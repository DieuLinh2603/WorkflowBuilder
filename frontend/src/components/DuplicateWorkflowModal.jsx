import { useEffect, useMemo, useState } from 'react';
import { Copy, GitBranch, Info, X } from 'lucide-react';

export default function DuplicateWorkflowModal({ workflow, currentUser, fetchDropdownUsers, busy, error, onClose, onConfirm }) {
  const isAdmin = currentUser?.systemRoles?.includes('ADMIN');
  const [name, setName] = useState(`${workflow.name} - Bản sao`);
  const [ownerId, setOwnerId] = useState(currentUser?.id || '');
  const [owners, setOwners] = useState([]);
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (!isAdmin || !fetchDropdownUsers) return;
    fetchDropdownUsers().then(setOwners).catch(() => setOwners([]));
  }, [fetchDropdownUsers, isAdmin]);

  const selectedOwner = useMemo(() => owners.find(owner => owner.id === ownerId), [ownerId, owners]);

  const submit = () => {
    const trimmedName = name.trim();
    if (!trimmedName) return setValidationError('Tên workflow mới là bắt buộc.');
    setValidationError('');
    onConfirm({
      name: trimmedName,
      sourceVersionId: workflow.id,
      ...(isAdmin && ownerId ? { ownerId } : {}),
    });
  };

  return <div className="fixed inset-0 z-[150] flex items-center justify-center bg-slate-900/55 p-4" onMouseDown={event => event.target === event.currentTarget && !busy && onClose()}>
    <div role="dialog" aria-modal="true" aria-labelledby="duplicate-workflow-title" className="w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl">
      <header className="flex items-start gap-4 border-b border-slate-100 px-6 py-5">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600"><Copy size={21}/></span>
        <div className="min-w-0 flex-1">
          <h2 id="duplicate-workflow-title" className="text-lg font-bold text-slate-800">Nhân bản Workflow</h2>
          <p className="mt-1 text-sm text-slate-500">Tạo một workflow độc lập từ phiên bản hiện tại.</p>
        </div>
        <button type="button" disabled={busy} onClick={onClose} aria-label="Đóng" className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"><X size={18}/></button>
      </header>

      <div className="space-y-5 px-6 py-5">
        {(validationError || error) && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{validationError || error}</div>}

        <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Workflow nguồn</p>
          <div className="mt-2 flex items-center gap-3">
            <GitBranch size={18} className="shrink-0 text-orange-500"/>
            <div className="min-w-0 flex-1"><p className="truncate text-sm font-bold text-slate-800">{workflow.name}</p><p className="mt-0.5 text-xs text-slate-500">v{workflow.version} · {workflow.status} · {workflow.module || 'Chưa có module'}</p></div>
          </div>
        </div>

        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold text-slate-700">Tên workflow mới <span className="text-red-500">*</span></span>
          <input autoFocus maxLength={200} value={name} onChange={event => { setName(event.target.value); setValidationError(''); }} onKeyDown={event => event.key === 'Enter' && !busy && submit()} className="input-field" placeholder="Nhập tên workflow mới"/>
          <span className="mt-1 block text-right text-[10px] text-slate-400">{name.length}/200</span>
        </label>

        {isAdmin && <label className="block">
          <span className="mb-1.5 block text-sm font-semibold text-slate-700">Người sở hữu workflow mới</span>
          <select value={ownerId} onChange={event => setOwnerId(event.target.value)} className="input-field bg-white">
            {!owners.some(owner => owner.id === ownerId) && <option value={ownerId}>{currentUser?.displayName || currentUser?.name || 'System Admin'}</option>}
            {owners.map(owner => <option key={owner.id} value={owner.id}>{owner.displayName}</option>)}
          </select>
          {selectedOwner && <span className="mt-1 block text-xs text-slate-400">Workflow mới sẽ thuộc về {selectedOwner.displayName}.</span>}
        </label>}

        <div className="flex gap-3 rounded-xl border border-blue-100 bg-blue-50/60 px-4 py-3 text-sm leading-6 text-blue-800">
          <Info size={18} className="mt-0.5 shrink-0"/>
          <div><b>Bản sao được tạo ở trạng thái DRAFT, phiên bản v1.0.</b><p className="text-xs text-blue-700">Các bước, field, điều kiện, kết nối và cấu hình sẽ được sao chép. Instance, lịch sử xử lý và Data Binding không được sao chép.</p></div>
        </div>
      </div>

      <footer className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4">
        <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Hủy</button>
        <button type="button" disabled={busy || !name.trim()} onClick={submit} className="flex min-w-44 items-center justify-center gap-2 rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"><Copy size={16}/>{busy ? 'Đang nhân bản...' : 'Nhân bản & chỉnh sửa'}</button>
      </footer>
    </div>
  </div>;
}
