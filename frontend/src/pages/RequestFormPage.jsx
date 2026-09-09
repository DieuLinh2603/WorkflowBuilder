import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, ArrowLeft, CalendarDays, CheckCircle2, Download, FileSpreadsheet, FileText, Info, Paperclip, Save, Send, Trash2, UploadCloud, X } from 'lucide-react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { apiError, apiFetch } from '../api';
import { notify } from '../components/ToastCenter';

export default function RequestFormPage() {
  const { workflowId } = useParams();
  const [searchParams] = useSearchParams();
  const preview = searchParams.get('preview') === 'true';
  const navigate = useNavigate();
  const [workflow, setWorkflow] = useState(null);
  const [fields, setFields] = useState([]);
  const [values, setValues] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(null);
  const [draftId, setDraftId] = useState(null);
  const [draftRevision, setDraftRevision] = useState(0);
  const [draftStatus, setDraftStatus] = useState('');
  const [draftUpdatedAt, setDraftUpdatedAt] = useState(null);
  const [draftVersionChanged, setDraftVersionChanged] = useState(false);
  const [confirmDeleteDraft, setConfirmDeleteDraft] = useState(false);
  const [batchRecords, setBatchRecords] = useState([]);
  const [batchFileName, setBatchFileName] = useState('');
  const valuesRef = useRef(values);
  const revisionRef = useRef(draftRevision);
  const saveInFlightRef = useRef(null);
  valuesRef.current = values;
  revisionRef.current = draftRevision;

  useEffect(() => {
    let active = true;
    (async () => {
      const workflowResponse = await apiFetch(preview
        ? `/api/workflows/${workflowId}`
        : `/api/workflows/${workflowId}/active`);
      if (!active) return;
      if (!workflowResponse.ok) {
        setError(await apiError(workflowResponse, 'Không thể mở biểu mẫu này'));
        setLoading(false);
        return;
      }
      const activeWorkflow = await workflowResponse.clone().json();
      if (!preview && activeWorkflow.id !== workflowId) {
        navigate(`/catalog/${activeWorkflow.id}`, { replace: true });
        return;
      }
      const stepsResponse = await apiFetch(`/api/workflows/${activeWorkflow.id}/steps`);
      if (!active) return;
      if (!workflowResponse.ok || !stepsResponse.ok) {
        setError(await apiError(!workflowResponse.ok ? workflowResponse : stepsResponse, 'Không thể mở biểu mẫu này'));
        setLoading(false); return;
      }
      const workflowData = await workflowResponse.json();
      const steps = await stepsResponse.json();
      const start = steps.find(step => step.type === 'START');
      if (!start) { setError('Workflow chưa có Start Step hợp lệ.'); setLoading(false); return; }
      // The catalog URL can still contain an older version id.  Always load the
      // form configuration from the active version that supplied `start`.
      const configResponse = await apiFetch(`/api/workflows/${activeWorkflow.id}/steps/${start.id}/start-config`);
      if (!configResponse.ok) { setError(await apiError(configResponse, 'Không thể tải các trường của biểu mẫu')); setLoading(false); return; }
      const config = await configResponse.json();
      setWorkflow({ ...workflowData, instruction: config.instructionForCreator,
        submissionMode: config.submissionMode || 'SINGLE', maxBatchRows: config.maxBatchRows || 500 });
      setFields(config.fields || []);
      if (!preview && config.submissionMode !== 'BATCH') {
        const draftResponse = await apiFetch(`/api/workflows/${activeWorkflow.id}/request-draft`);
        if (!active) return;
        if (draftResponse.ok && draftResponse.status !== 204) {
          const draft = await draftResponse.json();
          const allowedKeys = new Set((config.fields || []).map(field => field.fieldKey));
          setValues(Object.fromEntries(Object.entries(draft.fields || {}).filter(([key]) => allowedKeys.has(key))));
          setDraftId(draft.id);
          setDraftUpdatedAt(draft.updatedAt);
          setDraftVersionChanged(!!draft.versionChanged);
          setDraftStatus('saved');
        } else if (!draftResponse.ok) {
          setDraftStatus('error');
        }
      }
      setLoading(false);
    })();
    return () => { active = false; };
  }, [workflowId]);

  const orderedFields = useMemo(() => [...fields].sort((a, b) => a.displayOrder - b.displayOrder), [fields]);
  const batchMode = workflow?.submissionMode === 'BATCH';

  const persistDraft = async (snapshot = valuesRef.current, revision = draftRevision) => {
    if (preview || batchMode || !workflow) return true;
    setDraftStatus('saving');
    const operation = (async () => { try {
      const response = await apiFetch(`/api/workflows/${workflow.id}/request-draft`, {
        method: 'PUT', body: JSON.stringify({ workflowId: workflow.id, fields: snapshot }), toast: false
      });
      if (!response.ok) { setDraftStatus('error'); return false; }
      const draft = await response.json();
      setDraftId(draft.id); setDraftUpdatedAt(draft.updatedAt); setDraftVersionChanged(false);
      if (revision === revisionRef.current) setDraftStatus('saved');
      return true;
    } catch {
      setDraftStatus('error');
      return false;
    } })();
    saveInFlightRef.current = operation;
    const result = await operation;
    if (saveInFlightRef.current === operation) saveInFlightRef.current = null;
    return result;
  };

  useEffect(() => {
    if (preview || batchMode || loading || !workflow || draftRevision === 0 || success || submitting) return undefined;
    const revision = draftRevision;
    const snapshot = values;
    const timer = window.setTimeout(() => persistDraft(snapshot, revision), 900);
    return () => window.clearTimeout(timer);
  }, [draftRevision, values, workflow?.id, preview, loading, success, submitting]);

  useEffect(() => {
    if (preview) return undefined;
    const warn = event => {
      if (draftRevision > 0 && draftStatus !== 'saved') { event.preventDefault(); event.returnValue = ''; }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [draftRevision, draftStatus, preview]);

  const leaveForm = async () => {
    if (!preview && draftRevision > 0) {
      if (saveInFlightRef.current) await saveInFlightRef.current;
      if (draftStatus !== 'saved') await persistDraft(valuesRef.current, revisionRef.current);
    }
    navigate(preview ? '/workflows' : '/catalog');
  };

  const removeDraft = async () => {
    if (!draftId) return;
    const response = await apiFetch(`/api/workflows/${workflow.id}/request-draft`, { method: 'DELETE', toast: false });
    if (!response.ok) { setDraftStatus('error'); return; }
    setValues({}); setDraftId(null); setDraftRevision(0); setDraftUpdatedAt(null); setDraftStatus('');
    setConfirmDeleteDraft(false);
  };

  const submit = async event => {
    event.preventDefault();
    if (batchMode && !batchRecords.length) { setError('Vui lòng tải lên file CSV có ít nhất một dòng dữ liệu.'); return; }
    const missing = fields.find(field => field.required && (values[field.fieldKey] === undefined || values[field.fieldKey] === null || values[field.fieldKey] === '' || values[field.fieldKey] === false));
    if (!batchMode && missing) { setError(`Vui lòng nhập trường bắt buộc: ${missing.label}`); return; }
    if (preview) { setError(''); notify('Biểu mẫu hợp lệ. Chế độ chạy thử không tạo instance nghiệp vụ.'); return; }
    setError(''); setSubmitting(true);
    if (!batchMode && saveInFlightRef.current) await saveInFlightRef.current;
    if (!batchMode && draftRevision > 0) await persistDraft(values, draftRevision);
    const response = await apiFetch(batchMode ? '/api/instances/batch' : '/api/instances', {
      method: 'POST',
      body: JSON.stringify(batchMode
        ? { workflowId: workflow?.id || workflowId, records: batchRecords }
        : { workflowId: workflow?.id || workflowId, fields: values }),
      toast: false
    });
    if (!response.ok) { setError(await apiError(response, 'Không thể gửi yêu cầu')); setSubmitting(false); return; }
    setSuccess(await response.json()); setDraftId(null); setDraftRevision(0); setSubmitting(false);
  };

  const downloadTemplate = () => {
    const header = orderedFields.map(field => csvEscape(field.fieldKey)).join(',');
    const hints = orderedFields.map(field => csvEscape(`${field.label}${field.required ? ' *' : ''} [${field.type}]`)).join(',');
    const blob = new Blob([`\uFEFF${header}\r\n${hints}\r\n`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${(workflow?.name || 'workflow').replace(/[^a-zA-Z0-9_-]+/g, '_')}-template.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const importBatch = async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    setError(''); setBatchRecords([]); setBatchFileName(file.name);
    try {
      const rows = parseCsv(await file.text());
      if (!rows.length) throw new Error('File CSV không có dữ liệu.');
      const expected = orderedFields.map(field => field.fieldKey);
      const headers = rows[0].map(value => value.replace(/^\uFEFF/, '').trim());
      const missingHeaders = expected.filter(key => !headers.includes(key));
      if (missingHeaders.length) throw new Error(`Thiếu cột: ${missingHeaders.join(', ')}`);
      let dataRows = rows.slice(1).filter(row => row.some(cell => cell.trim() !== ''));
      if (dataRows[0] && orderedFields.every(field =>
        (dataRows[0][headers.indexOf(field.fieldKey)] || '').includes(`[${field.type}]`))) dataRows = dataRows.slice(1);
      if (!dataRows.length) throw new Error('File CSV chưa có dòng dữ liệu nào.');
      if (dataRows.length > workflow.maxBatchRows) throw new Error(`Chỉ được tải tối đa ${workflow.maxBatchRows} dòng.`);
      const records = dataRows.map((row, rowIndex) => Object.fromEntries(orderedFields.flatMap(field => {
        const raw = (row[headers.indexOf(field.fieldKey)] ?? '').trim();
        if (!raw) return [];
        try { return [[field.fieldKey, csvValue(raw, field.type)]]; }
        catch (reason) { throw new Error(`Dòng ${rowIndex + 1}, cột ${field.label}: ${reason.message}`); }
      })));
      setBatchRecords(records);
    } catch (reason) {
      setError(reason.message || 'Không thể đọc file CSV.');
    }
    event.target.value = '';
  };

  if (loading) return <div className="mx-auto max-w-4xl rounded-2xl border border-slate-200 bg-white p-12 text-center text-sm text-slate-400">Đang tải biểu mẫu...</div>;
  if (!workflow && error) return <div className="mx-auto max-w-xl rounded-2xl border border-red-200 bg-white p-8 text-center"><p className="text-sm text-red-600">{error}</p><button type="button" onClick={() => navigate('/catalog')} className="mt-5 rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600">Quay lại Catalog</button></div>;
  if (success) return <Success requestCode={success.requestCode} total={success.total} batchId={success.batchId} onBack={() => navigate('/catalog')} onTrack={() => navigate('/instances')}/>;

  return <div className="mx-auto w-full max-w-[980px]">
    <button type="button" onClick={leaveForm} className="mb-5 flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-orange-600"><ArrowLeft size={17}/>{preview ? 'Quay lại quản lý workflow' : 'Quay lại danh sách biểu mẫu'}</button>
    <div className="mb-5 flex items-center gap-3"><span className="flex h-11 w-11 items-center justify-center rounded-xl bg-orange-100 text-orange-500"><FileText size={21}/></span><div><div className="flex items-center gap-2"><h1 className="text-2xl font-bold text-slate-900">{workflow?.name || 'Biểu mẫu yêu cầu'}</h1>{preview && <span className="rounded-full bg-blue-50 px-2.5 py-1 text-[10px] font-bold uppercase text-blue-600">Chạy thử</span>}</div><p className="mt-1 text-sm text-slate-500">{workflow?.description || 'Điền thông tin để gửi yêu cầu vào quy trình.'}</p></div></div>
    {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
    {draftVersionChanged && <div className="mb-4 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700"><AlertTriangle size={17} className="mt-0.5 shrink-0"/><span>Biểu mẫu đã được cập nhật từ lúc bạn lưu bản nháp. Các trường còn phù hợp đã được giữ lại; vui lòng kiểm tra trước khi gửi.</span></div>}
    <form onSubmit={submit} className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 p-6"><div className="flex items-start gap-3 rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm leading-5 text-sky-700"><Info size={17} className="mt-0.5 shrink-0"/><span>{preview ? 'Đây là chế độ chạy thử. Bạn có thể kiểm tra giao diện và validation nhưng hệ thống sẽ không tạo instance.' : workflow?.instruction || 'Vui lòng điền đầy đủ và chính xác các thông tin trước khi gửi.'}</span></div></div>
      {batchMode ? <BatchImport fields={orderedFields} records={batchRecords} fileName={batchFileName} maxRows={workflow.maxBatchRows} onDownload={downloadTemplate} onImport={importBatch} onClear={() => { setBatchRecords([]); setBatchFileName(''); }} />
        : <div className="grid grid-cols-1 gap-x-5 gap-y-6 p-6 md:grid-cols-2">{orderedFields.length ? orderedFields.map(field => <DynamicField key={field.id} field={field} value={values[field.fieldKey]} onChange={value => { setError(''); setDraftStatus('dirty'); setValues(current => ({ ...current, [field.fieldKey]: value })); setDraftRevision(current => current + 1); }}/>) : <div className="col-span-full rounded-xl border border-dashed border-slate-300 py-10 text-center text-sm text-slate-400">Form này không yêu cầu nhập thêm thông tin.</div>}</div>}
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-6 py-5">
        <div className="flex items-center gap-3 text-xs text-slate-400">
          {!preview && !batchMode && <>
            <span className={`flex items-center gap-1.5 ${draftStatus === 'error' ? 'text-red-500' : draftStatus === 'saved' ? 'text-emerald-600' : ''}`}>
              <Save size={14}/>
              {draftStatus === 'saving' ? 'Đang lưu bản nháp...'
                : draftStatus === 'error' ? 'Không thể lưu bản nháp'
                  : draftStatus === 'dirty' ? 'Có thay đổi chưa lưu'
                  : draftStatus === 'saved' && draftUpdatedAt
                    ? `Đã lưu ${new Date(draftUpdatedAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`
                    : 'Bản nháp tự lưu khi bạn nhập'}
            </span>
            {draftStatus === 'error' && <button type="button" onClick={() => persistDraft()} className="font-semibold text-orange-600">Thử lại</button>}
            {draftId && draftStatus === 'saved' && <button type="button" onClick={() => setConfirmDeleteDraft(true)} className="flex items-center gap-1 font-semibold text-slate-400 hover:text-red-500"><Trash2 size={13}/>Xóa bản nháp</button>}
          </>}
        </div>
        <div className="flex gap-3">
          <button type="button" onClick={leaveForm} className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600">{preview ? 'Quay lại' : 'Thoát'}</button>
          <button type="submit" disabled={submitting} className="flex min-w-44 items-center justify-center gap-2 rounded-lg bg-orange-500 px-6 py-2.5 text-sm font-bold text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"><Send size={16}/>{submitting ? 'Đang gửi...' : preview ? 'Kiểm tra form' : 'Gửi yêu cầu'}</button>
        </div>
      </div>
    </form>
    {confirmDeleteDraft && <div className="fixed inset-0 z-[150] flex items-center justify-center bg-slate-900/55 p-4" onMouseDown={event => event.target === event.currentTarget && setConfirmDeleteDraft(false)}><div role="dialog" aria-modal="true" className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl"><div className="flex items-start gap-3 border-b border-slate-100 px-6 py-5"><span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-500"><Trash2 size={18}/></span><div className="flex-1"><h2 className="font-bold text-slate-800">Xóa bản nháp?</h2><p className="mt-1 text-sm leading-5 text-slate-500">Dữ liệu đã nhập trong biểu mẫu này sẽ bị xóa và không thể khôi phục.</p></div><button type="button" onClick={() => setConfirmDeleteDraft(false)} className="text-slate-400"><X size={18}/></button></div><div className="flex justify-end gap-3 bg-slate-50 px-6 py-4"><button type="button" onClick={() => setConfirmDeleteDraft(false)} className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600">Giữ bản nháp</button><button type="button" onClick={removeDraft} className="rounded-lg bg-red-500 px-4 py-2 text-sm font-bold text-white">Xóa bản nháp</button></div></div></div>}
  </div>;
}

function Success({ requestCode, total, batchId, onBack, onTrack }) { return <div className="mx-auto max-w-xl rounded-2xl border border-slate-200 bg-white p-10 text-center shadow-sm"><CheckCircle2 size={52} className="mx-auto text-emerald-500"/><h1 className="mt-5 text-2xl font-bold text-slate-900">Đã gửi yêu cầu</h1><p className="mt-2 text-sm text-slate-500">{total ? <>Một batch instance chứa <b className="text-slate-700">{total} hồ sơ</b> đã được tạo. Mỗi hồ sơ vẫn có trạng thái và nhánh xử lý riêng trong batch <span className="font-mono text-xs">{batchId}</span>.</> : <>Yêu cầu <b className="text-slate-700">{requestCode}</b> đã được tạo và chuyển vào workflow.</>}</p><div className="mt-7 flex justify-center gap-3"><button type="button" onClick={onBack} className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600">Tạo yêu cầu khác</button><button type="button" onClick={onTrack} className="rounded-lg bg-orange-500 px-5 py-2.5 text-sm font-semibold text-white">Xem tiến trình</button></div></div>; }

function BatchImport({ fields, records, fileName, maxRows, onDownload, onImport, onClear }) {
  return <div className="space-y-5 p-6"><div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-orange-200 bg-orange-50/50 p-4"><div><p className="text-sm font-bold text-slate-800">Nhập danh sách theo mẫu CSV</p><p className="mt-1 text-xs text-slate-500">Tối đa {maxRows} dòng. Không đổi tên cột ở dòng đầu tiên.</p></div><button type="button" onClick={onDownload} className="flex items-center gap-2 rounded-lg border border-orange-300 bg-white px-4 py-2 text-xs font-bold text-orange-600"><Download size={15}/>Tải form mẫu</button></div><label className="flex cursor-pointer flex-col items-center rounded-xl border-2 border-dashed border-slate-300 px-5 py-8 text-center hover:border-orange-400 hover:bg-orange-50/30"><FileSpreadsheet size={28} className="text-orange-500"/><span className="mt-2 text-sm font-bold text-slate-700">Chọn file CSV đã điền</span><span className="mt-1 text-xs text-slate-400">Hỗ trợ UTF-8, dấu phẩy hoặc chấm phẩy</span><input type="file" accept=".csv,text/csv" onChange={onImport} className="hidden"/></label>{records.length > 0 && <div className="overflow-hidden rounded-xl border border-emerald-200"><div className="flex items-center justify-between bg-emerald-50 px-4 py-3 text-xs text-emerald-700"><span><b>{records.length}</b> dòng hợp lệ từ {fileName}</span><button type="button" onClick={onClear} className="font-bold">Xóa file</button></div><div className="max-h-72 overflow-auto"><table className="min-w-full text-left text-xs"><thead className="sticky top-0 bg-slate-50"><tr>{fields.map(field => <th key={field.id} className="whitespace-nowrap px-3 py-2 font-semibold text-slate-600">{field.label}</th>)}</tr></thead><tbody>{records.slice(0, 50).map((record, index) => <tr key={index} className="border-t border-slate-100">{fields.map(field => <td key={field.id} className="max-w-52 truncate px-3 py-2 text-slate-600">{String(record[field.fieldKey] ?? '')}</td>)}</tr>)}</tbody></table></div>{records.length > 50 && <p className="border-t px-4 py-2 text-xs text-slate-400">Đang xem trước 50/{records.length} dòng.</p>}</div>}</div>;
}

function DynamicField({ field, value, onChange }) {
  const normalized = `${field.fieldKey} ${field.label}`.toLocaleLowerCase('vi');
  const longText = field.type === 'TEXT' && /(lý do|ly do|mô tả|mo ta|nội dung|noi dung|ghi chú|ghi chu)/.test(normalized);
  const fullWidth = field.type === 'FILE' || field.type === 'CHECKBOX' || longText;
  const style = 'w-full rounded-lg border border-slate-200 bg-white px-3.5 py-3 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-orange-400 focus:ring-4 focus:ring-orange-100';
  return <label className={fullWidth ? 'md:col-span-2' : ''}><span className="mb-2 block text-sm font-semibold text-slate-700">{field.label}{field.required && <span className="text-red-500"> *</span>}</span>
    {longText ? <textarea rows={4} value={value ?? ''} onChange={event => onChange(event.target.value)} required={field.required} placeholder={field.placeholder || `Nhập ${field.label.toLocaleLowerCase('vi')}...`} className={`${style} resize-y`}/>
      : field.type === 'TEXT' ? <input type="text" value={value ?? ''} onChange={event => onChange(event.target.value)} required={field.required} placeholder={field.placeholder || `Nhập ${field.label.toLocaleLowerCase('vi')}...`} className={style}/>
      : field.type === 'NUMBER' ? <input type="number" value={value ?? ''} onChange={event => onChange(event.target.value === '' ? '' : Number(event.target.value))} required={field.required} placeholder={field.placeholder || 'Nhập số'} className={style}/>
      : field.type === 'DATE' ? <div className="relative"><input type="date" value={value ?? ''} onChange={event => onChange(event.target.value)} required={field.required} className={`${style} pr-10`}/><CalendarDays size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400"/></div>
      : field.type === 'CHECKBOX' ? <span className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-normal text-slate-600"><input type="checkbox" checked={!!value} onChange={event => onChange(event.target.checked)} required={field.required} className="h-4 w-4 accent-orange-500"/>{field.placeholder || 'Xác nhận'}</span>
      : field.type === 'FILE' ? <FileField field={field} value={value} onChange={onChange}/>
      : <input type="text" value={value ?? ''} onChange={event => onChange(event.target.value)} required={field.required} className={style}/>} 
  </label>;
}

function FileField({ field, value, onChange }) {
  const inputRef = useRef(null);
  const selectFile = async event => { const file = event.target.files?.[0]; if (!file) return; if (file.size > 5 * 1024 * 1024) { event.target.value = ''; return; } onChange({ name: file.name, size: file.size, contentType: file.type, dataUrl: await readFile(file) }); };
  return <div><input ref={inputRef} type="file" className="hidden" onChange={selectFile}/>{value ? <div className="flex items-center gap-3 rounded-xl border border-orange-200 bg-orange-50 px-4 py-3"><Paperclip size={18} className="text-orange-500"/><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-700">{value.name}</p><p className="text-xs text-slate-400">{formatSize(value.size)}</p></div><button type="button" onClick={() => { onChange(null); if (inputRef.current) inputRef.current.value = ''; }} className="rounded-full p-1 text-slate-400 hover:bg-white hover:text-red-500"><X size={16}/></button></div> : <button type="button" onClick={() => inputRef.current?.click()} className="flex w-full flex-col items-center rounded-xl border border-dashed border-orange-300 bg-orange-50/60 px-5 py-6 text-center hover:bg-orange-50"><UploadCloud size={25} className="text-orange-500"/><span className="mt-2 text-sm font-semibold text-orange-600">Chọn file đính kèm</span><span className="mt-1 text-xs text-slate-400">Tối đa 5 MB</span></button>}</div>;
}

function readFile(file) { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); }); }
function formatSize(size = 0) { return size < 1024 * 1024 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`; }
function csvEscape(value) { const text = String(value ?? ''); return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text; }
function parseCsv(text) {
  const comma = parseCsvWithDelimiter(text, ','), semicolon = parseCsvWithDelimiter(text, ';');
  return (semicolon[0]?.length || 0) > (comma[0]?.length || 0) ? semicolon : comma;
}
function parseCsvWithDelimiter(text, delimiter) {
  const rows = []; let row = [], value = '', quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') { value += '"'; index += 1; }
      else quoted = !quoted;
    } else if (char === delimiter && !quoted) { row.push(value); value = ''; }
    else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && text[index + 1] === '\n') index += 1;
      row.push(value); rows.push(row); row = []; value = '';
    } else value += char;
  }
  if (quoted) throw new Error('CSV có dấu ngoặc kép chưa đóng.');
  if (value || row.length) { row.push(value); rows.push(row); }
  return rows.filter(item => item.some(cell => cell.trim() !== ''));
}
function csvValue(raw, type) {
  if (type === 'TEXT') return raw;
  if (type === 'NUMBER') { const value = Number(raw.replace(',', '.')); if (!Number.isFinite(value)) throw new Error('không phải là số hợp lệ'); return value; }
  if (type === 'DATE') { if (!/^\d{4}-\d{2}-\d{2}$/.test(raw) || Number.isNaN(Date.parse(`${raw}T00:00:00Z`))) throw new Error('ngày phải có dạng YYYY-MM-DD'); return raw; }
  if (type === 'CHECKBOX') {
    const normalized = raw.toLowerCase();
    if (['true', '1', 'yes', 'y', 'có', 'co', 'x'].includes(normalized)) return true;
    if (['false', '0', 'no', 'n', 'không', 'khong'].includes(normalized)) return false;
    throw new Error('checkbox phải là true/false, yes/no hoặc 1/0');
  }
  throw new Error('kiểu field này không được hỗ trợ trong CSV');
}
