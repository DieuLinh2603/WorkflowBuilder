import { useEffect, useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Download,
  Eye,
  FileText,
  Paperclip,
  Send,
  XCircle,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { apiFetch } from "../api";
import InstanceJourney from "../components/InstanceJourney";
import CalculatedOutputEditor, { CalculatedOutputTable } from '../components/CalculatedOutputEditor';

export default function TaskDetailPage() {
  const { taskId } = useParams(),
    navigate = useNavigate();
  const [task, setTask] = useState(null),
    [history, setHistory] = useState([]),
    [users, setUsers] = useState([]),
    [fields, setFields] = useState({}),
    [reviewResults, setReviewResults] = useState([]),
    [calculatedOutputs, setCalculatedOutputs] = useState([]),
    [calculatedRows, setCalculatedRows] = useState([]),
    [selectedRowNumbers, setSelectedRowNumbers] = useState([]),
    [selectedRowsOutcome, setSelectedRowsOutcome] = useState('PASS'),
    [previewing, setPreviewing] = useState(false),
    [comment, setComment] = useState(""),
    [loading, setLoading] = useState(true),
    [submitting, setSubmitting] = useState(false),
    [error, setError] = useState("");
  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const taskResponse = await apiFetch(`/api/my-tasks/${taskId}`);
        if (!taskResponse.ok) throw new Error(await apiError(taskResponse));
        const data = await taskResponse.json();
        setTask(data);
        setReviewResults(data.reviewResults || []);
        setCalculatedOutputs(data.calculatedOutputs || []);
        setCalculatedRows(data.calculatedRows || []);
        setSelectedRowNumbers(data.batch && data.stepType === 'REVIEW' && data.status === 'PENDING'
          ? (data.batchRecords || []).map(record => record.rowNumber) : []);
        setSelectedRowsOutcome('PASS');
        setComment('');
        setFields({ ...(data.fields || {}), ...Object.fromEntries((data.fieldDefinitions || []).filter(field => (data.fields || {})[field.fieldKey] === undefined).map(field => [field.fieldKey, field.allowMultiple || field.type === 'MULTI_CHOICE' ? [] : ''])) });
        const usersResponse = await apiFetch('/api/users/active', { toast: false });
        if (usersResponse.ok) setUsers(await usersResponse.json());
        const historyResponse = await apiFetch(
          `/api/instances/${data.instanceId}/history`,
        );
        if (historyResponse.ok) setHistory(await historyResponse.json());
      } catch (reason) {
        setError(reason.message);
      } finally {
        setLoading(false);
      }
    })();
  }, [taskId]);
  useEffect(() => {
    if (task?.status === 'PENDING') setCalculatedRows([]);
  }, [fields, calculatedOutputs, task?.status]);
  const previewOutputs = async () => {
    setPreviewing(true);
    setError('');
    try {
      const response = await apiFetch(`/api/tasks/${task.id}/preview-outputs`, {
        method: 'POST', body: JSON.stringify({ calculatedOutputs, fields }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      setCalculatedRows(await response.json());
    } catch (reason) { setError(reason.message); }
    finally { setPreviewing(false); }
  };
  const submit = async (action) => {
    const rowReviewEnabled = task.batch && task.stepType === 'REVIEW' && task.resultMode !== 'COMMENT_ONLY';
    const selectedPassCount = selectedRowsOutcome === 'PASS'
      ? selectedRowNumbers.length : (task.batchRecords || []).length - selectedRowNumbers.length;
    if (task.stepType === 'REVIEW' && reviewResults.some(item => !item.label.trim() || !item.content.trim())) {
      setError('Vui lòng nhập tên và nội dung cho từng mục kết quả bổ sung, hoặc xóa mục chưa dùng.');
      return;
    }
    if ((action === "reject" || action === "fail" || task.commentRequired
      || (rowReviewEnabled && selectedPassCount < (task.batchRecords || []).length)) && !comment.trim()) {
      setError(
        rowReviewEnabled && selectedPassCount < (task.batchRecords || []).length
          ? "Vui lòng nhập nhận xét vì kết quả có dòng FAIL"
          : action === "reject" || action === "fail"
          ? "Vui lòng nhập lý do khi chọn không đạt hoặc từ chối"
          : "Nhận xét là bắt buộc",
      );
      return;
    }
    setSubmitting(true);
    setError("");
    const response = await apiFetch(
      `/api/tasks/${task.id}/${action}`,
      {
        method: "POST",
        body: JSON.stringify({
          comment,
          reviewResults: task.stepType === 'REVIEW' ? reviewResults : [],
          calculatedOutputs: task.stepType === 'REVIEW' ? calculatedOutputs : [],
          selectedRowNumbers: rowReviewEnabled ? selectedRowNumbers : null,
          selectedRowsOutcome: rowReviewEnabled ? selectedRowsOutcome : null,
          fields: task.fieldsEditable ? Object.fromEntries((task.fieldDefinitions || []).map(field => [field.fieldKey, fields[field.fieldKey]])) : {},
        }),
      },
    );
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      setError(body.message || "Không thể xử lý nhiệm vụ");
      setSubmitting(false);
      return;
    }
    navigate("/tasks");
  };
  if (loading)
    return (
      <div className="flex h-screen items-center justify-center text-gray-400">
        Đang tải nhiệm vụ...
      </div>
    );
  if (!task)
    return (
      <div className="p-8">
        <button
          onClick={() => navigate("/tasks")}
          className="mb-6 flex items-center gap-2 text-sm text-gray-500"
        >
          <ArrowLeft size={16} />
          Quay lại
        </button>
        <div className="rounded-xl bg-red-50 p-5 text-red-600">
          {error || "Không tìm thấy task"}
        </div>
      </div>
    );
  const approval = task.stepType === "APPROVAL",
    review = task.stepType === "REVIEW",
    completed = task.status === "COMPLETED";
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex h-14 items-center justify-between border-b border-grayBorder bg-white px-8">
        <button
          onClick={() => navigate("/tasks")}
          className="flex items-center gap-2 text-sm font-medium text-gray-500 hover:text-orange-600"
        >
          <ArrowLeft size={17} />
          Quay lại danh sách nhiệm vụ
        </button>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${completed ? 'bg-emerald-50 text-emerald-700' : 'bg-orange-50 text-orange-600'}`}>
          {completed ? "Đã hoàn thành" : approval ? "Approver" : review ? "Reviewer" : "Assignee"}
        </span>
      </header>
      <main className="mx-auto max-w-[1180px] px-6 py-7">
        <div className="mb-6">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-900">
              {task.workflowName}
            </h1>
            <span className="rounded-full bg-orange-500 px-3 py-1 text-xs font-semibold text-white">
              {task.stepLabel}
            </span>
          </div>
          <p className="mt-2 text-sm text-gray-500">
            Mã yêu cầu: <b>{task.requestCode}</b> · Gửi bởi:{" "}
            <b>{task.requesterName}</b>
          </p>
        </div>
        <div className="grid grid-cols-[minmax(0,1fr)_340px] gap-6">
          <div className="space-y-5">
            <section className="rounded-2xl border border-grayBorder bg-white p-6 shadow-sm">
              {review && task.fieldsEditable && (
                <div className="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                  Bạn có thể nhập các trường kết quả và tự bổ sung mục tổng hợp trước khi xác nhận chuyển tiếp.
                </div>
              )}
              <h2 className="mb-5 font-bold text-slate-800">Dữ liệu đầu vào</h2>
              {task.batch ? <BatchRecordsTable records={task.batchRecords || []} historical={completed} selectable={review && !completed && task.resultMode !== 'COMMENT_ONLY'} selectedRows={selectedRowNumbers} onSelectedRowsChange={setSelectedRowNumbers} selectedOutcome={selectedRowsOutcome} onSelectedOutcomeChange={setSelectedRowsOutcome} /> : <div className="grid grid-cols-2 gap-5">
                {Object.entries(fields).filter(([key])=>!(task.fieldDefinitions||[]).some(field=>field.fieldKey===key)).map(([key, value]) => (
                  <label
                    key={key}
                    className={
                      isFileValue(value) || displayValue(value).length > 80 ? "col-span-2" : "block"
                    }
                  >
                    <span className="mb-1.5 block text-xs font-semibold capitalize text-slate-600">
                      {key.replaceAll("_", " ")}
                    </span>
                    {isFileValue(value) ? (
                      <FileValue file={value} />
                    ) : (
                      <div className="min-h-[42px] rounded-lg bg-slate-50 px-3 py-2.5 text-sm text-slate-700">
                        {displayValue(value)}
                      </div>
                    )}
                  </label>
                ))}
              </div>}
            </section>
            {!!task.reviewHandoffs?.length && <ReviewHandoffSection handoffs={task.reviewHandoffs} />}
            {!!task.fieldDefinitions?.length && <section className="rounded-2xl border border-orange-200 bg-white p-6 shadow-sm"><h2 className="mb-2 font-bold text-slate-800">{review ? 'Kết quả tổng hợp' : 'Kết quả thực hiện'}</h2><p className="mb-5 text-xs text-slate-500">Các dữ liệu đầu ra này sẽ được chuyển cho những bước tiếp theo.{task.batch && ' Giá trị nhập tại đây được áp dụng cho tất cả hồ sơ trong task hiện tại.'}</p><div className="grid grid-cols-2 gap-5">{task.fieldDefinitions.map(field=><TaskFieldInput key={field.id} field={field} users={users} value={fields[field.fieldKey]} disabled={!task.fieldsEditable} onChange={value=>setFields(current=>({...current,[field.fieldKey]:value}))}/>)}</div></section>}
            {review && <section className="rounded-2xl border border-orange-200 bg-white p-6 shadow-sm">
              <CalculatedOutputEditor value={calculatedOutputs} onChange={setCalculatedOutputs} fields={task.batch ? [...new Set((task.batchRecords || []).flatMap(record => Object.keys(record.fields || {})))] : Object.keys(fields)} disabled={task.status !== 'PENDING' || submitting || previewing}/>
              {task.status === 'PENDING' && <button type="button" disabled={submitting || previewing || !calculatedOutputs.length} onClick={previewOutputs} className="mt-3 rounded-lg bg-orange-50 px-4 py-2 text-xs font-semibold text-orange-600 disabled:opacity-40">{previewing ? 'Đang tính...' : 'Tính thử / Xem trước'}</button>}
              <CalculatedOutputTable outputs={calculatedOutputs} rows={calculatedRows}/>
            </section>}
            {review && <section className="rounded-2xl border border-orange-200 bg-white p-6 shadow-sm">
              <h2 className="mb-2 font-bold text-slate-800">Kết quả bổ sung của Reviewer</h2>
              <p className="mb-4 text-xs text-slate-500">Tự thêm tên mục và nội dung cần tổng hợp. Kết quả được lưu theo lượt review và hiển thị trong lịch sử cho các bước sau.</p>
              <div className="space-y-4">{reviewResults.map((item, index) => <div key={index} className="space-y-2 rounded-xl border border-slate-200 p-3">
                <input aria-label={`Tên mục ${index + 1}`} maxLength={200} placeholder="Tên mục kết quả" className="input-field text-sm" value={item.label} disabled={task.status !== 'PENDING' || submitting} onChange={event => setReviewResults(items => items.map((value, i) => i === index ? {...value, label: event.target.value} : value))}/>
                <textarea aria-label={`Nội dung mục ${index + 1}`} maxLength={5000} rows={3} placeholder="Nội dung tổng hợp" className="input-field text-sm" value={item.content} disabled={task.status !== 'PENDING' || submitting} onChange={event => setReviewResults(items => items.map((value, i) => i === index ? {...value, content: event.target.value} : value))}/>
                {task.status === 'PENDING' && <button type="button" disabled={submitting} className="text-xs text-red-500" onClick={() => setReviewResults(items => items.filter((_, i) => i !== index))}>Xóa mục</button>}
              </div>)}</div>
              {task.status === 'PENDING' ? <button type="button" disabled={submitting || reviewResults.length >= 50} onClick={() => setReviewResults(items => [...items, {label: '', content: ''}])} className="mt-3 rounded-lg border border-dashed border-orange-400 px-4 py-2 text-xs font-semibold text-orange-600 disabled:opacity-40">+ Thêm mục kết quả</button> : !reviewResults.length && <p className="text-sm text-slate-400">Không có kết quả bổ sung.</p>}
            </section>}
            <section className="rounded-2xl border border-grayBorder bg-white p-6">
              <h2 className="mb-5 font-bold text-slate-800">
                Lịch sử các bước
              </h2>
              <InstanceJourney history={history} instance={{ status: task.instanceStatus, currentStepId: task.instanceCurrentStepId }} compact />
            </section>
          </div>
          <aside>
            <section className="sticky top-6 rounded-2xl border border-grayBorder bg-white p-5 shadow-sm">
              {completed && <div className="mb-4 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"><CheckCircle2 size={18} className="mt-0.5 shrink-0"/><div><b>Bạn đã hoàn thành nhiệm vụ này.</b><p className="mt-1 text-xs text-emerald-700">{task.completedAt ? `Hoàn thành lúc ${new Date(task.completedAt).toLocaleString('vi-VN')}.` : ''} Nội dung bên dưới chỉ dùng để xem lại.</p></div></div>}
              <h2 className="font-bold text-slate-800">
                {approval
                  ? "Quyết định của bạn"
                  : review
                    ? "Kết quả review"
                    : "Hoàn thành công việc"}
              </h2>
              {task.deadlineAt && (
                <div className="mt-4 flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-500">
                  <Clock3 size={14} />
                  Deadline: {new Date(task.deadlineAt).toLocaleString("vi-VN")}
                </div>
              )}
              {!completed && <label className="mt-5 block">
                <span className="mb-2 block text-xs font-semibold text-slate-600">
                  {approval ? "Lý do / nhận xét" : review ? "Nhận xét review" : "Kết quả / lý do thất bại"}{" "}
                  {task.commentRequired && (
                    <span className="text-red-500">*</span>
                  )}
                </span>
                <textarea
                  rows={6}
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                  className="w-full resize-none rounded-lg border border-grayBorder p-3 text-sm outline-none focus:border-orange-400"
                  placeholder={
                    approval
                      ? "Nhập nhận xét; lý do là bắt buộc khi từ chối..."
                      : review ? "Ghi chú lỗi hoặc nhận xét review..." : "Nhập kết quả; lý do là bắt buộc khi báo thất bại..."
                  }
                />
              </label>}
              {error && (
                <p className="mt-3 rounded-lg bg-red-50 p-3 text-xs text-red-600">
                  {error}
                </p>
              )}
              {!completed && <div className="mt-5 space-y-2">
                {approval && (
                  <>
                    <button
                      disabled={submitting}
                      onClick={() => submit("approve")}
                      className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-500 py-3 text-sm font-semibold text-white hover:bg-emerald-600 disabled:opacity-50"
                    >
                      <CheckCircle2 size={16} />
                      Phê duyệt
                    </button>
                    <button
                      disabled={submitting}
                      onClick={() => submit("reject")}
                      className="flex w-full items-center justify-center gap-2 rounded-lg bg-red-500 py-3 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50"
                    >
                      <XCircle size={16} />
                      Từ chối
                    </button>
                  </>
                )}
                {review && task.batch && task.resultMode !== "COMMENT_ONLY" ? (
                  <button
                    disabled={submitting}
                    onClick={() => submit("complete")}
                    className="flex w-full items-center justify-center gap-2 rounded-lg bg-orange-500 py-3 text-sm font-semibold text-white hover:bg-orange-600 disabled:opacity-50"
                  >
                    <CheckCircle2 size={16} />
                    Xác nhận kết quả từng dòng
                  </button>
                ) : review && (
                  <>
                    <button
                      disabled={submitting}
                      onClick={() => submit("complete")}
                      className="flex w-full items-center justify-center gap-2 rounded-lg bg-orange-500 py-3 text-sm font-semibold text-white hover:bg-orange-600 disabled:opacity-50"
                    >
                      <CheckCircle2 size={16} />
                      Đạt — Chuyển tiếp
                    </button>
                    {task.resultMode !== "COMMENT_ONLY" && (
                      <button
                        disabled={submitting}
                        onClick={() => submit("reject")}
                        className="flex w-full items-center justify-center gap-2 rounded-lg border border-red-300 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 disabled:opacity-50"
                      >
                        <XCircle size={16} />
                        Không đạt — Chuyển xử lý
                      </button>
                    )}
                  </>
                )}
                {!approval && !review && (
                  <><button
                    disabled={submitting}
                    onClick={() => submit("complete")}
                    className="flex w-full items-center justify-center gap-2 rounded-lg bg-orange-500 py-3 text-sm font-semibold text-white"
                  >
                    <FileText size={16} />
                    Hoàn thành
                  </button>
                  {task.canFail && <button disabled={submitting} onClick={()=>submit("fail")} className="flex w-full items-center justify-center gap-2 rounded-lg border border-red-300 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 disabled:opacity-50"><XCircle size={16}/>Báo thất bại</button>}
                  </>
                )}
              </div>}
            </section>
          </aside>
        </div>
      </main>
    </div>
  );
}

function TaskFieldInput({field,value,onChange,users,disabled}) {
  const style="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm disabled:bg-slate-50";
  const label=<span className="mb-1.5 block text-xs font-semibold text-slate-600">{field.label}{field.required&&<span className="text-red-500"> *</span>}</span>;
  let input;
  if(field.type==='NUMBER') input=<input type="number" value={value??''} onChange={e=>onChange(e.target.value===''?'':Number(e.target.value))} disabled={disabled} className={style}/>;
  else if(field.type==='DATE'||field.type==='DATETIME') input=<input type={field.type==='DATE'?'date':'datetime-local'} value={value??''} onChange={e=>onChange(e.target.value)} disabled={disabled} className={style}/>;
  else if(field.type==='CHECKBOX') input=<input type="checkbox" checked={!!value} onChange={e=>onChange(e.target.checked)} disabled={disabled} className="h-4 w-4 accent-orange-500"/>;
  else if(field.type==='SELECT') input=<select value={value??''} onChange={e=>onChange(e.target.value)} disabled={disabled} className={style}><option value="">Chọn...</option>{(field.options||[]).map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>;
  else if(field.type==='RADIO') input=<div className="space-y-2">{(field.options||[]).map(o=><label key={o.value} className="flex gap-2 text-sm font-normal"><input type="radio" name={field.fieldKey} checked={value===o.value} onChange={()=>onChange(o.value)} disabled={disabled}/>{o.label}</label>)}</div>;
  else if(field.type==='MULTI_CHOICE') input=<div className="space-y-2">{(field.options||[]).map(o=><label key={o.value} className="flex gap-2 text-sm font-normal"><input type="checkbox" checked={(value||[]).includes(o.value)} onChange={e=>onChange(e.target.checked?[...(value||[]),o.value]:(value||[]).filter(v=>v!==o.value))} disabled={disabled}/>{o.label}</label>)}</div>;
  else if(field.type==='USER_PICKER') input=<select multiple={field.allowMultiple} value={field.allowMultiple?(value||[]):(value??'')} onChange={e=>onChange(field.allowMultiple?[...e.target.selectedOptions].map(o=>o.value):e.target.value)} disabled={disabled} className={style}><option value="">Chọn người dùng...</option>{users.map(u=><option key={u.id} value={u.id}>{u.displayName} · {u.email}</option>)}</select>;
  else input=<input value={value??''} onChange={e=>onChange(e.target.value)} disabled={disabled} className={style}/>;
  return <label className={field.type==='MULTI_CHOICE'||field.type==='RADIO'?'col-span-2':''}>{label}{input}</label>;
}

function ReviewHandoffSection({ handoffs }) {
  return <section className="rounded-2xl border border-violet-200 bg-white p-6 shadow-sm">
    <div className="mb-4">
      <h2 className="font-bold text-slate-800">Báo cáo bàn giao từ bước Review</h2>
      <p className="mt-1 text-xs text-slate-500">Dữ liệu, kết quả phân loại và ghi chú được Reviewer chuyển cho bước hiện tại.</p>
    </div>
    <div className="space-y-4">{handoffs.map((handoff, index) => {
      const passCount = (handoff.records || []).filter(record => record.outcome === 'PASS').length;
      const failCount = (handoff.records || []).filter(record => record.outcome === 'FAIL').length;
      return <article key={handoff.sourceTaskId || index} className="overflow-hidden rounded-xl border border-violet-100">
        <div className="flex flex-wrap items-start justify-between gap-3 bg-violet-50 px-4 py-3">
          <div><p className="text-sm font-semibold text-violet-900">{handoff.sourceStepLabel || 'Review'}</p><p className="mt-0.5 text-[11px] text-violet-600">{handoff.reviewerName || 'Reviewer'}{handoff.reviewedAt ? ` · ${new Date(handoff.reviewedAt).toLocaleString('vi-VN')}` : ''}</p></div>
          <div className="flex gap-2">{passCount > 0 && <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-[10px] font-bold text-emerald-700">PASS: {passCount}</span>}{failCount > 0 && <span className="rounded-full bg-red-100 px-2.5 py-1 text-[10px] font-bold text-red-700">FAIL: {failCount}</span>}</div>
        </div>
        <div className="space-y-4 p-4">
          {handoff.comment && <div><p className="mb-1 text-[11px] font-bold uppercase text-slate-500">Nhận xét</p><p className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">{handoff.comment}</p></div>}
          {!!handoff.additionalResults?.length && <div><p className="mb-2 text-[11px] font-bold uppercase text-slate-500">Nội dung tổng hợp</p><dl className="grid gap-2 sm:grid-cols-2">{handoff.additionalResults.map((item, itemIndex) => <div key={`${item.label}-${itemIndex}`} className="rounded-lg border border-slate-100 px-3 py-2"><dt className="text-xs font-semibold text-slate-600">{item.label}</dt><dd className="mt-1 whitespace-pre-wrap break-words text-sm text-slate-800">{item.content}</dd></div>)}</dl></div>}
          {!!handoff.records?.length && <HandoffRecordsTable records={handoff.records} />}
        </div>
      </article>;
    })}</div>
  </section>;
}

function HandoffRecordsTable({ records }) {
  const keys = [...new Set(records.flatMap(record => Object.keys(record.fields || {})))];
  const showRowNumber = records.some(record => record.rowNumber != null);
  return <div><p className="mb-2 text-[11px] font-bold uppercase text-slate-500">Dữ liệu đã review</p><div className="overflow-x-auto rounded-lg border border-slate-200"><table className="min-w-full text-left text-xs"><thead className="bg-slate-50 text-slate-600"><tr>{showRowNumber && <th className="whitespace-nowrap px-3 py-2">Dòng</th>}<th className="whitespace-nowrap px-3 py-2">Kết quả</th>{keys.map(key => <th key={key} className="whitespace-nowrap px-3 py-2">{key.replaceAll('_', ' ')}</th>)}</tr></thead><tbody>{records.map((record, index) => <tr key={`${record.rowNumber ?? 'record'}-${index}`} className="border-t border-slate-100">{showRowNumber && <td className="px-3 py-2 font-semibold text-violet-600">{record.rowNumber ?? '—'}</td>}<td className="px-3 py-2"><span className={`rounded-full px-2 py-1 text-[10px] font-bold ${record.outcome === 'PASS' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>{record.outcome || '—'}</span></td>{keys.map(key => <td key={key} className="max-w-[260px] px-3 py-2 text-slate-700">{displayValue(record.fields?.[key])}</td>)}</tr>)}</tbody></table></div></div>;
}

