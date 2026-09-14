import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, BarChart3, BriefcaseBusiness, FileText, Headphones, Search, ShoppingCart, Sparkles, UserPlus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiError, apiFetch } from '../api';

const CARD_ICONS = [BriefcaseBusiness, ShoppingCart, FileText, Headphones, BarChart3, UserPlus];

export default function CatalogPage() {
  const navigate = useNavigate();
  const [workflows, setWorkflows] = useState([]);
  const [query, setQuery] = useState('');
  const [moduleFilter, setModuleFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      const response = await apiFetch('/api/workflows/catalog');
      if (!active) return;
      if (response.ok) setWorkflows(await response.json());
      else setError(await apiError(response, 'Không thể tải danh sách form được cấp quyền'));
      setLoading(false);
    })();
    return () => { active = false; };
  }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase('vi');
    return workflows.filter(workflow => (!keyword || `${workflow.name} ${workflow.description || ''} ${workflow.moduleName || workflow.module || ''} ${workflow.typeName || workflow.type || ''}`.toLocaleLowerCase('vi').includes(keyword)) && (!moduleFilter || workflow.module === moduleFilter) && (!typeFilter || workflow.type === typeFilter));
  }, [query, workflows, moduleFilter, typeFilter]);
  const modules = useMemo(() => [...new Map(workflows.map(item => [item.module, item.moduleName || item.module])).entries()], [workflows]);
  const types = useMemo(() => [...new Map(workflows.map(item => [item.type, item.typeName || item.type])).entries()], [workflows]);

  return <div className="mx-auto w-full max-w-[1240px] space-y-7">
    <header className="flex flex-wrap items-end justify-between gap-5">
      <div><div className="mb-2 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-orange-500"><Sparkles size={15}/> Cổng yêu cầu nhân viên</div><h1 className="text-3xl font-bold tracking-tight text-slate-900">Bạn muốn tạo yêu cầu gì?</h1><p className="mt-2 text-sm text-slate-500">Chọn một biểu mẫu đã được cấp quyền để bắt đầu yêu cầu mới.</p></div>
      <div className="flex w-full flex-wrap justify-end gap-2 lg:w-auto"><select value={moduleFilter} onChange={event => setModuleFilter(event.target.value)} className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm"><option value="">Tất cả module</option>{modules.map(([code,name]) => <option key={code} value={code}>{name}</option>)}</select><select value={typeFilter} onChange={event => setTypeFilter(event.target.value)} className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm"><option value="">Tất cả loại</option>{types.map(([code,name]) => <option key={code} value={code}>{name}</option>)}</select><div className="relative w-full sm:w-72"><Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"/><input value={query} onChange={event => setQuery(event.target.value)} className="w-full rounded-xl border border-slate-200 bg-white py-3 pl-10 pr-4 text-sm outline-none transition focus:border-orange-400 focus:ring-4 focus:ring-orange-100" placeholder="Tìm loại yêu cầu..."/></div></div>
    </header>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
    {loading ? <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">{[0, 1, 2, 3, 4, 5].map(item => <div key={item} className="h-44 animate-pulse rounded-2xl border border-slate-200 bg-white p-6"><div className="h-11 w-11 rounded-xl bg-slate-100"/><div className="mt-5 h-4 w-2/3 rounded bg-slate-100"/><div className="mt-3 h-3 w-full rounded bg-slate-100"/></div>)}</div>
      : filtered.length ? <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">{filtered.map((workflow, index) => { const Icon = CARD_ICONS[index % CARD_ICONS.length]; return <button key={workflow.id} type="button" onClick={() => navigate(`/catalog/${workflow.id}`)} className="group min-h-44 rounded-2xl border border-slate-200 bg-white p-6 text-left shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-orange-400 hover:shadow-lg hover:shadow-orange-100/70"><div className="flex items-start justify-between gap-4"><span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-orange-50 text-orange-500 transition group-hover:bg-orange-500 group-hover:text-white"><Icon size={22}/></span><span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-600">v{workflow.version}</span></div><h2 className="mt-5 text-base font-bold text-slate-800 group-hover:text-orange-600">{workflow.name}</h2><p className="mt-2 line-clamp-2 min-h-10 text-sm leading-5 text-slate-500">{workflow.description || 'Tạo và gửi yêu cầu theo quy trình đã được thiết lập.'}</p><div className="mt-4 flex items-center justify-between text-xs text-slate-400"><span className="rounded-full bg-sky-50 px-2 py-1 font-semibold text-sky-700">{workflow.moduleName || workflow.module || 'Workflow'}</span><span className="flex items-center gap-1 font-semibold text-orange-500 opacity-0 transition group-hover:opacity-100">Mở form <ArrowRight size={14}/></span></div></button>; })}</div>
      : !error && <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center"><FileText size={36} className="mx-auto text-slate-300"/><h2 className="mt-4 font-bold text-slate-700">Không có biểu mẫu phù hợp</h2><p className="mt-2 text-sm text-slate-400">Bạn chưa được cấp quyền sử dụng form nào hoặc không có kết quả tìm kiếm.</p></div>}
  </div>;
}
