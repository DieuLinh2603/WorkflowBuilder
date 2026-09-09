import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, Clock3, Eye, FileEdit, GitBranch, Layers, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiError, apiFetch } from '../../api';
import InstanceStatusBadge from '../InstanceStatusBadge';
import Pagination from '../Pagination';

const PAGE_SIZE = 5;

const WORKFLOW_STATUS = {
  DRAFT: 'bg-slate-100 text-slate-700', PUBLISHED: 'bg-emerald-100 text-emerald-700',
  SUSPENDED: 'bg-amber-100 text-amber-700', ARCHIVED: 'bg-gray-100 text-gray-500',
};
const ROLE_COPY = {
  ADMIN: ['Dashboard quản trị hệ thống', 'Dữ liệu tổng quan được cập nhật trực tiếp từ hệ thống', 'Workflow cập nhật gần đây'],
  WORKFLOW_OWNER: ['Workflow của tôi', 'Theo dõi workflow do bạn sở hữu và các instance đang chạy', 'Workflow do bạn sở hữu'],
  EDITOR: ['Workflow được giao chỉnh sửa', 'Các workflow mà owner đã cấp quyền Editor cho bạn', 'Workflow được giao'],
  VIEWER: ['Tổng quan công việc của tôi', 'Yêu cầu đã gửi và các task đang chờ bạn xử lý', 'Yêu cầu gần đây'],
};

