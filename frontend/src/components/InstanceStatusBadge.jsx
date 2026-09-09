const STATUSES = {
  RUNNING: ['Đang xử lý', 'border-orange-200 bg-orange-50 text-orange-600'],
  COMPLETED: ['Hoàn thành', 'border-emerald-200 bg-emerald-50 text-emerald-700'],
  APPROVED: ['Đã duyệt', 'border-blue-200 bg-blue-50 text-blue-700'],
  REJECTED: ['Từ chối', 'border-red-200 bg-red-50 text-red-600'],
  CANCELLED: ['Đã hủy', 'border-slate-200 bg-slate-100 text-slate-500'],
  WITHDRAWN: ['Đã thu hồi', 'border-amber-200 bg-amber-50 text-amber-700'],
};

export default function InstanceStatusBadge({ value, className = '' }) {
  const [label, colors] = STATUSES[value] || [value || 'Không xác định', 'border-slate-200 bg-slate-100 text-slate-600'];
  return <span className={`inline-flex items-center rounded-md border px-2.5 py-1 text-[11px] font-bold ${colors} ${className}`}>{label}</span>;
}