function BatchRecordsTable({ records, historical, selectable = false, selectedRows = [], onSelectedRowsChange, selectedOutcome = 'PASS', onSelectedOutcomeChange }) {
  const [filteredRowNumbers, setFilteredRowNumbers] = useState(null);
  const recordKey = records.map(record => record.rowNumber).join(',');
  useEffect(() => setFilteredRowNumbers(null), [recordKey]);
  if (!records.length && historical) return <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-5 text-sm leading-6 text-slate-600">Các dòng thuộc task này đã được xử lý và chuyển sang bước tiếp theo. Bạn có thể xem kết quả thao tác trong phần <b>Lịch sử các bước</b>.</div>;
  const keys = [...new Set(records.flatMap(record => Object.keys(record.fields || {})))];
  const selected = new Set(selectedRows);
  const visibleRecords = filteredRowNumbers === null
    ? records
    : records.filter(record => filteredRowNumbers.includes(record.rowNumber));
  const allVisibleSelected = visibleRecords.length > 0 && visibleRecords.every(record => selected.has(record.rowNumber));
  const toggleVisibleRows = checked => onSelectedRowsChange(checked
    ? [...new Set([...selected, ...visibleRecords.map(record => record.rowNumber)])]
    : [...selected].filter(rowNumber => !visibleRecords.some(record => record.rowNumber === rowNumber)));
  return <div>
    <div className="mb-3 rounded-lg border border-violet-200 bg-violet-50 px-4 py-3 text-sm text-violet-700"><b>{records.length} hồ sơ</b> đang được xử lý cùng nhau trong task này.</div>
    {selectable && <BatchReviewControls records={records} keys={keys} selected={selected} onChange={onSelectedRowsChange} onFilter={setFilteredRowNumbers} selectedOutcome={selectedOutcome} onSelectedOutcomeChange={onSelectedOutcomeChange}/>}
    <div className="max-h-[520px] overflow-auto rounded-xl border border-slate-200"><table className="min-w-full text-left text-xs"><thead className="sticky top-0 z-10 bg-slate-50"><tr>{selectable && <th className="px-3 py-2"><input type="checkbox" aria-label="Chọn tất cả dòng đang hiển thị" checked={allVisibleSelected} onChange={event => toggleVisibleRows(event.target.checked)} className="accent-orange-500"/></th>}<th className="px-3 py-2">Dòng</th>{keys.map(key => <th key={key} className="whitespace-nowrap px-3 py-2 capitalize">{key.replaceAll('_', ' ')}</th>)}{selectable && <th className="px-3 py-2">Kết quả</th>}</tr></thead><tbody>{visibleRecords.map(record => { const isSelected=selected.has(record.rowNumber), pass=isSelected === (selectedOutcome==='PASS'); return <tr key={record.rowNumber} className={`border-t border-slate-100 ${isSelected?'bg-orange-50/40':''}`}>{selectable && <td className="px-3 py-2"><input type="checkbox" aria-label={`Chọn dòng ${record.rowNumber}`} checked={isSelected} onChange={event => onSelectedRowsChange(event.target.checked ? [...selected, record.rowNumber] : [...selected].filter(value => value !== record.rowNumber))} className="accent-orange-500"/></td>}<td className="px-3 py-2 font-bold text-violet-600">{record.rowNumber}</td>{keys.map(key => <td key={key} title={displayValue(record.fields?.[key])} className="max-w-64 truncate px-3 py-2 text-slate-600">{displayValue(record.fields?.[key])}</td>)}{selectable && <td className="px-3 py-2"><span className={`rounded-full px-2 py-1 font-semibold ${pass?'bg-emerald-100 text-emerald-700':'bg-red-100 text-red-600'}`}>{pass?'PASS':'FAIL'}</span></td>}</tr>})}{!visibleRecords.length && <tr><td colSpan={keys.length + (selectable ? 3 : 1)} className="border-t border-slate-100 px-4 py-8 text-center text-sm text-slate-400">Không có dòng nào phù hợp với bộ lọc.</td></tr>}</tbody></table></div>
  </div>;
}

