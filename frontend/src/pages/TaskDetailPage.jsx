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

export default function TaskDetailPage() {
  const { taskId } = useParams(),
    navigate = useNavigate();
  const [task, setTask] = useState(null),
    [history, setHistory] = useState([]),
    [fields, setFields] = useState({}),
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
        setFields(data.fields || {});
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
  const submit = async (action) => {
    if ((action === "reject" || task.commentRequired) && !comment.trim()) {
      setError(
        action === "reject"
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
          fields: task.fieldsEditable ? fields : {},
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
                  Bạn có thể chỉnh sửa dữ liệu phát hiện sai sót trước khi xác
                  nhận chuyển tiếp.
                </div>
              )}
              <h2 className="mb-5 font-bold text-slate-800">Dữ liệu đầu vào</h2>
              {task.batch ? <BatchRecordsTable records={task.batchRecords || []} historical={completed} /> : <div className="grid grid-cols-2 gap-5">
                {Object.entries(fields).map(([key, value]) => (
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
                    ) : review && task.fieldsEditable && isScalarValue(value) ? (
                      <input
                        value={value ?? ""}
                        onChange={(event) =>
                          setFields((current) => ({
                            ...current,
                            [key]: event.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-grayBorder px-3 py-2.5 text-sm outline-none focus:border-orange-400"
                      />
                    ) : (
                      <div className="min-h-[42px] rounded-lg bg-slate-50 px-3 py-2.5 text-sm text-slate-700">
                        {displayValue(value)}
                      </div>
                    )}
                  </label>
                ))}
              </div>}
            </section>
            <section className="rounded-2xl border border-grayBorder bg-white p-6">
              <h2 className="mb-5 font-bold text-slate-800">
                Lịch sử các bước
              </h2>
              <InstanceJourney history={history} instance={{ status: 'RUNNING', currentStepId: task.stepId }} compact />
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
                  {approval ? "Lý do / nhận xét" : "Nhận xét review"}{" "}
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
                      : "Ghi chú lỗi hoặc nhận xét review..."
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
                {review && (
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
                  <button
                    disabled={submitting}
                    onClick={() => submit("complete")}
                    className="flex w-full items-center justify-center gap-2 rounded-lg bg-orange-500 py-3 text-sm font-semibold text-white"
                  >
                    <FileText size={16} />
                    Hoàn thành
                  </button>
                )}
              </div>}
            </section>
          </aside>
        </div>
      </main>
    </div>
  );
}

function BatchRecordsTable({ records, historical }) {
  if (!records.length && historical) return <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-5 text-sm leading-6 text-slate-600">Các dòng thuộc task này đã được xử lý và chuyển sang bước tiếp theo. Bạn có thể xem kết quả thao tác trong phần <b>Lịch sử các bước</b>.</div>;
  const keys = [...new Set(records.flatMap(record => Object.keys(record.fields || {})))];
  return <div><div className="mb-3 rounded-lg border border-violet-200 bg-violet-50 px-4 py-3 text-sm text-violet-700"><b>{records.length} hồ sơ</b> đang được xử lý cùng nhau trong task này.</div><div className="max-h-[520px] overflow-auto rounded-xl border border-slate-200"><table className="min-w-full text-left text-xs"><thead className="sticky top-0 bg-slate-50"><tr><th className="px-3 py-2">Dòng</th>{keys.map(key => <th key={key} className="whitespace-nowrap px-3 py-2 capitalize">{key.replaceAll('_', ' ')}</th>)}</tr></thead><tbody>{records.map(record => <tr key={record.rowNumber} className="border-t border-slate-100"><td className="px-3 py-2 font-bold text-violet-600">{record.rowNumber}</td>{keys.map(key => <td key={key} className="max-w-64 truncate px-3 py-2 text-slate-600">{displayValue(record.fields?.[key])}</td>)}</tr>)}</tbody></table></div></div>;
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
