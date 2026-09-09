import { AlertCircle, CheckCircle2, ShieldCheck, X, XCircle } from 'lucide-react';

export default function ValidationPublishPanel({steps,errors,validating,publishing,published,canPublish,onValidate,onPublish,onClose}){
 const normalized=errors.map(error=>String(error).toLocaleLowerCase('vi'));
 const isConfigError=text=>['cấu hình','config','thiếu','chưa chọn','approver','template'].some(token=>text.includes(token));
 const configErrors=normalized.filter(isConfigError);
 const structureErrors=normalized.filter(text=>!isConfigError(text)&&!text.includes('bước start')&&!text.includes('bước end'));
 const hasStart=steps.some(step=>step.type==='START');
 const hasEnd=steps.some(step=>step.type==='END');
 const checks=[['Có Start Step',hasStart],['Có End Step',hasEnd],['Không có node hoặc connection bị lỗi',structureErrors.length===0],['Tất cả step đã cấu hình đầy đủ',configErrors.length===0],['Validate thành công',errors.length===0]];
 const valid=!validating&&errors.length===0;
 const publishAllowed=valid&&canPublish;
 return <aside className="z-40 flex h-full w-[410px] min-w-[380px] shrink-0 flex-col border-l border-grayBorder bg-white shadow-[-4px_0_18px_-8px_rgba(15,23,42,0.18)]">
  <header className="flex items-center justify-between border-b border-grayBorder px-6 py-5"><div className="flex items-center gap-3"><div className="flex h-9 w-9 items-center justify-center rounded-full bg-orange-50 text-orange-500"><ShieldCheck size={18}/></div><h2 className="text-base font-bold text-slate-800">Kiểm tra &amp; Xuất bản</h2></div><button type="button" onClick={onClose} className="rounded-full p-2 text-gray-400 hover:bg-gray-100"><X size={17}/></button></header>
  <div className="flex-1 overflow-y-auto px-6 py-5">
   <p className="mb-4 text-[11px] font-bold uppercase tracking-wide text-slate-500">Kết quả kiểm tra</p>
   <div className="space-y-3">{checks.map(([label,passed],index)=><div key={label} className="flex items-center gap-2.5 text-sm">{validating?<span className="h-4 w-4 animate-spin rounded-full border-2 border-orange-200 border-t-orange-500"/>:passed?<CheckCircle2 size={17} className="shrink-0 text-emerald-500"/>:<XCircle size={17} className="shrink-0 text-red-500"/>}<span className={`${index===checks.length-1?'font-semibold ':''}${passed?'text-slate-700':'text-red-600'}`}>{label}</span></div>)}</div>
   {validating?<div className="mt-6 rounded-xl border border-orange-200 bg-orange-50 p-4 text-sm text-orange-700">Đang kiểm tra cấu trúc, connection và cấu hình workflow...</div>:valid?<div className="mt-6 rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm leading-6 text-emerald-700">Mọi điều kiện và cấu hình đều đạt chuẩn. Workflow này đã sẵn sàng để xuất bản.</div>:<div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4"><div className="mb-2 flex items-center gap-2 text-sm font-semibold text-red-700"><AlertCircle size={16}/>Cần sửa {errors.length} lỗi trước khi publish</div><ul className="space-y-2 pl-5 text-xs leading-5 text-red-600">{errors.map((error,index)=><li key={`${error}-${index}`} className="list-disc">{error}</li>)}</ul></div>}
   <p className="mt-6 text-xs leading-5 text-gray-400">Sau khi publish, workflow chuyển sang trạng thái Published và không thể chỉnh sửa trực tiếp. Muốn thay đổi, hãy tạo một draft version mới.</p>
   {!canPublish && valid && <div className="mt-4 rounded-xl border border-blue-200 bg-blue-50 p-4 text-xs leading-5 text-blue-700">Bạn đã hoàn tất kiểm tra. Chỉ Workflow Owner hoặc Admin mới có quyền publish workflow này.</div>}
  </div>
  <footer className="space-y-2 border-t border-grayBorder bg-white px-6 py-5">{published?<div className="rounded-lg bg-emerald-50 px-4 py-3 text-center text-sm font-semibold text-emerald-700">Workflow đã được publish thành công</div>:<button type="button" onClick={publishAllowed?onPublish:onValidate} disabled={validating||publishing||(valid&&!canPublish)} className={`w-full rounded-lg py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 ${publishAllowed?'bg-orange-500 hover:bg-orange-600':'bg-slate-500 hover:bg-slate-600'}`}>{publishing?'Đang publish...':valid?(canPublish?'Publish Workflow':'Chờ Owner publish'):'Kiểm tra lại'}</button>}<button type="button" onClick={onClose} className="w-full rounded-lg border border-grayBorder py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50">Đóng</button></footer>
 </aside>;
}
