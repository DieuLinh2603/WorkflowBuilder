import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, ArrowLeft, Check, Eye, GitCompare, Minus, Pencil, Plus, RotateCcw, Search, X } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiFetch } from '../api';

export default function WorkflowVersionHistoryPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState([]);
  const [comparison, setComparison] = useState(null);
  const [comparing, setComparing] = useState(false);
  const [restoring, setRestoring] = useState('');
  const [pendingRestore, setPendingRestore] = useState(null);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await apiFetch(`/api/workflows/${id}/versions`);
      if (!response.ok) throw new Error(await apiError(response));
      const items = await response.json();
      setVersions(items);
      setSelected(items.slice(0, 2).map((item) => item.id));
    } catch (reason) {
      setError(reason.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const workflow = versions[0];
  const filtered = useMemo(() => versions.filter((item) =>
    `${item.version} ${item.ownerName} ${item.status} ${item.changes?.join(' ')}`
      .toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))), [versions, query]);

  const toggle = (versionId) => setSelected((current) => (
    current.includes(versionId)
      ? current.filter((item) => item !== versionId)
      : current.length < 2 ? [...current, versionId] : [current[1], versionId]
  ));

  const compare = async () => {
    if (selected.length !== 2) return;
    setComparing(true);
    setError('');
    try {
      const response = await apiFetch(`/api/workflows/${selected[0]}/compare/${selected[1]}`);
      if (!response.ok) throw new Error(await apiError(response));
      setComparison(await response.json());
    } catch (reason) {
      setError(reason.message);
    } finally {
      setComparing(false);
    }
  };

  const restore = async () => {
    const version = pendingRestore;
    if (!version) return;
    setRestoring(version.id);
    setError('');
    try {
      const response = await apiFetch(`/api/workflows/${version.id}/restore`, {
        method: 'POST',
        successMessage: `Đã kích hoạt lại phiên bản v${version.version}.`,
      });
      if (!response.ok) throw new Error(await apiError(response));
      setPendingRestore(null);
      await load();
    } catch (reason) {
      setError(reason.message);
    } finally {
      setRestoring('');
    }
  };

  return (
    <div className="flex min-h-full flex-col overflow-hidden rounded-xl border border-grayBorder bg-white shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-grayBorder px-6 py-4">
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => navigate('/workflows')} className="rounded-lg p-2 text-gray-400 hover:bg-gray-100"><ArrowLeft size={18} /></button>
          <div>
            <h1 className="text-lg font-bold text-slate-800">Lịch sử phiên bản — {workflow?.name || 'Workflow'}</h1>
            <p className="mt-0.5 text-xs text-gray-400">Theo dõi, so sánh và kích hoạt lại các phiên bản thiết kế quy trình</p>
          </div>
        </div>
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={query} onChange={(event) => setQuery(event.target.value)} className="w-64 rounded-lg border border-grayBorder py-2 pl-9 pr-3 text-sm outline-none focus:border-orange-400" placeholder="Tìm phiên bản..." />
        </div>
      </header>

      <div className="flex items-center justify-between border-b border-grayBorder bg-slate-50/70 px-6 py-3">
        <div><p className="text-sm font-semibold text-slate-700">Danh sách phiên bản</p><p className="mt-0.5 text-[10px] text-gray-400">Chọn đúng 2 phiên bản để so sánh</p></div>
        <button type="button" onClick={compare} disabled={selected.length !== 2 || comparing} className="flex items-center gap-2 rounded-lg border border-grayBorder bg-white px-4 py-2 text-xs font-semibold text-slate-600 hover:border-orange-300 hover:text-orange-600 disabled:opacity-40"><GitCompare size={15} />{comparing ? 'Đang so sánh...' : 'So sánh phiên bản'}</button>
      </div>

      {error && <div className="mx-6 mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

      <div className="flex-1 overflow-auto bg-slate-50/50 px-6 py-5">
        {loading ? <div className="py-16 text-center text-sm text-gray-400">Đang tải lịch sử phiên bản...</div> : !filtered.length ? <div className="py-16 text-center text-sm text-gray-400">Không tìm thấy phiên bản phù hợp</div> : (
          <div className="relative space-y-5 before:absolute before:bottom-5 before:left-[23px] before:top-5 before:w-px before:bg-slate-200">
            {filtered.map((version) => (
              <article key={version.id} className="relative grid grid-cols-[48px_1fr] gap-4">
                <button type="button" onClick={() => toggle(version.id)} className={`relative z-10 flex h-12 w-12 items-center justify-center rounded-full border-4 border-slate-50 text-[11px] font-bold shadow-sm ${selected.includes(version.id) ? 'bg-orange-500 text-white' : 'bg-white text-slate-500'}`}>{selected.includes(version.id) ? <Check size={15} /> : `v${version.version}`}</button>
                <div>
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <button type="button" onClick={() => navigate(`/workflows/${version.id}/design`)} className="text-sm font-bold text-slate-800 hover:text-orange-600">v{version.version} · {version.ownerName}</button>
                    <Status status={version.status} />
                    <span className="text-[10px] text-gray-400">{formatDate(version.updatedAt || version.createdAt)}</span>
                    <span className="text-[10px] text-gray-400">{version.stepCount} steps · {version.fieldCount} fields · {version.connectionCount} connections</span>
                    <div className="ml-auto flex items-center gap-2">
                      <button type="button" onClick={() => navigate(`/workflows/${version.id}/design`)} className="flex items-center gap-1 rounded-md border border-grayBorder bg-white px-2.5 py-1.5 text-[10px] font-semibold text-slate-500 hover:text-orange-600"><Eye size={13} />Xem</button>
                      {version.canRestore && <button type="button" onClick={() => setPendingRestore(version)} disabled={!!restoring} className="flex items-center gap-1 rounded-md bg-orange-500 px-2.5 py-1.5 text-[10px] font-semibold text-white hover:bg-orange-600 disabled:opacity-50"><RotateCcw size={13} />{restoring === version.id ? 'Đang kích hoạt...' : 'Kích hoạt lại'}</button>}
                    </div>
                  </div>
                  <div className="rounded-xl border border-grayBorder bg-white px-4 py-3 shadow-sm">
                    <p className="mb-2 text-[10px] font-bold uppercase tracking-wide text-slate-500">Chi tiết thay đổi</p>
                    <div className="space-y-1.5">{version.changes?.map((change, index) => <Change key={`${change}-${index}`} text={change} />)}</div>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      {comparison && <ComparisonModal comparison={comparison} onClose={() => setComparison(null)} />}
      {pendingRestore && <RestoreModal version={pendingRestore} busy={!!restoring} onClose={() => !restoring && setPendingRestore(null)} onConfirm={restore} />}
    </div>
  );
}

function RestoreModal({ version, busy, onClose, onConfirm }) {
  return <div className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/50 p-4" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><div role="dialog" aria-modal="true" className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl">
    <header className="flex items-start gap-4 border-b border-slate-100 px-6 py-5"><span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-500"><AlertTriangle size={21} /></span><div className="flex-1"><h2 className="text-lg font-bold text-slate-800">Kích hoạt lại phiên bản v{version.version}?</h2><p className="mt-1 text-sm text-slate-500">{version.name}</p></div><button type="button" disabled={busy} onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100"><X size={18} /></button></header>
    <div className="px-6 py-5"><div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">Phiên bản v{version.version} sẽ chuyển trực tiếp thành <b>PUBLISHED</b>. Phiên bản đang chạy hiện tại chuyển thành <b>SUSPENDED</b>. Không tạo thêm version mới và các instance đã tồn tại không bị thay đổi.</div></div>
    <footer className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4"><button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600">Hủy</button><button type="button" disabled={busy} onClick={onConfirm} className="min-w-36 rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-orange-600 disabled:opacity-50">{busy ? 'Đang kích hoạt...' : 'Kích hoạt phiên bản'}</button></footer>
  </div></div>;
}

function Status({ status }) {
  const style = status === 'PUBLISHED' ? 'bg-emerald-50 text-emerald-600' : status === 'DRAFT' ? 'bg-orange-50 text-orange-600' : status === 'SUSPENDED' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500';
  return <span className={`rounded-full px-2 py-0.5 text-[9px] font-bold ${style}`}>{status}</span>;
}

function Change({ text }) {
  const add = text.startsWith('Thêm') || text.startsWith('Tạo');
  const remove = text.startsWith('Xóa');
  const Icon = add ? Plus : remove ? Minus : Pencil;
  const style = add ? 'bg-emerald-500' : remove ? 'bg-red-500' : 'bg-blue-500';
  return <div className="flex items-start gap-2 text-xs text-slate-600"><span className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded text-white ${style}`}><Icon size={10} /></span><span>{text}</span></div>;
}

function ComparisonModal({ comparison, onClose }) {
  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 p-4"><div className="w-full max-w-xl rounded-2xl bg-white shadow-2xl"><header className="flex items-center justify-between border-b border-grayBorder px-6 py-5"><div><h2 className="font-bold text-slate-800">So sánh phiên bản</h2><p className="mt-1 text-xs text-gray-400">v{comparison.fromVersion} → v{comparison.toVersion}</p></div><button type="button" onClick={onClose} className="rounded-full p-2 text-gray-400 hover:bg-gray-100"><X size={17} /></button></header><div className="max-h-[55vh] space-y-3 overflow-auto px-6 py-5">{comparison.changes?.map((change, index) => <div key={`${change}-${index}`} className="rounded-lg border border-grayBorder bg-slate-50 p-3"><Change text={change} /></div>)}</div><footer className="flex justify-end border-t border-grayBorder px-6 py-4"><button type="button" onClick={onClose} className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-semibold text-white">Đóng</button></footer></div></div>;
}

function formatDate(value) { return value ? new Date(value).toLocaleString('vi-VN') : '-'; }
async function apiError(response) { const body = await response.json().catch(() => null); return body?.message || `HTTP ${response.status}`; }