export default function RoleDashboard({ role }) {
  const navigate = useNavigate();
  const copy = ROLE_COPY[role] || ROLE_COPY.VIEWER;
  const [workflows, setWorkflows] = useState([]), [instances, setInstances] = useState([]), [tasks, setTasks] = useState([]);
  const [userCount, setUserCount] = useState(0), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true); setError('');
      const requests = role === 'VIEWER'
        ? [apiFetch('/api/instances/mine'), apiFetch('/api/my-tasks')]
        : [apiFetch('/api/workflows/managed'), apiFetch('/api/instances'), apiFetch('/api/my-tasks')];
      if (role === 'ADMIN') requests.push(apiFetch('/api/users?page=0&size=1'));
      try {
        const responses = await Promise.all(requests), failed = responses.find(response => !response.ok);
        if (failed) throw new Error(await apiError(failed, 'Không thể tải dữ liệu dashboard'));
        const payloads = await Promise.all(responses.map(response => response.json()));
        if (!active) return;
        if (role === 'VIEWER') { setInstances(payloads[0] || []); setTasks(payloads[1] || []); }
        else {
          setWorkflows(payloads[0] || []); setInstances(payloads[1] || []); setTasks(payloads[2] || []);
          if (role === 'ADMIN') setUserCount(payloads[3]?.totalElements || 0);
        }
      } catch (requestError) { if (active) setError(requestError.message || 'Không thể tải dữ liệu dashboard'); }
      finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, [role]);
  const stats = useMemo(() => buildStats(role, { workflows, instances, tasks, userCount }), [role, workflows, instances, tasks, userCount]);
  const sortedWorkflows = useMemo(() => [...workflows].sort(byNewest('createdAt')), [workflows]);
  const sortedInstances = useMemo(() => [...instances].sort(byNewest('startedAt')), [instances]);
  const tableItems = role === 'VIEWER' ? sortedInstances : sortedWorkflows;
  const pageRows = tableItems.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  useEffect(() => {
    const lastPage = Math.max(0, Math.ceil(tableItems.length / PAGE_SIZE) - 1);
    if (page > lastPage) setPage(lastPage);
  }, [page, tableItems.length]);
  useEffect(() => setPage(0), [role]);

  return <div className="space-y-6">
    <header><h1 className="text-2xl font-bold text-slate-800">{copy[0]}</h1><p className="mt-1 text-sm text-gray-500">{copy[1]}</p></header>
    {error && <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"><AlertCircle size={17}/>{error}</div>}
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{stats.map(stat => <div key={stat.label} className="rounded-xl border border-grayBorder bg-white p-5 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="text-sm font-medium text-gray-500">{stat.label}</p><p className="mt-1 text-3xl font-bold text-slate-800">{loading ? '—' : stat.value}</p><p className="mt-2 text-xs text-gray-400">{stat.note}</p></div><span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${stat.color}`}><stat.icon size={21}/></span></div></div>)}</section>
    <section className="overflow-hidden rounded-xl border border-grayBorder bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-grayBorder px-6 py-4"><h2 className="text-lg font-bold text-slate-800">{copy[2]}</h2><button type="button" onClick={() => navigate(role === 'VIEWER' ? '/my-work' : '/workflows')} className="text-sm font-semibold text-orange-600 hover:text-orange-700">Xem tất cả</button></div>
      {role === 'VIEWER' ? <InstanceTable rows={pageRows} loading={loading} onOpen={item => navigate(`/instances/${item.id}`)}/> : <WorkflowTable rows={pageRows} loading={loading} onOpen={item => navigate(`/workflows/${item.id}/design`)}/>} 
      {!loading && <Pagination page={page} totalItems={tableItems.length} pageSize={PAGE_SIZE} onPageChange={setPage} itemLabel={role === 'VIEWER' ? 'yêu cầu' : 'workflow'}/>} 
    </section>
  </div>;
}

function buildStats(role, { workflows, instances, tasks, userCount }) {
  const workflowFamilies = new Set(workflows.map(item => item.familyId || item.id)).size;
  const published = workflows.filter(item => item.status === 'PUBLISHED').length, drafts = workflows.filter(item => item.status === 'DRAFT').length;
  const running = instances.filter(item => item.status === 'RUNNING').length, completed = instances.filter(item => item.status === 'COMPLETED').length;
  if (role === 'ADMIN') return [
    stat('Tổng người dùng', userCount, 'Tài khoản trong hệ thống', Users, 'bg-blue-100 text-blue-600'),
    stat('Tổng workflow', workflowFamilies, `${published} phiên bản đang chạy`, GitBranch, 'bg-orange-100 text-orange-600'),
    stat('Instance đang chạy', running, `${instances.length} instance có thể truy cập`, Layers, 'bg-emerald-100 text-emerald-600'),
    stat('Task của tôi', tasks.length, 'Đang chờ xử lý', Clock3, 'bg-amber-100 text-amber-600')];
  if (role === 'WORKFLOW_OWNER') return [
    stat('Workflow của tôi', workflowFamilies, `${workflows.length} phiên bản quản lý`, GitBranch, 'bg-blue-100 text-blue-600'),
    stat('Đang Published', published, 'Đang nhận yêu cầu mới', CheckCircle2, 'bg-emerald-100 text-emerald-600'),
    stat('Đang Draft', drafts, 'Chờ hoàn thiện và publish', FileEdit, 'bg-orange-100 text-orange-600'),
    stat('Instance đang chạy', running, `${instances.length} instance thuộc workflow`, Layers, 'bg-purple-100 text-purple-600')];
  if (role === 'EDITOR') return [
    stat('Workflow được giao', workflowFamilies, `${workflows.length} phiên bản có thể truy cập`, GitBranch, 'bg-blue-100 text-blue-600'),
    stat('Bản Draft', drafts, 'Có thể tiếp tục cấu hình', FileEdit, 'bg-orange-100 text-orange-600'),
    stat('Đã Published', published, 'Phiên bản đang hoạt động', CheckCircle2, 'bg-emerald-100 text-emerald-600'),
    stat('Task của tôi', tasks.length, 'Đang chờ xử lý', Clock3, 'bg-purple-100 text-purple-600')];
  return [
    stat('Task đang chờ', tasks.length, 'Được giao trực tiếp cho bạn', Clock3, 'bg-orange-100 text-orange-600'),
    stat('Yêu cầu đang chạy', running, 'Do bạn gửi', Layers, 'bg-blue-100 text-blue-600'),
    stat('Đã hoàn thành', completed, 'Yêu cầu đã kết thúc', CheckCircle2, 'bg-emerald-100 text-emerald-600'),
    stat('Tổng yêu cầu', instances.length, 'Toàn bộ yêu cầu của bạn', GitBranch, 'bg-purple-100 text-purple-600')];
}
function stat(label, value, note, icon, color) { return { label, value, note, icon, color }; }

function WorkflowTable({ rows, loading, onOpen }) {
  return <div className="overflow-auto"><table className="w-full text-left"><thead><tr className="border-b border-grayBorder bg-slate-50 text-[11px] font-bold uppercase text-gray-500"><th className="px-6 py-3">Tên workflow</th><th className="px-4 py-3">Loại / Module</th><th className="px-4 py-3">Phiên bản</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Chủ sở hữu</th><th className="px-4 py-3">Ngày tạo</th><th className="px-4 py-3"></th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? <EmptyRow colSpan={7} text="Đang tải dữ liệu..."/> : rows.length ? rows.map(item => <tr key={item.id} className="text-sm hover:bg-slate-50/70"><td className="px-6 py-4 font-semibold text-slate-800">{item.name}</td><td className="px-4 py-4 text-gray-500">{[item.type, item.module].filter(Boolean).join(' · ') || '—'}</td><td className="px-4 py-4 text-gray-600">v{item.version}</td><td className="px-4 py-4"><span className={`rounded-md px-2.5 py-1 text-xs font-semibold ${WORKFLOW_STATUS[item.status] || WORKFLOW_STATUS.ARCHIVED}`}>{item.status}</span></td><td className="px-4 py-4 text-gray-600">{item.ownerName || '—'}</td><td className="px-4 py-4 text-gray-500">{formatDate(item.createdAt)}</td><td className="px-4 py-4"><OpenButton onClick={() => onOpen(item)}/></td></tr>) : <EmptyRow colSpan={7} text="Chưa có workflow phù hợp"/>}</tbody></table></div>;
}
function InstanceTable({ rows, loading, onOpen }) {
  return <div className="overflow-auto"><table className="w-full text-left"><thead><tr className="border-b border-grayBorder bg-slate-50 text-[11px] font-bold uppercase text-gray-500"><th className="px-6 py-3">Mã yêu cầu</th><th className="px-4 py-3">Workflow</th><th className="px-4 py-3">Bước hiện tại</th><th className="px-4 py-3">Trạng thái</th><th className="px-4 py-3">Bắt đầu</th><th className="px-4 py-3"></th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? <EmptyRow colSpan={6} text="Đang tải dữ liệu..."/> : rows.length ? rows.map(item => <tr key={item.id} className="text-sm hover:bg-slate-50/70"><td className="px-6 py-4 font-bold text-slate-800">{item.requestCode}</td><td className="px-4 py-4 font-medium text-slate-700">{item.workflowName}</td><td className="px-4 py-4 text-gray-500">{item.currentStepLabel || 'Đã kết thúc'}</td><td className="px-4 py-4"><InstanceStatusBadge value={item.status}/></td><td className="px-4 py-4 text-gray-500">{formatDate(item.startedAt)}</td><td className="px-4 py-4"><OpenButton onClick={() => onOpen(item)}/></td></tr>) : <EmptyRow colSpan={6} text="Bạn chưa gửi yêu cầu nào"/>}</tbody></table></div>;
}
function OpenButton({ onClick }) { return <button type="button" onClick={onClick} className="rounded-lg p-2 text-gray-400 hover:bg-orange-50 hover:text-orange-600" title="Xem chi tiết"><Eye size={16}/></button>; }
function EmptyRow({ colSpan, text }) { return <tr><td colSpan={colSpan} className="px-6 py-12 text-center text-sm text-gray-400">{text}</td></tr>; }
function formatDate(value) { return value ? new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—'; }
function byNewest(field) { return (left, right) => new Date(right[field] || 0) - new Date(left[field] || 0); }
