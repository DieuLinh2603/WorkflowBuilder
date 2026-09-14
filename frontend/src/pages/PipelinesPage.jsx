import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { DatabaseZap, PauseCircle, Play, Plus, RotateCw, Share2, Trash2, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';
import DeletePipelineModal from '../components/DeletePipelineModal';
import { useAuth } from '../context/AuthContext';

export default function PipelinesPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [rows, setRows] = useState([]);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', description: '' });
  const [error, setError] = useState('');
  const [runningId, setRunningId] = useState('');
  const [changingRunId, setChangingRunId] = useState('');
  const [deleteState, setDeleteState] = useState(null);
  const [owners, setOwners] = useState([]);
  const [shareState, setShareState] = useState(null);

  const load = async () => {
    const response = await apiFetch('/api/pipelines');
    if (response.ok) setRows(await response.json());
  };
  useEffect(() => {
    load();
    if (hasRole('ADMIN')) apiFetch('/api/users/active').then(async response => {
      if (response.ok) setOwners((await response.json()).filter(user => user.systemRoles?.includes('WORKFLOW_OWNER')));
    });
    const timer = window.setInterval(load, 3000);
    return () => window.clearInterval(timer);
  }, [hasRole]);

  const share = async () => {
    if (!shareState) return;
    const response = await apiFetch(`/api/pipelines/${shareState.pipeline.id}/share`, {
      method: 'PUT', body: JSON.stringify({ userId: shareState.userId || null }),
      successMessage: shareState.userId ? 'Đã chia sẻ pipeline cho Workflow Owner.' : 'Đã gỡ chia sẻ pipeline.',
    });
    if (!response.ok) return setError(await apiError(response, 'Không thể cập nhật quyền chia sẻ pipeline.'));
    setShareState(null); await load();
  };

  const create = async event => {
    event.preventDefault(); setError('');
    const response = await apiFetch('/api/pipelines', {
      method: 'POST', successMessage: `Đã tạo pipeline “${form.name.trim()}”.`, body: JSON.stringify({
        name: form.name.trim(), description: form.description.trim(), businessKey: 'id',
        definition: { sources: [], joins: [], transforms: [] }, outputSchema: [], scheduleType: 'MANUAL', timezone: 'Asia/Ho_Chi_Minh',
      }),
    });
    if (!response.ok) return setError(await apiError(response));
    const pipeline = await response.json(); navigate(`/pipelines/${pipeline.id}`);
  };

  const run = async id => {
    setError(''); setRunningId(id);
    try {
      const response = await apiFetch(`/api/pipelines/${id}/run`, { method: 'POST', toast: false });
      if (!response.ok) throw new Error(await apiError(response, 'Không thể chạy pipeline.'));
      const result = await response.json();
      if (result.status === 'PAUSED') {
        window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'info', message: 'Lượt chạy pipeline đã được tạm ngừng.' } }));
      } else if (result.status === 'SUCCESS' || result.status === 'NO_CHANGES') {
        const message = result.status === 'NO_CHANGES'
          ? 'Pipeline đã chạy xong, không có dữ liệu thay đổi.'
          : `Pipeline đã chạy xong: ${result.outputCount || 0} dòng, ${result.changedCount || 0} dòng mới hoặc thay đổi.`;
        window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
      } else {
        const stage = result.errorStage ? ` tại bước ${result.errorStage}` : '';
        const retry = result.status === 'RETRY' ? ' Hệ thống sẽ tự thử lại.' : '';
        throw new Error(`Pipeline chưa chạy xong${stage}: ${result.errorMessage || 'Không xác định được lỗi.'}${retry}`);
      }
    } catch (reason) {
      setError(reason.message);
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'error', message: reason.message } }));
    } finally {
      setRunningId(''); await load();
    }
  };

  const pause = async id => {
    setChangingRunId(id); setError('');
    const response = await apiFetch(`/api/pipelines/${id}/pause`, { method: 'POST', toast: false });
    if (!response.ok) setError(await apiError(response, 'Không thể tạm ngừng pipeline.'));
    else window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message: 'Đã tạm ngừng lượt chạy pipeline.' } }));
    setChangingRunId(''); await load();
  };

  const resume = async (pipelineId, runId) => {
    setChangingRunId(pipelineId); setError('');
    const response = await apiFetch(`/api/pipelines/${pipelineId}/runs/${runId}/resume`, { method: 'POST', toast: false });
    if (!response.ok) setError(await apiError(response, 'Không thể tiếp tục lượt chạy pipeline.'));
    else {
      const result = await response.json();
      const message = result.status === 'PAUSED' ? 'Lượt chạy vẫn đang tạm ngừng.' : 'Đã tiếp tục và xử lý lượt chạy pipeline.';
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
    }
    setChangingRunId(''); await load();
  };

  const askDelete = async pipeline => {
    setError('');
    setDeleteState({ pipeline, loading: true, impact: null, confirmation: '', deleting: false });
    const response = await apiFetch(`/api/pipelines/${pipeline.id}/deletion-impact`, { toast: false });
    if (!response.ok) {
      setDeleteState(null); setError(await apiError(response, 'Không thể kiểm tra dữ liệu liên quan đến pipeline.')); return;
    }
    const impact = await response.json();
    setDeleteState(current => current ? { ...current, loading: false, impact } : null);
  };

  const confirmDelete = async () => {
    if (!deleteState?.impact?.canDelete || deleteState.confirmation !== deleteState.pipeline.name) return;
    setDeleteState(current => ({ ...current, deleting: true }));
    const response = await apiFetch(`/api/pipelines/${deleteState.pipeline.id}`, {
      method: 'DELETE', successMessage: `Đã xóa pipeline “${deleteState.pipeline.name}” cùng dữ liệu liên quan.`,
    });
    if (!response.ok) {
      setDeleteState(current => ({ ...current, deleting: false }));
      setError(await apiError(response)); return;
    }
    setDeleteState(null); await load();
  };

  return <div className="space-y-5">
    <header className="flex items-center justify-between"><div><h1 className="text-2xl font-bold">Data Pipelines</h1><p className="text-sm text-gray-500">Hợp nhất nhiều nguồn thành dataset có version dùng chung.</p></div><button onClick={() => setCreating(true)} className="btn-primary flex items-center gap-2"><Plus size={17}/>Tạo pipeline</button></header>
    {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>}
    {creating && <form onSubmit={create} className="rounded-xl border border-orange-200 bg-white p-5 shadow-sm"><div className="flex items-center justify-between"><h2 className="font-bold">Thông tin pipeline mới</h2><button type="button" onClick={() => setCreating(false)} className="text-gray-400"><X size={18}/></button></div><div className="mt-4 grid gap-3 md:grid-cols-2"><input autoFocus required className="input-field" placeholder="Tên pipeline" value={form.name} onChange={event => setForm({ ...form, name: event.target.value })}/><textarea className="input-field min-h-20 md:row-span-2" placeholder="Mô tả mục đích và dữ liệu đầu ra" value={form.description} onChange={event => setForm({ ...form, description: event.target.value })}/></div><button className="btn-primary mt-4">Tạo và thiết kế</button></form>}
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{rows.map(pipeline => { const activeRun = pipeline.activeRun; const active = runningId === pipeline.id || ['QUEUED', 'RUNNING', 'RETRY'].includes(activeRun?.status); return <article key={pipeline.id} className="rounded-xl border border-grayBorder bg-white p-5 shadow-sm"><div className="flex items-start gap-3"><span className="rounded-lg bg-orange-50 p-2 text-orange-600"><DatabaseZap/></span><div className="min-w-0 flex-1"><Link to={`/pipelines/${pipeline.id}`} className="font-bold hover:text-orange-600">{pipeline.name}</Link><p className="text-xs text-gray-500">{pipeline.status} · {pipeline.scheduleType} · v{pipeline.latestVersion || 0}</p>{pipeline.sharedWithName&&<p className="mt-1 text-[11px] font-semibold text-blue-600">Chia sẻ với: {pipeline.sharedWithName}</p>}{activeRun && <p className={`mt-1 text-[11px] font-semibold ${activeRun.status === 'PAUSED' ? 'text-amber-600' : 'text-blue-600'}`}>{activeRun.status === 'PAUSED' ? 'Lượt chạy đang tạm ngừng' : 'Lượt chạy đang xử lý'}</p>}<p className="mt-2 line-clamp-2 text-sm text-slate-600">{pipeline.description || 'Chưa có mô tả.'}</p></div><div className="flex gap-2">{hasRole('ADMIN')&&<button onClick={()=>setShareState({pipeline,userId:pipeline.sharedWithId||''})} title="Chia sẻ pipeline" className="text-gray-400 hover:text-blue-600"><Share2 size={17}/></button>}{pipeline.canDelete&&<button onClick={() => askDelete(pipeline)} title="Xóa pipeline" className="text-gray-400 hover:text-red-500"><Trash2 size={17}/></button>}</div></div><div className="mt-4 flex flex-wrap gap-2"><Link className="rounded-lg border px-3 py-2 text-xs font-semibold" to={`/pipelines/${pipeline.id}`}>Thiết kế</Link>{active ? <button disabled={changingRunId === pipeline.id} onClick={() => pause(pipeline.id)} className="flex items-center gap-1 rounded-lg border border-amber-300 px-3 py-2 text-xs font-semibold text-amber-700 disabled:opacity-40"><PauseCircle size={14}/>Tạm ngừng</button> : activeRun?.status === 'PAUSED' ? <button disabled={changingRunId === pipeline.id} onClick={() => resume(pipeline.id, activeRun.id)} className="flex items-center gap-1 rounded-lg border border-emerald-300 px-3 py-2 text-xs font-semibold text-emerald-700 disabled:opacity-40"><RotateCw size={14}/>Tiếp tục</button> : <button disabled={pipeline.status !== 'PUBLISHED' || changingRunId === pipeline.id} onClick={() => run(pipeline.id)} className="flex items-center gap-1 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40"><Play size={14}/>Chạy</button>}</div></article>; })}</div>
    {deleteState && <DeletePipelineModal state={deleteState} setState={setDeleteState} onConfirm={confirmDelete}/>}
    {shareState&&<div className="fixed inset-0 z-[160] flex items-center justify-center bg-slate-900/50 p-4" onMouseDown={event=>event.target===event.currentTarget&&setShareState(null)}><div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl"><div className="flex items-center justify-between"><div><h2 className="font-bold text-slate-800">Chia sẻ pipeline</h2><p className="mt-1 text-sm text-slate-500">{shareState.pipeline.name}</p></div><button onClick={()=>setShareState(null)} className="text-slate-400"><X size={18}/></button></div><label className="mt-5 block text-sm font-semibold text-slate-700">Workflow Owner<select value={shareState.userId} onChange={event=>setShareState(current=>({...current,userId:event.target.value}))} className="input-field mt-2 bg-white"><option value="">— Không chia sẻ —</option>{owners.filter(owner=>owner.id!==shareState.pipeline.ownerId).map(owner=><option key={owner.id} value={owner.id}>{owner.displayName} · {owner.email}</option>)}</select></label><p className="mt-2 text-xs text-slate-400">Chỉ một Workflow Owner được truy cập pipeline tại một thời điểm.</p><div className="mt-6 flex justify-end gap-2"><button onClick={()=>setShareState(null)} className="rounded-lg border px-4 py-2 text-sm font-semibold text-slate-600">Hủy</button><button onClick={share} className="btn-primary px-4 py-2 text-sm">Lưu chia sẻ</button></div></div></div>}
  </div>;
}
