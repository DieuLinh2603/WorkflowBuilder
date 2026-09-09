import { useEffect, useState } from 'react';
import { CheckCircle2, CircleStop, ShieldCheck, XCircle } from 'lucide-react';
import { apiFetch } from '../../api';
import { PanelFrame, SectionLabel, Toggle } from './StepPanelShared';

const DEFAULTS={outcome:'COMPLETED',title:'Yêu cầu đã hoàn tất',message:'Yêu cầu {{requestCode}} đã kết thúc.',notifyRequester:true,notifyRecordRecipient:false};
const OUTCOMES=[['COMPLETED','Hoàn tất','Kết thúc request thành công',CheckCircle2,'bg-emerald-50 text-emerald-500'],['APPROVED','Đã duyệt','Kết thúc với kết quả được duyệt',ShieldCheck,'bg-blue-50 text-blue-500'],['REJECTED','Từ chối','Kết thúc với kết quả bị từ chối',XCircle,'bg-red-50 text-red-500'],['CANCELLED','Đã hủy','Kết thúc và đánh dấu đã hủy',CircleStop,'bg-slate-100 text-slate-500']];

export default function EndStepPanel({workflowId,step,onClose,onDelete}){
 const [config,setConfig]=useState(DEFAULTS),[saving,setSaving]=useState(false),[message,setMessage]=useState('');
 useEffect(()=>{load(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(saved=>setConfig({...DEFAULTS,...saved})).catch(e=>setMessage(e.message));},[workflowId,step.id]);
 const save=async()=>{setSaving(true);setMessage('');try{const response=await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/end`,{method:'PUT',body:JSON.stringify(config)});if(!response.ok)throw new Error(await error(response));setConfig({...DEFAULTS,...await response.json()});setMessage('Đã lưu cấu hình End Step.')}catch(e){setMessage(e.message)}finally{setSaving(false)}};
 return <PanelFrame icon={CircleStop} title="Cấu hình: End Step" subtitle="Thiết lập kết quả khi workflow đi tới điểm kết thúc" onClose={onClose} onDelete={onDelete} footer={<div className="flex justify-end gap-2"><button type="button" onClick={onClose} className="rounded-lg border border-grayBorder px-4 py-2.5 text-sm text-slate-600">Hủy</button><button type="button" onClick={save} disabled={saving} className="btn-primary min-w-[150px] py-2.5 text-sm disabled:opacity-60">{saving?'Đang lưu...':'Lưu cấu hình'}</button></div>}>
  <div className="space-y-6">
   <section><SectionLabel>Kết quả kết thúc</SectionLabel><div className="grid grid-cols-2 gap-3">{OUTCOMES.map(([value,label,description,Icon,colorClass])=><button key={value} type="button" onClick={()=>setConfig(c=>({...c,outcome:value}))} className={`rounded-xl border p-4 text-left transition ${config.outcome===value?'border-orange-500 bg-orange-50 ring-1 ring-orange-100':'border-grayBorder bg-white hover:border-orange-200'}`}><div className="flex items-start gap-3"><div className={`rounded-lg p-2 ${colorClass}`}><Icon size={18}/></div><div><p className="text-sm font-semibold text-slate-700">{label}</p><p className="mt-1 text-[10px] leading-4 text-gray-400">{description}</p></div></div></button>)}</div></section>
   <section><SectionLabel>Thông báo hoàn tất</SectionLabel><label className="mb-4 block"><span className="mb-1.5 block text-[11px] text-slate-600">Tiêu đề</span><input value={config.title} maxLength={160} onChange={e=>setConfig(c=>({...c,title:e.target.value}))} className="input-field"/></label><label className="block"><span className="mb-1.5 block text-[11px] text-slate-600">Nội dung</span><textarea rows={6} value={config.message||''} maxLength={1000} onChange={e=>setConfig(c=>({...c,message:e.target.value}))} className="input-field resize-y"/><p className="mt-1.5 text-[10px] text-gray-400">Có thể dùng biến {'{{requestCode}}'} và {'{{workflowName}}'}.</p></label></section>
   <section className="space-y-3 rounded-xl border border-grayBorder p-4"><Toggle checked={!!config.notifyRequester} onChange={value=>setConfig(c=>({...c,notifyRequester:value}))} label="Thông báo kết quả cho người tạo request"/><Toggle checked={!!config.notifyRecordRecipient} onChange={value=>setConfig(c=>({...c,notifyRecordRecipient:value}))} label="Thông báo cho người nhận của dòng CSV đi tới End Step này"/></section>
   <div className="rounded-xl bg-slate-50 p-4 text-xs leading-5 text-slate-500"><span className="font-semibold text-slate-700">Lưu ý:</span> End Step không có connection đi ra. Workflow có thể có nhiều End Step với kết quả khác nhau cho từng nhánh IF, Approve hoặc Reject.</div>
   {message&&<p className={`rounded-lg px-3 py-2 text-xs ${message.startsWith('Đã')?'bg-emerald-50 text-emerald-700':'bg-red-50 text-red-600'}`}>{message}</p>}
  </div>
 </PanelFrame>;
}
async function load(url){const response=await apiFetch(url);if(!response.ok)throw new Error(await error(response));return response.json()}
async function error(response){const body=await response.json().catch(()=>null);return body?.message||'Không thể lưu cấu hình'}
