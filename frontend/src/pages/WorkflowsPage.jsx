import { Search, Plus, GitBranch, Edit2, Play, Trash2, Copy, Clock3, AlertTriangle, X, DatabaseZap } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import CreateWorkflowModal from '../components/CreateWorkflowModal';
import useWorkflowDesigner from '../hooks/useWorkflowDesigner';
import { apiError, apiFetch } from '../api';
import Pagination from '../components/Pagination';
import WorkflowRemovalModal from '../components/WorkflowRemovalModal';
import WorkflowDataBindingModal from '../components/WorkflowDataBindingModal';
import DuplicateWorkflowModal from '../components/DuplicateWorkflowModal';

export default function WorkflowsPage() {
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const { createWorkflow } = useWorkflowDesigner();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [bindingWorkflow, setBindingWorkflow] = useState(null);
  const [pendingDuplicate, setPendingDuplicate] = useState(null);
  const [duplicating, setDuplicating] = useState(false);
  const [duplicateError, setDuplicateError] = useState('');
  const [pendingRemoval, setPendingRemoval] = useState(null);
  const [removing, setRemoving] = useState(false);
  const [workflows, setWorkflows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query,setQuery]=useState(''),[statusFilter,setStatusFilter]=useState(''),[moduleFilter,setModuleFilter]=useState(''),[typeFilter,setTypeFilter]=useState(''),[page,setPage]=useState(0);
  const pageSize=8;

  const loadWorkflows = async () => {
    setLoading(true); const res = await apiFetch('/api/workflows/managed');
    if (res.ok) setWorkflows(await res.json()); setLoading(false);
  };
  useEffect(() => { loadWorkflows(); }, []);

  const fetchDropdownUsers = async () => {
    const res = await apiFetch('/api/users/dropdown');
    if (!res.ok) return [];
    return await res.json();
  };

  const handleCreate = async (data) => {
    const result = await createWorkflow(data);
    setShowCreateModal(false);
    navigate(`/workflows/${result.id}/design`);
  };

  const handleDuplicate = wf => {
    setDuplicateError('');
    setPendingDuplicate(wf);
  };
  const confirmDuplicate = async data => {
    if (!pendingDuplicate) return;
    setDuplicating(true);
    setDuplicateError('');
    try {
      const response = await apiFetch(`/api/workflows/${pendingDuplicate.id}/duplicate`, {
        method: 'POST', body: JSON.stringify(data), successMessage: 'Đã nhân bản workflow thành một Draft độc lập.',
      });
      if (!response.ok) throw new Error(await apiError(response));
      const copy = await response.json();
      setPendingDuplicate(null);
      await loadWorkflows();
      navigate(`/workflows/${copy.id}/design`);
    } catch (reason) {
      setDuplicateError(reason.message);
    } finally {
      setDuplicating(false);
    }
  };
  const handleEdit = async wf => {
    if (wf.status === 'DRAFT') { navigate(`/workflows/${wf.id}/design`); return; }
    const response = await apiFetch(`/api/workflows/${wf.id}/versions`, { method: 'POST', successMessage: 'Đã tạo phiên bản Draft tiếp theo để chỉnh sửa.' });
    if (!response.ok) return;
    const draft = await response.json();
    await loadWorkflows();
    navigate(`/workflows/${draft.id}/design`);
  };
  const handleRemove = async wf => {
    setPendingRemoval(wf);
  };
  const confirmRemove = async () => {
    const wf=pendingRemoval;
    if(!wf)return;
    setRemoving(true);
    const hardDelete = wf.status === 'DRAFT';
    const response = hardDelete
      ? await apiFetch(`/api/workflows/${wf.id}`, { method: 'DELETE', successMessage: `Đã xóa workflow Draft “${wf.name}”.` })
      : await apiFetch(`/api/workflows/${wf.id}/archive`, { method: 'POST', successMessage: `Đã lưu trữ workflow “${wf.name}”. Các instance cũ vẫn tiếp tục chạy.` });
    if (response.ok) { setPendingRemoval(null); await loadWorkflows(); }
    setRemoving(false);
  };
  const handleTestRun = wf => navigate(`/catalog/${wf.id}?preview=true`);
  const statusClass = status => status === 'PUBLISHED' ? 'bg-green-100 text-green-700' : status === 'SUSPENDED' ? 'bg-yellow-100 text-yellow-700' : status === 'ARCHIVED' ? 'bg-slate-200 text-slate-500' : 'bg-gray-200 text-gray-700';
  const handleAudience = async wf => {
    const current = await apiFetch(`/api/workflows/${wf.id}/audience`); const rules = current.ok ? await current.json() : [];
    const initial = rules.length ? rules.map(x => `${x.subjectType}:${x.subjectValue}`).join(',') : 'ALL_ACTIVE:*';
    const raw = window.prompt('Audience (TYPE:value), ví dụ ALL_ACTIVE:*, SYSTEM_ROLE:VIEWER, USER:<uuid>, GROUP:<uuid>', initial); if (raw == null) return;
    const body = raw.split(',').map(x => x.trim()).filter(Boolean).map(x => { const [subjectType, ...rest] = x.split(':'); return { subjectType, subjectValue: rest.join(':') || '*' }; });
    const res = await apiFetch(`/api/workflows/${wf.id}/audience`, { method: 'PUT', body: JSON.stringify(body) }); if (!res.ok) alert('Không thể lưu audience');
  };
  const workflowRows=useMemo(()=>{
    const families=new Map();
    workflows.forEach(workflow=>{
      const key=workflow.familyId||workflow.id;
      if(!families.has(key))families.set(key,[]);
      families.get(key).push(workflow);
    });
    return [...families.values()].flatMap(versions=>{
      const ordered=[...versions].sort((left,right)=>compareVersion(right.version,left.version));
      const published=ordered.find(version=>version.status==='PUBLISHED');
      const draft=ordered.find(version=>version.status==='DRAFT');
      if(published&&draft)return [published,{...draft,activePublishedId:published.id,activePublishedVersion:published.version}];
      return [draft||published||ordered[0]].filter(Boolean);
    });
  },[workflows]);
  const modules=useMemo(()=>[...new Map(workflowRows.filter(item=>item.module).map(item=>[item.module,item.moduleName || item.module])).entries()].sort((a,b)=>a[1].localeCompare(b[1],'vi')),[workflowRows]);
  const types=useMemo(()=>[...new Map(workflowRows.filter(item=>item.type).map(item=>[item.type,item.typeName || item.type])).entries()].sort((a,b)=>a[1].localeCompare(b[1],'vi')),[workflowRows]);
  const filtered=useMemo(()=>workflowRows.filter(item=>`${item.name} ${item.typeName || item.type} ${item.moduleName || item.module} ${item.ownerName}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))&&(statusFilter?item.status===statusFilter:item.status!=='ARCHIVED')&&(!moduleFilter||item.module===moduleFilter)&&(!typeFilter||item.type===typeFilter)),[workflowRows,query,statusFilter,moduleFilter,typeFilter]);
  const displayed=filtered.slice(page*pageSize,(page+1)*pageSize);
  useEffect(()=>setPage(0),[query,statusFilter,moduleFilter,typeFilter]);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-grayBorder relative h-full flex flex-col overflow-hidden">
      <div className="px-6 py-4 border-b border-grayBorder flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-800">Quản lý Workflows</h1>
          <p className="text-sm text-gray-500">Thiết kế và quản lý các luồng quy trình của tổ chức</p>
        </div>
      </div>

      <div className="px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input type="text" value={query} onChange={event=>setQuery(event.target.value)} placeholder="Tìm quy trình..." className="pl-9 pr-4 py-2 border border-grayBorder rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-primary w-64" />
          </div>
          
          <select value={moduleFilter} onChange={event=>setModuleFilter(event.target.value)} className="border border-grayBorder rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px]">
            <option value="">Module: Tất cả</option>{modules.map(([code,name])=><option key={code} value={code}>{name}</option>)}
          </select>

          <select value={typeFilter} onChange={event=>setTypeFilter(event.target.value)} className="border border-grayBorder rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px]"><option value="">Loại: Tất cả</option>{types.map(([code,name])=><option key={code} value={code}>{name}</option>)}</select>

          <select value={statusFilter} onChange={event=>setStatusFilter(event.target.value)} className="border border-grayBorder rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px]">
            <option value="">Trạng thái: Tất cả</option><option value="DRAFT">Draft</option><option value="PUBLISHED">Published</option><option value="SUSPENDED">Suspended</option><option value="ARCHIVED">Archived</option>
          </select>
        </div>

        {(hasRole('ADMIN') || hasRole('WORKFLOW_OWNER')) && (
          <button 
            onClick={() => setShowCreateModal(true)}
            className="btn-primary flex items-center gap-2 py-2"
          >
            <Plus size={16} /> Tạo Workflow mới
          </button>
        )}
      </div>

      <div className="px-6 pb-4 flex-1 overflow-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="text-xs font-semibold text-gray-500 uppercase border-b border-grayBorder bg-gray-50/50">
              <th className="py-3 px-4">TÊN WORKFLOW</th>
              <th className="py-3 px-4">LOẠI</th>
              <th className="py-3 px-4">MODULE</th>
              <th className="py-3 px-4">VERSION</th>
              <th className="py-3 px-4">TRẠNG THÁI</th>
              <th className="py-3 px-4">NGƯỜI SỞ HỮU</th>
              <th className="py-3 px-4">NGÀY CẬP NHẬT</th>
              <th className="py-3 px-4">HÀNH ĐỘNG</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan="8" className="p-8 text-center text-gray-400">Đang tải...</td></tr>}
            {displayed.map((wf) => (
              <tr key={wf.id} className="hover:bg-gray-50 text-sm">
                <td className="py-3 px-4 font-medium text-gray-800 flex items-center gap-2">
                  <GitBranch size={16} className="text-gray-400" />
                  {wf.name}
                </td>
                <td className="py-3 px-4 text-gray-600">{wf.typeName || wf.type}</td>
                <td className="py-3 px-4 text-gray-600"><span className="rounded-full bg-sky-50 px-2.5 py-1 text-xs font-semibold text-sky-700">{wf.moduleName || wf.module}</span></td>
                <td className="py-3 px-4 text-gray-600">v{wf.version}</td>
                <td className="py-3 px-4">
                  <div className="flex flex-wrap items-center gap-2"><span className={`px-2.5 py-1 rounded-md text-xs font-semibold ${statusClass(wf.status)}`}>{wf.status}</span>{wf.status==='DRAFT'&&wf.activePublishedVersion&&<span className="rounded-md bg-blue-50 px-2 py-1 text-[10px] font-semibold text-blue-600" title="Phiên bản này vẫn đang phục vụ các request mới và instance hiện tại">v{wf.activePublishedVersion} đang chạy</span>}</div>
                </td>
                <td className="py-3 px-4 text-gray-600">{wf.ownerName}</td>
                <td className="py-3 px-4 text-gray-600">{wf.createdAt ? new Date(wf.createdAt).toLocaleDateString('vi-VN') : '-'}</td>
                <td className="py-3 px-4">
                  <div className="flex items-center gap-3 text-gray-400">
                    <button onClick={() => navigate(`/workflows/${wf.id}/versions`)} className="hover:text-orange-500" title="Lịch sử phiên bản"><Clock3 size={16}/></button>
                    {wf.status === 'PUBLISHED' && wf.canPublish && <button type="button" onClick={() => setBindingWorkflow(wf)} className="hover:text-emerald-600" title="Data Binding"><DatabaseZap size={16}/></button>}
                    {(wf.canEdit || (wf.canPublish && ['PUBLISHED','SUSPENDED'].includes(wf.status))) && <button onClick={() => handleEdit(wf)} className="hover:text-primary tooltip" title={wf.status === 'DRAFT' ? 'Chỉnh sửa' : 'Tạo phiên bản tiếp theo để chỉnh sửa'}>
                      <Edit2 size={16} />
                    </button>}
                    {wf.canDuplicate && <button onClick={() => handleDuplicate(wf)} className="hover:text-blue-500" title="Duplicate"><Copy size={16}/></button>}
                    {(wf.canEdit || wf.canPublish) && <button onClick={() => handleTestRun(wf)} className="hover:text-green-500 tooltip" title="Chạy thử biểu mẫu, không tạo instance"><Play size={16}/></button>}
                    {wf.canPublish && wf.status !== 'ARCHIVED' && <button onClick={() => handleRemove(wf)} className="hover:text-red-500 tooltip" title={wf.status === 'DRAFT' ? 'Xóa workflow Draft' : 'Lưu trữ workflow; instance đang chạy không bị ảnh hưởng'}><Trash2 size={16}/></button>}

                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={page} totalItems={filtered.length} pageSize={pageSize} onPageChange={setPage} itemLabel="workflows" />

      <CreateWorkflowModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreate={handleCreate}
        currentUser={user}
        fetchDropdownUsers={fetchDropdownUsers}
      />
      {pendingRemoval&&<WorkflowRemovalModal workflow={pendingRemoval} busy={removing} onClose={()=>!removing&&setPendingRemoval(null)} onConfirm={confirmRemove}/>} 
      {bindingWorkflow&&<WorkflowDataBindingModal workflow={bindingWorkflow} onClose={()=>setBindingWorkflow(null)}/>} 
      {pendingDuplicate&&<DuplicateWorkflowModal workflow={pendingDuplicate} currentUser={user} fetchDropdownUsers={fetchDropdownUsers} busy={duplicating} error={duplicateError} onClose={()=>!duplicating&&setPendingDuplicate(null)} onConfirm={confirmDuplicate}/>} 
    </div>
  );
}

function RemovalModal({workflow,busy,onClose,onConfirm}){
  const hardDelete=workflow.status==='DRAFT';
  return <div className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/50 p-4" onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
    <div role="dialog" aria-modal="true" aria-labelledby="workflow-removal-title" className="w-full max-w-[520px] overflow-hidden rounded-2xl bg-white shadow-2xl">
      <div className="flex items-start gap-4 border-b border-slate-100 px-6 py-5"><span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${hardDelete?'bg-red-50 text-red-500':'bg-amber-50 text-amber-500'}`}>{hardDelete?<Trash2 size={21}/>:<AlertTriangle size={21}/>}</span><div className="min-w-0 flex-1"><h2 id="workflow-removal-title" className="text-lg font-bold text-slate-800">{hardDelete?'Xóa workflow Draft?':'Lưu trữ workflow đang chạy?'}</h2><p className="mt-1 truncate text-sm font-semibold text-slate-500">{workflow.name} · v{workflow.version}</p></div><button type="button" disabled={busy} onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40" aria-label="Đóng"><X size={18}/></button></div>
      <div className="px-6 py-5">{hardDelete?<div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700">Workflow Draft và toàn bộ step, connection, cấu hình liên quan sẽ bị xóa vĩnh viễn. Thao tác này không thể hoàn tác.</div>:<div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800"><b>Workflow sẽ ngừng nhận yêu cầu mới.</b> Các instance đã tạo vẫn tiếp tục chạy trên đúng phiên bản hiện tại và dữ liệu lịch sử không bị xóa.</div>}</div>
      <div className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4"><button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">Hủy</button><button type="button" disabled={busy} onClick={onConfirm} className={`flex min-w-32 items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50 ${hardDelete?'bg-red-500 hover:bg-red-600':'bg-orange-500 hover:bg-orange-600'}`}>{busy?'Đang xử lý...':hardDelete?'Xóa workflow':'Lưu trữ'}</button></div>
    </div>
  </div>;
}

function compareVersion(left='0',right='0'){
  const a=String(left).split('.').map(value=>Number.parseInt(value,10)||0),b=String(right).split('.').map(value=>Number.parseInt(value,10)||0);
  for(let index=0;index<Math.max(a.length,b.length);index++){const difference=(a[index]||0)-(b[index]||0);if(difference)return difference;}
  return 0;
}
