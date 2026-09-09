import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Trash2, X } from 'lucide-react';

export default function DeleteStepModal({ step, steps, connections, onConfirm, onClose }) {
  const incoming = useMemo(() => connections.filter(connection => connection.toStepId === step.id), [connections, step.id]);
  const outgoing = useMemo(() => connections.filter(connection => connection.fromStepId === step.id), [connections, step.id]);
  const canReconnect = incoming.length > 0 && outgoing.length > 0;
  const [mode, setMode] = useState(canReconnect ? 'RECONNECT' : 'DISCONNECT');
  const [incomingId, setIncomingId] = useState(incoming[0]?.id || '');
  const [outgoingId, setOutgoingId] = useState(outgoing[0]?.id || '');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setMode(canReconnect ? 'RECONNECT' : 'DISCONNECT');
    setIncomingId(incoming[0]?.id || '');
    setOutgoingId(outgoing[0]?.id || '');
    setError('');
  }, [step.id, canReconnect, incoming, outgoing]);

  const stepName = id => steps.find(item => item.id === id)?.label || 'Node không xác định';
  const describeIncoming = connection => `${stepName(connection.fromStepId)} → ${step.label} · ${connection.type}`;
  const describeOutgoing = connection => `${step.label} → ${stepName(connection.toStepId)} · ${connection.type}`;
  const submit = async () => {
    setDeleting(true); setError('');
    try {
      await onConfirm(mode === 'RECONNECT' ? { incomingConnectionId: incomingId, outgoingConnectionId: outgoingId } : null);
    } catch (reason) {
      setError(reason.message || 'Không thể xóa step');
      setDeleting(false);
    }
  };

  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/35 p-4" onMouseDown={event => { if (event.target === event.currentTarget && !deleting) onClose(); }}>
    <div className="w-full max-w-xl rounded-2xl border border-gray-200 bg-white shadow-2xl">
      <header className="flex items-start justify-between border-b border-gray-100 px-6 py-5">
        <div className="flex gap-3"><div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-500"><Trash2 size={19} /></div><div><h2 className="text-base font-bold text-slate-800">Xóa step “{step.label}”?</h2><p className="mt-1 text-xs text-gray-500">{step.type} · {incoming.length} đường vào · {outgoing.length} đường ra</p></div></div>
        <button type="button" onClick={onClose} disabled={deleting} className="rounded-full p-1 text-gray-400 hover:bg-gray-100"><X size={18} /></button>
      </header>

      <div className="space-y-4 px-6 py-5">
        <div className="flex gap-2 rounded-lg border border-amber-100 bg-amber-50 p-3 text-xs leading-5 text-amber-800"><AlertTriangle size={17} className="mt-0.5 shrink-0" /><span>Step và tất cả connection liên quan sẽ bị xóa. Thao tác này không thể hoàn tác.</span></div>
        <label className={`block rounded-xl border p-4 ${canReconnect ? 'cursor-pointer' : 'cursor-not-allowed opacity-50'} ${mode === 'RECONNECT' ? 'border-orange-400 bg-orange-50/40' : 'border-gray-200'}`}>
          <span className="flex items-start gap-3"><input type="radio" name="delete-mode" disabled={!canReconnect} checked={mode === 'RECONNECT'} onChange={() => setMode('RECONNECT')} className="mt-1 accent-orange-500" /><span><b className="block text-sm text-slate-700">Xóa và nối lại</b><small className="mt-1 block text-xs leading-5 text-gray-500">Chọn một đường vào và một đường ra. Connection mới giữ loại và condition của đường vào.</small></span></span>
        </label>
        {mode === 'RECONNECT' && canReconnect && <div className="grid grid-cols-2 gap-3 rounded-xl bg-slate-50 p-4">
          <label className="text-xs font-semibold text-slate-600">Đường đi vào<select value={incomingId} onChange={event => setIncomingId(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-200 bg-white px-3 py-2.5 text-xs outline-none focus:border-orange-400">{incoming.map(connection => <option key={connection.id} value={connection.id}>{describeIncoming(connection)}</option>)}</select></label>
          <label className="text-xs font-semibold text-slate-600">Đường đi ra<select value={outgoingId} onChange={event => setOutgoingId(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-200 bg-white px-3 py-2.5 text-xs outline-none focus:border-orange-400">{outgoing.map(connection => <option key={connection.id} value={connection.id}>{describeOutgoing(connection)}</option>)}</select></label>
          {(incoming.length > 1 || outgoing.length > 1) && <p className="col-span-2 text-[11px] leading-4 text-amber-700">Chỉ cặp đã chọn được nối lại. Những nhánh còn lại sẽ bị xóa.</p>}
        </div>}
        <label className={`block cursor-pointer rounded-xl border p-4 ${mode === 'DISCONNECT' ? 'border-red-300 bg-red-50/40' : 'border-gray-200'}`}><span className="flex items-start gap-3"><input type="radio" name="delete-mode" checked={mode === 'DISCONNECT'} onChange={() => setMode('DISCONNECT')} className="mt-1 accent-red-500" /><span><b className="block text-sm text-slate-700">Xóa và ngắt kết nối</b><small className="mt-1 block text-xs leading-5 text-gray-500">Không tạo connection thay thế; bạn sẽ nối lại luồng thủ công trên canvas.</small></span></span></label>
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>}
      </div>

      <footer className="flex justify-end gap-3 border-t border-gray-100 px-6 py-4"><button type="button" onClick={onClose} disabled={deleting} className="rounded-lg border border-gray-200 px-5 py-2.5 text-sm font-medium text-gray-600">Hủy</button><button type="button" onClick={submit} disabled={deleting || (mode === 'RECONNECT' && (!incomingId || !outgoingId))} className="flex items-center gap-2 rounded-lg bg-red-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50"><Trash2 size={15} />{deleting ? 'Đang xóa...' : 'Xóa step'}</button></footer>
    </div>
  </div>;
}
