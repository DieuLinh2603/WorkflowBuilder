import { CheckCircle2, Circle, PlayCircle, XCircle } from 'lucide-react';

const FAILED_ACTIONS = new Set(['REJECT', 'REVIEW_NOT_PASSED', 'AUTO_REJECTED', 'CANCELLED', 'REQUEST_WITHDRAWN']);

export default function InstanceJourney({ history = [], instance, compact = false }) {
  const visits = buildVisits(history);
  return <div className={`relative pl-7 before:absolute before:bottom-3 before:left-[9px] before:top-2 before:w-px before:bg-slate-200 ${compact ? 'space-y-5' : 'space-y-6'}`}>
    {visits.map((visit, index) => {
      const current = instance?.status === 'RUNNING' && visit.stepId === instance.currentStepId
        && !visits.slice(index + 1).some(item => item.stepId === visit.stepId);
      const failed = visit.actions.some(item => FAILED_ACTIONS.has(item.action));
      const completed = !current && (visit.actions.length > 0 || index < visits.length - 1 || instance?.status !== 'RUNNING');
      const Icon = failed ? XCircle : current ? PlayCircle : completed ? CheckCircle2 : Circle;
      return <div key={visit.key} className="relative">
        <Icon size={compact ? 18 : 19} className={`absolute -left-7 top-0 bg-white ${failed ? 'text-red-500' : current ? 'text-orange-500' : completed ? 'text-emerald-500' : 'text-slate-300'}`} />
        <div className="flex items-start justify-between gap-2">
          <p className={`text-sm font-semibold ${current ? 'text-orange-600' : 'text-slate-700'}`}>{visit.stepLabel}</p>
          <span className={`shrink-0 rounded-full px-2 py-0.5 text-[9px] font-bold ${failed ? 'bg-red-50 text-red-600' : current ? 'bg-orange-50 text-orange-600' : 'bg-emerald-50 text-emerald-600'}`}>{failed ? 'KHÔNG ĐẠT' : current ? 'ĐANG XỬ LÝ' : 'ĐÃ QUA'}</span>
        </div>
        <p className="mt-1 text-[10px] text-gray-400">Bắt đầu {formatDate(visit.startedAt)}</p>
        {visit.actions.map(action => <div key={action.id} className="mt-2 rounded-lg bg-slate-50 px-3 py-2">
          <p className="text-[11px] text-slate-600"><b>{labelAction(action.action)}</b>{action.actorName && ` · ${action.actorName}`} · {formatDate(action.actedAt)}</p>
          {action.comment && <p className="mt-1 text-[11px] leading-4 text-slate-500">{action.comment}</p>}
        </div>)}
        {current && !visit.actions.length && <p className="mt-1 text-xs text-slate-500">Đang chờ người được giao xử lý.</p>}
        {current && visit.actions.length > 0 && <p className="mt-2 text-[10px] text-orange-600">Đã có {visit.actions.length} người xử lý, đang chờ đạt điều kiện chuyển bước.</p>}
      </div>;
    })}
    {!visits.length && <p className="text-xs text-gray-400">Chưa có lịch sử xử lý.</p>}
  </div>;
}

function buildVisits(history) {
  const visits = [];
  history.forEach(item => {
    if (item.action === 'SUBMITTED' || item.action === 'STEP_ACTIVATED') {
      visits.push({ key: item.id, stepId: item.stepId, stepLabel: item.stepLabel, stepType: item.stepType, startedAt: item.actedAt, actions: item.action === 'SUBMITTED' ? [item] : [] });
      return;
    }
    const visit = [...visits].reverse().find(candidate => candidate.stepId === item.stepId);
    if (visit) visit.actions.push(item);
    else visits.push({ key: item.id, stepId: item.stepId, stepLabel: item.stepLabel, stepType: item.stepType, startedAt: item.actedAt, actions: [item] });
  });
  return visits;
}

export function labelAction(value) {
  return ({ SUBMITTED: 'Khởi tạo request', APPROVE: 'Phê duyệt', REJECT: 'Từ chối', COMPLETE: 'Hoàn thành', REVIEW_NOT_PASSED: 'Review không đạt', AUTO_APPROVED: 'Tự động duyệt', AUTO_REJECTED: 'Tự động từ chối', SYSTEM_ACTION_COMPLETED: 'System Action hoàn tất', SYSTEM_ACTION_FAILED: 'System Action thất bại', CANCELLED: 'Hủy yêu cầu', REQUEST_WITHDRAWN: 'Thu hồi yêu cầu' })[value] || value;
}

function formatDate(value) { return value ? new Date(value).toLocaleString('vi-VN') : '—'; }
