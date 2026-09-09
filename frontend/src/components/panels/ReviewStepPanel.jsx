import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { apiFetch } from '../../api';
import { ActorSelector, CompletionPolicy, DeadlinePicker, PanelFrame, SectionLabel, Toggle } from './StepPanelShared';

const DEFAULTS={mode:'MANUAL',approverMode:'FIXED_USER',actorUserIds:[],fixedUserEmail:'',actorRole:'',dynamicActorSource:'REQUEST_CREATOR_MANAGER',reviewContent:'REQUEST_CONTENT',commentRequired:true,resultMode:'REQUIRE_APPROVAL',completionMode:'ANY',completionPercentage:50,deadlineHours:48,deadlineDate:null};

export default function ReviewStepPanel({workflowId,step,onClose,onDelete}){
 const [config,setConfig]=useState(DEFAULTS),[users,setUsers]=useState([]),[message,setMessage]=useState(''),[saving,setSaving]=useState(false);
 useEffect(()=>{Promise.all([apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(json),apiFetch('/api/users/active').then(json)]).then(([c,u])=>{setConfig({...DEFAULTS,...c});setUsers(Array.isArray(u)?u:[])}).catch(e=>setMessage(e.message));},[workflowId,step.id]);
 const save=async()=>{setSaving(true);setMessage('');const r=await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/review`,{method:'PUT',body:JSON.stringify(config)});setMessage(r.ok?'Đã lưu cấu hình Review Step.':await error(r));setSaving(false)};
 return <PanelFrame icon={Search} title="Cấu hình: Review Step" onClose={onClose} onDelete={onDelete} footer={<button onClick={save} disabled={saving} className="btn-primary w-full py-2.5 text-sm disabled:opacity-60">{saving?'Đang lưu...':'Lưu cấu hình'}</button>}>
  <div className="space-y-6">
   <ActorSelector config={config} setConfig={setConfig} users={users} label="Người review"/>
   <section><SectionLabel>Nội dung cần review</SectionLabel><label className="mb-1.5 block text-[11px] text-gray-500">Loại nội dung</label><select value={config.reviewContent} onChange={e=>setConfig(c=>({...c,reviewContent:e.target.value}))} className="input-field bg-white text-xs"><option value="REQUEST_CONTENT">Nội dung request</option><option value="ATTACHMENTS">Tài liệu đính kèm</option><option value="REQUEST_AND_ATTACHMENTS">Request và tài liệu đính kèm</option></select><div className="mt-3"><Toggle checked={!!config.commentRequired} onChange={v=>setConfig(c=>({...c,commentRequired:v}))} label="Bắt buộc để lại nhận xét (comment)"/></div></section>
   <section><SectionLabel>Kết quả review</SectionLabel><div className="space-y-3 text-xs text-slate-700"><label className="flex cursor-pointer gap-2"><input type="radio" name={`review-result-${step.id}`} checked={config.resultMode==='COMMENT_ONLY'} onChange={()=>setConfig(c=>({...c,resultMode:'COMMENT_ONLY'}))} className="accent-orange-500"/><span>Chỉ ghi nhận review, không chặn luồng</span></label><label className="flex cursor-pointer gap-2"><input type="radio" name={`review-result-${step.id}`} checked={config.resultMode==='REQUIRE_APPROVAL'} onChange={()=>setConfig(c=>({...c,resultMode:'REQUIRE_APPROVAL'}))} className="accent-orange-500"/><span>Yêu cầu review đạt mới cho qua bước tiếp theo</span></label></div><div className="mt-3 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-[11px] leading-5 text-blue-700">Trên canvas, nối nhánh <b>Đạt</b> tới bước tiếp theo. Nối nhánh <b>Không đạt</b> tới Assignment để sửa/bổ sung dữ liệu, sau đó có thể nối trở lại Review.</div></section>
   <CompletionPolicy config={config} setConfig={setConfig}/>
   <DeadlinePicker config={config} setConfig={setConfig}/>
   {message&&<p className={`rounded-lg px-3 py-2 text-xs ${message.startsWith('Đã')?'bg-emerald-50 text-emerald-700':'bg-red-50 text-red-600'}`}>{message}</p>}
  </div>
 </PanelFrame>;
}
async function json(r){if(!r.ok)throw new Error(await error(r));return r.json()}async function error(r){const b=await r.json().catch(()=>null);return b?.message||'Không thể lưu cấu hình';}