function BatchReviewControls({records,keys,selected,onChange,onFilter,selectedOutcome,onSelectedOutcomeChange}) {
  const [field,setField]=useState(keys[0]||''), [operator,setOperator]=useState('CONTAINS'), [value,setValue]=useState('');
  useEffect(()=>{if(!keys.includes(field))setField(keys[0]||'')},[keys,field]);
  const matching=records.filter(record=>matchesFilter(record.fields?.[field],operator,value)).map(record=>record.rowNumber);
  const selectedPass=selectedOutcome==='PASS'?selected.size:records.length-selected.size;
  return <div className="mb-3 space-y-3 rounded-xl border border-orange-200 bg-orange-50/40 p-4">
    <div className="flex flex-wrap items-center gap-2 text-xs"><span className="font-semibold text-slate-700">Dòng được chọn sẽ:</span><select value={selectedOutcome} onChange={e=>onSelectedOutcomeChange(e.target.value)} className="rounded-lg border border-orange-200 bg-white px-3 py-2 font-semibold text-slate-700"><option value="PASS">PASS — dòng còn lại FAIL</option><option value="FAIL">FAIL — dòng còn lại PASS</option></select><span className="ml-auto rounded-full bg-emerald-100 px-3 py-1.5 font-semibold text-emerald-700">{selectedPass} PASS</span><span className="rounded-full bg-red-100 px-3 py-1.5 font-semibold text-red-600">{records.length-selectedPass} FAIL</span></div>
    <div className="grid gap-2 sm:grid-cols-[1fr_150px_1fr_auto]"><select value={field} onChange={e=>setField(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs">{keys.map(key=><option key={key} value={key}>{key.replaceAll('_',' ')}</option>)}</select><select value={operator} onChange={e=>setOperator(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs"><option value="CONTAINS">Có chứa</option><option value="EQUALS">Bằng</option><option value="NOT_EQUALS">Khác</option><option value="GT">Lớn hơn</option><option value="LT">Nhỏ hơn</option><option value="EMPTY">Trống</option><option value="NOT_EMPTY">Không trống</option></select><input value={value} onChange={e=>setValue(e.target.value)} disabled={operator==='EMPTY'||operator==='NOT_EMPTY'} placeholder="Giá trị lọc..." className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs disabled:bg-slate-100"/><button type="button" onClick={()=>onFilter(matching)} className="rounded-lg bg-orange-500 px-5 py-2 text-xs font-semibold text-white">Lọc</button></div>
    <div className="flex gap-2"><button type="button" onClick={()=>onChange(records.map(record=>record.rowNumber))} className="text-xs font-semibold text-orange-600">Chọn tất cả</button><span className="text-slate-300">·</span><button type="button" onClick={()=>onChange([])} className="text-xs font-semibold text-slate-500">Bỏ chọn tất cả</button><span className="ml-auto text-xs text-slate-500">Đã chọn {selected.size}/{records.length} dòng</span></div>
  </div>;
}

function matchesFilter(actual,operator,expected) {
  const empty=actual==null||actual===''||(Array.isArray(actual)&&actual.length===0);
  if(operator==='EMPTY')return empty;
  if(operator==='NOT_EMPTY')return !empty;
  const left=String(actual??'').toLocaleLowerCase('vi'), right=String(expected??'').toLocaleLowerCase('vi');
  if(operator==='EQUALS')return left===right;
  if(operator==='NOT_EQUALS')return left!==right;
  if(operator==='GT'||operator==='LT'){const a=Number(actual),b=Number(expected);return Number.isFinite(a)&&Number.isFinite(b)&&(operator==='GT'?a>b:a<b)}
  return left.includes(right);
}
async function apiError(response) {
  const body = await response.json().catch(() => null);
  return body?.message || `HTTP ${response.status}`;
}

function isFileValue(value) {
  return !!value && typeof value === "object" && typeof value.name === "string" && typeof value.dataUrl === "string";
}

function isScalarValue(value) {
  return value == null || ["string", "number", "boolean"].includes(typeof value);
}

function displayValue(value) {
  if (value == null || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

function FileValue({ file }) {
  const [fileError, setFileError] = useState("");
  const preview = () => {
    setFileError("");
    const previewWindow = window.open("", "_blank");
    if (!previewWindow) { setFileError("Trình duyệt đang chặn cửa sổ xem file."); return; }
    previewWindow.opener = null;
    previewWindow.document.write('<div style="font:14px Arial;padding:24px;color:#64748b">Đang mở file...</div>');
    try {
      const blobUrl = URL.createObjectURL(fileBlob(file));
      if (canPreview(file)) {
        previewWindow.location.replace(blobUrl);
      } else {
        const name = escapeHtml(file.name);
        previewWindow.document.open();
        previewWindow.document.write(`<title>${name}</title><main style="font-family:Arial;max-width:620px;margin:80px auto;padding:32px;border:1px solid #e2e8f0;border-radius:16px;text-align:center"><h2 style="color:#1e293b">Không thể xem trước định dạng này</h2><p style="color:#64748b">${name}</p><p style="color:#94a3b8">Trình duyệt không hỗ trợ xem trực tiếp file này. Vui lòng tải xuống để mở bằng ứng dụng phù hợp.</p><a href="${blobUrl}" download="${name}" style="display:inline-block;margin-top:16px;padding:11px 20px;background:#f97316;color:white;text-decoration:none;border-radius:8px">Tải file xuống</a></main>`);
        previewWindow.document.close();
      }
      setTimeout(() => URL.revokeObjectURL(blobUrl), 5 * 60 * 1000);
    } catch (reason) {
      previewWindow.close();
      setFileError(reason.message || "Không thể đọc dữ liệu file.");
    }
  };
  const download = () => {
    setFileError("");
    try {
      const blobUrl = URL.createObjectURL(fileBlob(file));
      const anchor = document.createElement("a");
      anchor.href = blobUrl; anchor.download = file.name || "attachment"; document.body.appendChild(anchor); anchor.click(); anchor.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch (reason) { setFileError(reason.message || "Không thể tải file."); }
  };
  return <div className="flex min-h-[64px] items-center gap-3 rounded-xl border border-orange-200 bg-orange-50/60 px-4 py-3">
    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-orange-500 shadow-sm"><Paperclip size={18}/></span>
    <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-700">{file.name}</p><p className="mt-1 text-xs text-slate-400">{formatFileSize(file.size)}{file.contentType ? ` · ${file.contentType}` : ""}</p></div>
    {fileError && <span className="max-w-44 text-xs text-red-500">{fileError}</span>}
    <button type="button" onClick={preview} className="flex items-center gap-1.5 rounded-lg border border-orange-200 bg-white px-3 py-2 text-xs font-semibold text-orange-600 hover:bg-orange-50"><Eye size={14}/> Xem</button>
    <button type="button" onClick={download} className="flex items-center gap-1.5 rounded-lg bg-orange-500 px-3 py-2 text-xs font-semibold text-white hover:bg-orange-600"><Download size={14}/> Tải xuống</button>
  </div>;
}

function fileBlob(file) {
  const dataUrl = file.dataUrl || "";
  const comma = dataUrl.indexOf(",");
  if (!dataUrl.startsWith("data:") || comma < 0) throw new Error("Dữ liệu file không hợp lệ.");
  const metadata = dataUrl.slice(5, comma);
  const encoded = dataUrl.slice(comma + 1);
  const type = file.contentType || metadata.split(";")[0] || "application/octet-stream";
  const binary = metadata.includes(";base64") ? atob(encoded) : decodeURIComponent(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type });
}

function canPreview(file) {
  const type = (file.contentType || "").toLowerCase();
  const name = (file.name || "").toLowerCase();
  return type.startsWith("image/") || type.startsWith("text/") || type.startsWith("audio/") || type.startsWith("video/")
    || type === "application/pdf" || /\.(pdf|png|jpe?g|gif|webp|svg|txt|csv)$/i.test(name);
}

function escapeHtml(value = "") {
  return String(value).replace(/[&<>"']/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" })[character]);
}

function formatFileSize(size) {
  if (!Number.isFinite(Number(size))) return "Tệp đính kèm";
  const bytes = Number(size);
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.ceil(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
