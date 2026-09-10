import { useEffect, useState } from 'react';
import { Search, Trash2 } from 'lucide-react';
import { apiFetch } from '../../api';
import AddFieldModal from './AddFieldModal';
import CalculatedOutputEditor from '../CalculatedOutputEditor';
import { ActorSelector, CompletionPolicy, DeadlinePicker, PanelFrame, SectionLabel, Toggle } from './StepPanelShared';

const DEFAULTS={mode:'MANUAL',approverMode:'FIXED_USER',actorUserIds:[],fixedUserEmail:'',actorRole:'',dynamicActorSource:'REQUEST_CREATOR_MANAGER',reviewContent:'REQUEST_CONTENT',commentRequired:true,resultMode:'REQUIRE_APPROVAL',completionMode:'ANY',completionPercentage:50,deadlineHours:48,deadlineDate:null};

export default function ReviewStepPanel({workflowId,step,onClose,onDelete}){
 const [calculationFields,setCalculationFields]=useState([]);
 useEffect(()=>{apiFetch(`/api/workflows/${workflowId}/fields`).then(json).then(items=>setCalculationFields(Array.isArray(items)?items:[])).catch(e=>setMessage(e.message));},[workflowId]);
 const [config,setConfig]=useState(DEFAULTS),[users,setUsers]=useState([]),[fields,setFields]=useState([]),[modal,setModal]=useState(false),[editingField,setEditingField]=useState(null),[message,setMessage]=useState(''),[saving,setSaving]=useState(false);
 useEffect(()=>{Promise.all([apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(json),apiFetch('/api/users/active').then(json),apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields`).then(json)]).then(([c,u,f])=>{setConfig({...DEFAULTS,...c});setUsers(Array.isArray(u)?u:[]);setFields(Array.isArray(f)?f:[])}).catch(e=>setMessage(e.message));},[workflowId,step.id]);
 const save=async()=>{setSaving(true);setMessage('');const r=await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/review`,{method:'PUT',body:JSON.stringify(config)});setMessage(r.ok?'Đã lưu cấu hình Review Step.':await error(r));setSaving(false)};
 const addField=async data=>{const r=await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields${editingField?`/${editingField.id}`:''}`,{method:editingField?'PUT':'POST',body:JSON.stringify(data)});if(r.ok){const saved=await r.json();setFields(v=>editingField?v.map(f=>f.id===saved.id?saved:f):[...v,saved]);setEditingField(null);setModal(false)}else setMessage(await error(r))};
 const deleteField=async id=>{const r=await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields/${id}`,{method:'DELETE'});if(r.ok)setFields(v=>v.filter(f=>f.id!==id));else setMessage(await error(r))};
 return <PanelFrame icon={Search} title="Cấu hình: Review Step" onClose={onClose} onDelete={onDelete} footer={<button onClick={save} disabled={saving} className="btn-primary w-full py-2.5 text-sm disabled:opacity-60">{saving?'Đang lưu...':'Lưu cấu hình'}</button>}>
  <div className="space-y-6">
   <ActorSelector config={config} setConfig={setConfig} users={users} label="Người review"/>
   <section><SectionLabel>Nội dung cần review</SectionLabel><label className="mb-1.5 block text-[11px] text-gray-500">Loại nội dung</label><select value={config.reviewContent} onChange={e=>setConfig(c=>({...c,reviewContent:e.target.value}))} className="input-field bg-white text-xs"><option value="REQUEST_CONTENT">Nội dung request</option><option value="ATTACHMENTS">Tài liệu đính kèm</option><option value="REQUEST_AND_ATTACHMENTS">Request và tài liệu đính kèm</option></select><div className="mt-3"><Toggle checked={!!config.commentRequired} onChange={v=>setConfig(c=>({...c,commentRequired:v}))} label="Bắt buộc để lại nhận xét (comment)"/></div></section>
   <section><SectionLabel>Output Fields (Kết quả tổng hợp)</SectionLabel><p className="mb-3 text-[11px] text-gray-500">Reviewer nhập dữ liệu đầu ra để các bước Approval, IF và Notification phía sau sử dụng.</p><div className="space-y-2">{fields.map(f=><div key={f.id} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"><div><p className="text-xs font-semibold">{f.label}</p><p className="text-[10px] text-gray-400">{f.fieldKey} | {f.type}</p></div><div className="flex gap-2"><button onClick={()=>{setEditingField(f);setModal(true)}} className="text-[10px] font-semibold text-orange-500">Sửa</button><button onClick={()=>deleteField(f.id)} className="text-gray-400 hover:text-red-500"><Trash2 size={13}/></button></div></div>)}<button onClick={()=>{setEditingField(null);setModal(true)}} className="w-full rounded-lg border border-dashed border-orange-400 py-2 text-xs font-medium text-orange-500 hover:bg-orange-50">+ Thêm output field</button></div></section>
   <p className="rounded-lg bg-orange-50 p-3 text-xs leading-5 text-orange-700">Ngoài các output field cố định, Reviewer có thể tự thêm mục kết quả (tên mục và nội dung) khi xử lý task. Các mục bổ sung được lưu theo lượt review trong lịch sử; output field cố định vẫn dùng cho điều kiện và thông báo tự động.</p>
   <CalculatedOutputEditor value={config.calculatedOutputs||[]} onChange={calculatedOutputs=>setConfig(c=>({...c,calculatedOutputs}))} fields={calculationFields}/>
   <p className="text-xs text-slate-500">Đây là công thức gợi ý. Reviewer có thể thêm, sửa hoặc xóa công thức trên task trước khi gửi kết quả.</p>
   <CompletionPolicy config={config} setConfig={setConfig}/>
   <DeadlinePicker config={config} setConfig={setConfig}/>
   {message&&<p className={`rounded-lg px-3 py-2 text-xs ${message.startsWith('Đã')?'bg-emerald-50 text-emerald-700':'bg-red-50 text-red-600'}`}>{message}</p>}
  </div><AddFieldModal isOpen={modal} onClose={()=>{setModal(false);setEditingField(null)}} onSave={addField} editingField={editingField}/>
 </PanelFrame>;
}
async function json(r){if(!r.ok)throw new Error(await error(r));return r.json()}async function error(r){const b=await r.json().catch(()=>null);return b?.message||'Không thể lưu cấu hình';}
