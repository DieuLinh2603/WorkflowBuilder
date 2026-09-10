import { AlertTriangle, Loader2, X } from 'lucide-react';

export default function DeletePipelineModal({ state, setState, onConfirm }) {
  const { pipeline, impact, loading, deleting, confirmation } = state;
  const matches = confirmation === pipeline.name;
  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/50 p-4" onMouseDown={() => !deleting && setState(null)}>
    <div role="dialog" aria-modal="true" aria-labelledby="delete-pipeline-title" className="w-full max-w-lg rounded-2xl bg-white shadow-2xl" onMouseDown={event => event.stopPropagation()}>
      <div className="flex items-start justify-between border-b px-6 py-5"><div className="flex gap-3"><span className="rounded-full bg-red-50 p-2 text-red-600"><AlertTriangle size={22}/></span><div><h2 id="delete-pipeline-title" className="font-bold text-slate-800">Xác nhận xóa pipeline</h2><p className="mt-1 text-sm text-gray-500">Thao tác này không thể hoàn tác.</p></div></div><button disabled={deleting} onClick={() => setState(null)} className="text-gray-400 hover:text-gray-600"><X size={19}/></button></div>
      <div className="space-y-4 px-6 py-5">
        {loading ? <div className="flex items-center gap-2 py-6 text-sm text-gray-500"><Loader2 className="animate-spin" size={18}/>Đang kiểm tra dữ liệu liên quan...</div> : <>
          <p className="text-sm text-slate-700">Bạn đang xóa pipeline <b>“{pipeline.name}”</b>.</p>
          <div className="rounded-xl border border-red-100 bg-red-50/60 p-4 text-sm text-slate-700"><p className="font-semibold text-red-700">Dữ liệu sẽ bị xóa cùng pipeline:</p><ul className="mt-2 list-disc space-y-1 pl-5"><li>{impact.runCount} lần chạy và toàn bộ lịch sử lỗi</li><li>{impact.fileCount} phiên bản file đã tải lên</li><li>{impact.datasetVersionCount} phiên bản dataset cùng các record</li><li>{impact.bindingCount} liên kết dataset với workflow</li></ul>{impact.instanceRecordCount > 0 && <p className="mt-3 font-medium text-amber-700">Dataset đang được tham chiếu bởi {impact.instanceRecordCount} bản ghi workflow.</p>}</div>
          {!impact.canDelete && <div className="rounded-lg bg-amber-50 p-3 text-sm font-medium text-amber-700">{impact.reason}</div>}
          {impact.canDelete && <label className="block text-sm text-slate-700">Nhập chính xác <b>{pipeline.name}</b> để xác nhận:<input autoFocus className="input-field mt-2" value={confirmation} onChange={event => setState(current => ({ ...current, confirmation: event.target.value }))} placeholder={pipeline.name}/></label>}
        </>}
      </div>
      <div className="flex justify-end gap-3 border-t px-6 py-4"><button disabled={deleting} onClick={() => setState(null)} className="rounded-lg border px-4 py-2 text-sm font-semibold text-slate-600">Hủy</button><button disabled={loading || deleting || !impact?.canDelete || !matches} onClick={onConfirm} className="flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40">{deleting && <Loader2 className="animate-spin" size={16}/>}Xóa pipeline</button></div>
    </div>
  </div>;
}
