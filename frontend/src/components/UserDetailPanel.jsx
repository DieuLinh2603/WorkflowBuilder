import { BriefcaseBusiness, CalendarDays, Database, Edit2, Mail, ShieldCheck, UserRound, UsersRound, X } from 'lucide-react';

const ROLE_STYLES = {
  ADMIN: 'bg-red-100 text-red-700',
  WORKFLOW_OWNER: 'bg-orange-100 text-orange-700',
  EDITOR: 'bg-blue-100 text-blue-700',
  VIEWER: 'bg-gray-100 text-gray-700',
};

const ROLE_LABELS = {
  ADMIN: 'Admin',
  WORKFLOW_OWNER: 'Workflow Owner',
  EDITOR: 'Editor',
  VIEWER: 'Viewer',
};

export default function UserDetailPanel({ user, loading, error, onClose, onEdit }) {
  if (!user) return null;
  const roles = user.systemRoles || [];

  return <>
    <div className="fixed inset-0 z-40 bg-slate-900/45 backdrop-blur-[1px]" onMouseDown={onClose}/>
    <aside role="dialog" aria-modal="true" aria-label="Thông tin User" className="fixed inset-y-0 right-0 z-50 flex w-full max-w-[480px] flex-col border-l border-slate-200 bg-white shadow-2xl">
      <header className="flex items-start justify-between border-b border-slate-200 bg-slate-50/70 px-6 py-5">
        <div><h2 className="text-lg font-bold text-slate-800">Thông tin User</h2><p className="mt-1 text-xs text-gray-500">Thông tin tài khoản trong hệ thống</p></div>
        <button type="button" onClick={onClose} className="rounded-lg p-2 text-gray-400 hover:bg-white hover:text-slate-700" aria-label="Đóng"><X size={19}/></button>
      </header>

      <div className="flex-1 overflow-y-auto px-6 py-6">
        <section className="flex items-center gap-4 border-b border-slate-100 pb-6">
          <div className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-full text-lg font-bold text-white ${user.avatarColor || 'bg-slate-500'}`}>{user.avatarInitials || initials(user.displayName)}</div>
          <div className="min-w-0"><h3 className="truncate text-xl font-bold text-slate-800">{user.displayName || '—'}</h3><p className="mt-1 truncate text-sm text-gray-500">{user.email || '—'}</p><span className={`mt-2 inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold ${user.active === false ? 'bg-red-50 text-red-600' : 'bg-emerald-50 text-emerald-700'}`}>{user.active === false ? 'Đã vô hiệu hóa' : 'Đang hoạt động'}</span></div>
        </section>

        {loading && <div className="mt-5 rounded-lg bg-slate-50 px-4 py-3 text-sm text-gray-500">Đang tải thông tin chi tiết...</div>}
        {error && <div className="mt-5 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}

        <section className="mt-6 space-y-5">
          <DetailRow icon={Mail} label="Email" value={user.email}/>
          <DetailRow icon={BriefcaseBusiness} label="Chức danh" value={user.jobTitle}/>
          <DetailRow icon={UsersRound} label="Quản lý trực tiếp" value={user.managerName}/>
          <DetailRow icon={Database} label="Nguồn tài khoản" value={sourceLabel(user.dataSource)}/>
          <DetailRow icon={CalendarDays} label="Ngày tạo" value={formatDate(user.createdAt)}/>
          <DetailRow icon={CalendarDays} label="Cập nhật gần nhất" value={formatDate(user.updatedAt)}/>
        </section>

        <section className="mt-7 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
          <div className="mb-3 flex items-center gap-2"><ShieldCheck size={17} className="text-orange-500"/><h3 className="text-xs font-bold uppercase tracking-wide text-slate-600">Role hệ thống</h3></div>
          <div className="flex flex-wrap gap-2">{roles.length ? roles.map(role => <span key={role} className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${ROLE_STYLES[role] || 'bg-gray-100 text-gray-700'}`}>{ROLE_LABELS[role] || role}</span>) : <span className="rounded-lg bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-600">Chưa gán role</span>}</div>
          {!roles.length && <p className="mt-2 text-[11px] leading-5 text-red-500">Tài khoản cũ chưa có role. Vui lòng cập nhật và chọn Viewer hoặc role phù hợp.</p>}
        </section>

        <section className="mt-4 rounded-xl border border-sky-100 bg-sky-50/60 p-4"><h3 className="mb-3 text-xs font-bold uppercase tracking-wide text-slate-600">Module nghiệp vụ</h3><div className="flex flex-wrap gap-2">{user.moduleCodes?.length ? user.moduleCodes.map(code => <span key={code} className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-sky-700 shadow-sm">{code}</span>) : <span className="text-xs text-gray-500">Admin có quyền trên toàn bộ module</span>}</div></section>

        <section className="mt-5 rounded-xl border border-blue-100 bg-blue-50/60 p-4">
          <div className="flex gap-2 text-xs leading-5 text-blue-700"><UserRound size={16} className="mt-0.5 shrink-0"/><span>Mật khẩu và thông tin xác thực không được hiển thị nhằm bảo vệ tài khoản.</span></div>
        </section>
      </div>

      <footer className="flex shrink-0 justify-end gap-3 border-t border-slate-200 bg-slate-50/70 px-6 py-4">
        <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50">Đóng</button>
        <button type="button" onClick={onEdit} disabled={loading || !!error} className="flex items-center gap-2 rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"><Edit2 size={16}/>Chỉnh sửa</button>
      </footer>
    </aside>
  </>;
}

function DetailRow({ icon: Icon, label, value }) {
  return <div className="flex gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500"><Icon size={16}/></span><div className="min-w-0"><p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400">{label}</p><p className="mt-1 break-words text-sm font-medium text-slate-700">{value || '—'}</p></div></div>;
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
}

function sourceLabel(value) {
  if (!value) return '—';
  return value === 'MANUAL' ? 'Tạo thủ công bởi Admin' : value;
}

function initials(name) {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return 'NA';
  return `${parts[0][0]}${parts.length > 1 ? parts.at(-1)[0] : parts[0][1] || ''}`.toUpperCase();
}
