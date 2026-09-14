import { useState } from 'react';
import { Search, ChevronRight, Zap, CheckCircle2, Eye, UserCheck, Bell, Cpu, CircleStop } from 'lucide-react';

const STEP_TYPES = [
  {
    type: 'START',
    label: 'Start',
    description: 'Điểm bắt đầu workflow',
    icon: Zap,
    bgColor: 'bg-green-50',
    iconColor: 'text-green-600',
    borderColor: 'border-green-200'
  },
  {
    type: 'APPROVAL',
    label: 'Approval Step',
    description: 'Bước phê duyệt',
    icon: CheckCircle2,
    bgColor: 'bg-orange-50',
    iconColor: 'text-orange-600',
    borderColor: 'border-orange-200'
  },
  {
    type: 'REVIEW',
    label: 'Review Step',
    description: 'Review nội dung/hồ sơ',
    icon: Eye,
    bgColor: 'bg-blue-50',
    iconColor: 'text-blue-600',
    borderColor: 'border-blue-200'
  },
  {
    type: 'ASSIGNMENT',
    label: 'Assignment Step',
    description: 'Giao việc cho user/group',
    icon: UserCheck,
    bgColor: 'bg-purple-50',
    iconColor: 'text-purple-600',
    borderColor: 'border-purple-200'
  },
  {
    type: 'NOTIFICATION',
    label: 'Notification Step',
    description: 'Gửi thông báo',
    icon: Bell,
    bgColor: 'bg-pink-50',
    iconColor: 'text-pink-600',
    borderColor: 'border-pink-200'
  },
  {
    type: 'SYSTEM_ACTION',
    label: 'System Action',
    description: 'Tự động xử lý dữ liệu',
    icon: Cpu,
    bgColor: 'bg-gray-50',
    iconColor: 'text-gray-600',
    borderColor: 'border-gray-200'
  },
  {
    type: 'END',
    label: 'End',
    description: 'Kết thúc workflow',
    icon: CircleStop,
    bgColor: 'bg-red-50',
    iconColor: 'text-red-600',
    borderColor: 'border-red-200'
  }
];

export default function AddStepPopup({ position, onSelect, onClose, hasStartStep, adding = false }) {
  const [search, setSearch] = useState('');

  const popupWidth = 320;
  const popupHeight = 430;
  const viewportWidth = typeof window === 'undefined' ? 1280 : window.innerWidth;
  const viewportHeight = typeof window === 'undefined' ? 800 : window.innerHeight;
  const preferredLeft = position?.x || 400;
  const left = preferredLeft + popupWidth <= viewportWidth - 12
    ? preferredLeft
    : Math.max(12, (position?.anchorX || preferredLeft) - popupWidth - 10);
  const top = Math.min(Math.max(64, position?.y || 200), Math.max(64, viewportHeight - popupHeight - 12));

  // Filter out START if already present, and filter by search text
  const filteredSteps = STEP_TYPES.filter(s => {
    if (s.type === 'START' && hasStartStep) return false;
    if (search && !s.label.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <>
      {/* Invisible backdrop to close popup */}
      <div className="fixed inset-0 z-40" onClick={onClose} />

      {/* Popup */}
      <div
        className="fixed z-[100] w-[320px] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-2xl"
        style={{ left, top }}
        role="dialog"
        aria-label="Chọn loại step"
      >
        {/* Search */}
        <div className="p-3 border-b border-gray-100">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Tìm kiếm step..."
              className="w-full pl-9 pr-3 py-2 border border-gray-200 rounded-lg text-sm outline-none focus:border-orange-400 transition-colors"
              value={search}
              onChange={e => setSearch(e.target.value)}
              autoFocus
            />
          </div>
        </div>

        {/* Step list */}
        <div className="max-h-[350px] overflow-auto">
          {filteredSteps.map(step => {
            const Icon = step.icon;
            return (
              <button
                type="button"
                key={step.type}
                disabled={adding}
                className="group flex w-full items-center gap-3 border-b border-gray-50 px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-orange-50 disabled:cursor-wait disabled:opacity-50"
                onClick={() => onSelect(step.type)}
              >
                <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${step.bgColor} ${step.iconColor}`}>
                  <Icon size={18} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-gray-800 group-hover:text-orange-700 transition-colors">
                    {step.label}
                  </div>
                  <div className="text-xs text-gray-500 truncate">{step.description}</div>
                </div>
                <ChevronRight size={16} className="text-gray-300 group-hover:text-orange-400 transition-colors flex-shrink-0" />
              </button>
            );
          })}
          {filteredSteps.length === 0 && (
            <div className="px-4 py-6 text-center text-sm text-gray-400">Không tìm thấy step nào</div>
          )}
        </div>
      </div>
    </>
  );
}
