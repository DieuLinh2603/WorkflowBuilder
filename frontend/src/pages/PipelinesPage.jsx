import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { DatabaseZap, Play, Plus, Trash2, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';

export default function PipelinesPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', description: '' });
  const [error, setError] = useState('');
  const [runningId, setRunningId] = useState('');
  const load = async () => { const response = await apiFetch('/api/pipelines'); if (response.ok) setRows(await response.json()); };
  useEffect(() => { load(); }, []);

  const create = async event => {
    event.preventDefault(); setError('');
    const response = await apiFetch('/api/pipelines', { method: 'POST', body: JSON.stringify({
      name: form.name, description: form.description, businessKey: 'id',
      definition: { sources: [], joins: [], transforms: [] }, outputSchema: [], scheduleType: 'MANUAL', timezone: 'Asia/Ho_Chi_Minh',
    }) });
    if (!response.ok) return setError(await apiError(response));
    const pipeline = await response.json(); navigate(`/pipelines/${pipeline.id}`);
  };
  const run = async id => {
    setError('');
    setRunningId(id);
    try {
      const response = await apiFetch(`/api/pipelines/${id}/run`, { method: 'POST', toast: false });
      if (!response.ok) throw new Error(await apiError(response, 'Không thể chạy Pipeline'));
      const result = await response.json();
      if (result.status === 'SUCCESS' || result.status === 'NO_CHANGES') {
        const message = result.status === 'NO_CHANGES'
          ? 'Pipeline đã chạy xong, không có dữ liệu thay đổi.'
          : `Pipeline đã chạy thành công: ${result.outputCount || 0} dòng, ${result.changedCount || 0} dòng mới/thay đổi.`;
        window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
      } else {
        const stage = result.errorStage ? ` tại bước ${result.errorStage}` : '';
        const retry = result.status === 'RETRY' ? ' Hệ thống sẽ tự thử lại.' : '';
        const message = `Pipeline chưa chạy thành công${stage}: ${result.errorMessage || 'Không xác định được lỗi.'}${retry}`;
        setError(message);
        window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'error', message } }));
      }
    } catch (reason) {
      setError(reason.message);
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'error', message: reason.message } }));
    } finally {
      setRunningId('');
      await load();
    }
  };
  const remove = async pipeline => {
    if (!window.confirm(`Xóa pipeline “${pipeline.name}”? Dataset và lịch sử liên quan có thể bị ảnh hưởng.`)) return;
    const response = await apiFetch(`/api/pipelines/${pipeline.id}`, { method: 'DELETE' });
    if (!response.ok) return setError(await apiError(response));
    await load();
  };

  return <div className="space-y-5">
    <header className="flex items-center justify-between"><div><h1 className="text-2xl font-bold">Data Pipelines</h1><p className="text-sm text-gray-500">Hợp nhất nhiều nguồn thành dataset có version dùng chung.</p></div><button onClick={() => setCreating(true)} className="btn-primary flex items-center gap-2"><Plus size={17}/>Tạo pipeline</button></header>
    {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>}
    {creating && <form onSubmit={create} className="rounded-xl border border-orange-200 bg-white p-5 shadow-sm"><div className="flex items-center justify-between"><h2 className="font-bold">Thông tin pipeline mới</h2><button type="button" onClick={() => setCreating(false)} className="text-gray-400"><X size={18}/></button></div><div className="mt-4 grid gap-3 md:grid-cols-2"><input autoFocus required className="input-field" placeholder="Tên pipeline" value={form.name} onChange={event => setForm({ ...form, name: event.target.value })}/><textarea className="input-field min-h-20 md:row-span-2" placeholder="Mô tả mục đích và dữ liệu đầu ra" value={form.description} onChange={event => setForm({ ...form, description: event.target.value })}/></div><button className="btn-primary mt-4">Tạo và thiết kế</button></form>}
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{rows.map(pipeline => <article key={pipeline.id} className="rounded-xl border border-grayBorder bg-white p-5 shadow-sm"><div className="flex items-start gap-3"><span className="rounded-lg bg-orange-50 p-2 text-orange-600"><DatabaseZap/></span><div className="min-w-0 flex-1"><Link to={`/pipelines/${pipeline.id}`} className="font-bold hover:text-orange-600">{pipeline.name}</Link><p className="text-xs text-gray-500">{pipeline.status} · {pipeline.scheduleType} · v{pipeline.latestVersion || 0}</p><p className="mt-2 line-clamp-2 text-sm text-slate-600">{pipeline.description || 'Chưa có mô tả.'}</p></div><button onClick={() => remove(pipeline)} title="Xóa pipeline" className="text-gray-400 hover:text-red-500"><Trash2 size={17}/></button></div><div className="mt-4 flex gap-2"><Link className="rounded-lg border px-3 py-2 text-xs font-semibold" to={`/pipelines/${pipeline.id}`}>Thiết kế</Link><button disabled={pipeline.status !== 'PUBLISHED' || runningId === pipeline.id} onClick={() => run(pipeline.id)} className="flex items-center gap-1 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40"><Play size={14}/>{runningId === pipeline.id ? 'Đang chạy...' : 'Chạy'}</button></div></article>)}</div>
  </div>;
}
