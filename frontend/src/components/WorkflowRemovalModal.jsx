import { AlertTriangle, Trash2, X } from 'lucide-react';

export default function WorkflowRemovalModal({ workflow, busy, onClose, onConfirm }) {
  const hardDelete = workflow.status === 'DRAFT';

  return (
    <div
      className="fixed inset-0 z-[140] flex items-center justify-center bg-slate-900/55 p-4"
      onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}
    >
      <div role="dialog" aria-modal="true" aria-labelledby="remove-workflow-title" className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl">
        <header className="flex items-start gap-4 border-b border-slate-100 px-6 py-5">
          <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${hardDelete ? 'bg-red-50 text-red-500' : 'bg-amber-50 text-amber-500'}`}>
            {hardDelete ? <Trash2 size={21} /> : <AlertTriangle size={21} />}
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="remove-workflow-title" className="text-lg font-bold text-slate-800">
              {hardDelete ? 'Xác nhận xóa workflow?' : 'Xác nhận lưu trữ workflow?'}
            </h2>
            <p className="mt-1 truncate text-sm font-semibold text-slate-500">{workflow.name} · v{workflow.version}</p>
          </div>
          <button type="button" disabled={busy} onClick={onClose} aria-label="Đóng" className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40"><X size={18} /></button>
        </header>

        <div className="px-6 py-5">
          {hardDelete ? (
            <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700">
              Workflow Draft cùng toàn bộ step, connection và cấu hình liên quan sẽ bị xóa vĩnh viễn. Thao tác này không thể hoàn tác.
            </div>
          ) : (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
              <b>Workflow sẽ ngừng nhận yêu cầu mới.</b> Các instance đã tạo vẫn tiếp tục chạy trên đúng phiên bản hiện tại và dữ liệu lịch sử được giữ nguyên.
            </div>
          )}
        </div>

        <footer className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4">
          <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Hủy</button>
          <button type="button" disabled={busy} onClick={onConfirm} className={`flex min-w-36 items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50 ${hardDelete ? 'bg-red-500 hover:bg-red-600' : 'bg-orange-500 hover:bg-orange-600'}`}>
            {busy ? 'Đang xử lý...' : hardDelete ? 'Xóa workflow' : 'Lưu trữ workflow'}
          </button>
        </footer>
      </div>
    </div>
  );
}
