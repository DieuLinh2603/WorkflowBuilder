import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Plus, Trash2, X } from 'lucide-react';
import { apiFetch } from '../../api';
import CalculationExpressionBuilder, { createCalculationExpression, isCalculationExpressionValid } from './CalculationExpressionBuilder';

const OPERATORS = {
  EQ: 'Bằng (=)', NEQ: 'Khác (≠)', GT: 'Lớn hơn (>)', GTE: 'Lớn hơn hoặc bằng (≥)',
  LT: 'Nhỏ hơn (<)', LTE: 'Nhỏ hơn hoặc bằng (≤)', CONTAINS: 'Có chứa',
  IS_EMPTY: 'Đang trống', NOT_EMPTY: 'Không trống'
};

const OPERATOR_BY_TYPE = {
  TEXT: ['EQ', 'NEQ', 'CONTAINS', 'IS_EMPTY', 'NOT_EMPTY'],
  NUMBER: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'NOT_EMPTY'],
  DATE: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'NOT_EMPTY'],
  CHECKBOX: ['EQ', 'NEQ', 'IS_EMPTY', 'NOT_EMPTY'],
  FILE: ['IS_EMPTY', 'NOT_EMPTY']
};

const emptyClause = (field) => ({
  fieldKey: field?.fieldKey || '',
  operator: OPERATOR_BY_TYPE[field?.type]?.[0] || 'EQ',
  expectedValue: ''
});

async function readJson(responsePromise) {
  const response = await responsePromise;
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try { const body = await response.json(); message = body.message || body.error || message; } catch { /* empty response */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

export default function ConnectionConfigModal({
  workflowId, connection, sourceStepType, targetStepLabel, editing = false, onSave, onDelete, onClose
}) {
  const approvalSource = sourceStepType === 'APPROVAL';
  const reviewSource = sourceStepType === 'REVIEW';
  const resultSource = approvalSource || reviewSource;
  const forcedTypes = ['APPROVE', 'REJECT', 'REVIEW_PASS', 'REVIEW_FAIL'];
  const forced = !editing && forcedTypes.includes(connection?.sourceHandle)
    ? connection.sourceHandle : null;
  const [type, setType] = useState(connection?.currentType || forced || (resultSource ? '' : 'DEFAULT'));
  const [logicalOperator, setLogicalOperator] = useState(connection?.logicalOperator || 'AND');
  const [priority, setPriority] = useState(connection?.priority ?? 100);
  const [clauses, setClauses] = useState(connection?.clauses?.length
    ? connection.clauses.map(({ fieldKey, operator, expectedValue, expression }) => ({ fieldKey, operator, expectedValue: expectedValue ?? '', expression }))
    : [emptyClause()]);
  const existingExpression = connection?.clauses?.find(item => item.expression)?.expression;
  const [advanced, setAdvanced] = useState(!!existingExpression);
  const [advancedExpression, setAdvancedExpression] = useState(existingExpression || createCalculationExpression());
  const [fields, setFields] = useState([]);
  const [loadingFields, setLoadingFields] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!workflowId || resultSource) return;
    let active = true;
    setLoadingFields(true);
    readJson(apiFetch(`/api/workflows/${workflowId}/fields`))
      .then(items => {
        if (!active) return;
        const list = Array.isArray(items) ? items : [];
        setFields(list);
        setClauses(current => current.map((clause, index) => {
          if (clause.fieldKey || !list.length || index > 0) return clause;
          return emptyClause(list[0]);
        }));
      })
      .catch(reason => active && setError(reason.message || 'Không thể tải các trường của workflow'))
      .finally(() => active && setLoadingFields(false));
    return () => { active = false; };
  }, [workflowId, resultSource]);

  const fieldsByKey = useMemo(() => new Map(fields.map(field => [field.fieldKey, field])), [fields]);
  const invalidClause = clause => {
    const field = fieldsByKey.get(clause.fieldKey);
    if (!field) return true;
    if (clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY') return false;
    return String(clause.expectedValue ?? '').trim() === '';
  };
  const invalidApprovalType = approvalSource && type !== 'APPROVE' && type !== 'REJECT';
  const invalidReviewType = reviewSource && type !== 'REVIEW_PASS' && type !== 'REVIEW_FAIL';
  const invalidCondition = type === 'IF' && (loadingFields || !fields.length || (advanced ? !isCalculationExpressionValid(advancedExpression, fields) : !clauses.length || clauses.some(invalidClause)));

  const updateClause = (index, property, value) => setClauses(current => current.map((clause, clauseIndex) => {
    if (clauseIndex !== index) return clause;
    if (property === 'fieldKey') return emptyClause(fieldsByKey.get(value));
    return { ...clause, [property]: value };
  }));
  const addClause = () => setClauses(current => [...current, emptyClause(fields[0])]);
  const removeClause = index => setClauses(current => current.length === 1
    ? [emptyClause(fields[0])]
    : current.filter((_, clauseIndex) => clauseIndex !== index));

  const submit = async () => {
    setSaving(true); setError('');
    try {
      await onSave({
        fromStepId: connection.source,
        toStepId: connection.target,
        type,
        logicalOperator,
        priority: Number(priority),
        clauses: type === 'IF' ? (advanced ? [{ expression: advancedExpression }] : clauses.map(clause => ({
          ...clause,
          expectedValue: clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY' ? null : String(clause.expectedValue)
        }))) : []
      });
    } catch (reason) {
      setError(reason.message || 'Không thể lưu connection');
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!window.confirm('Bạn có chắc muốn xóa connection này?')) return;
    setSaving(true); setError('');
    try { await onDelete(); } catch (reason) { setError(reason.message || 'Không thể xóa connection'); setSaving(false); }
  };

  const preview = clauses.map(clause => {
    const field = fieldsByKey.get(clause.fieldKey);
    const value = clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY' ? '' : ` ${clause.expectedValue || '...'}`;
    return `${field?.label || 'Chọn trường'} ${OPERATORS[clause.operator] || clause.operator}${value}`;
  }).join(logicalOperator === 'AND' ? ' VÀ ' : ' HOẶC ');

  return <div className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-[1px]">
    <div className={`flex max-h-[calc(100vh-2rem)] w-full flex-col ${type === 'IF' ? 'max-w-[760px]' : 'max-w-[500px]'} overflow-hidden rounded-2xl bg-white shadow-2xl`}>
      <div className="flex shrink-0 items-center justify-between border-b border-slate-100 px-6 py-5">
        <h3 className="text-lg font-bold text-slate-800">{type === 'IF' ? (editing ? 'Chỉnh sửa điều kiện rẽ nhánh' : 'Thêm điều kiện rẽ nhánh') : editing ? 'Chỉnh sửa connection' : 'Cấu hình connection'}</h3>
        <button type="button" onClick={onClose} disabled={saving} className="rounded-full p-1.5 text-gray-400 hover:bg-gray-100"><X size={18} /></button>
      </div>

      <div className="space-y-5 overflow-y-auto px-6 py-5">
        {approvalSource && connection?.currentType === 'DEFAULT' && <div className="flex gap-2 rounded-lg border border-amber-100 bg-amber-50 p-3 text-xs leading-5 text-amber-700"><AlertTriangle size={16} className="mt-0.5 shrink-0" /><span>Connection DEFAULT cũ chưa được Approval engine sử dụng. Hãy phân loại thành Approve hoặc Reject.</span></div>}
        {reviewSource && connection?.currentType === 'DEFAULT' && <div className="flex gap-2 rounded-lg border border-amber-100 bg-amber-50 p-3 text-xs leading-5 text-amber-700"><AlertTriangle size={16} className="mt-0.5 shrink-0" /><span>Connection DEFAULT cũ đang được coi là nhánh Đạt. Hãy phân loại thành Đạt hoặc Không đạt để luồng Review rõ ràng.</span></div>}

        <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500">Loại connection
          <select value={type} onChange={event => setType(event.target.value)} disabled={!!forced} className="input-field mt-2 bg-white">
            {approvalSource
              ? <><option value="">Chọn nhánh...</option><option value="APPROVE">APPROVE — Được duyệt</option><option value="REJECT">REJECT — Bị từ chối</option></>
              : reviewSource
                ? <><option value="">Chọn kết quả...</option><option value="REVIEW_PASS">ĐẠT — Chuyển tiếp</option><option value="REVIEW_FAIL">KHÔNG ĐẠT — Chuyển sang xử lý lỗi</option></>
              : <><option value="DEFAULT">Luồng mặc định</option><option value="IF">IF — khi điều kiện đúng</option><option value="ELSE">ELSE — khi IF không thỏa</option></>}
          </select>
        </label>

        {type === 'IF' && <div className="space-y-4">
          <div className="flex rounded-lg bg-slate-100 p-1 text-xs font-semibold">
            <button type="button" onClick={() => setAdvanced(false)} className={`flex-1 rounded-md px-3 py-2 ${!advanced ? 'bg-white text-orange-600 shadow-sm' : 'text-slate-500'}`}>Điều kiện cơ bản</button>
            <button type="button" onClick={() => setAdvanced(true)} className={`flex-1 rounded-md px-3 py-2 ${advanced ? 'bg-white text-orange-600 shadow-sm' : 'text-slate-500'}`}>Biểu thức tính toán</button>
          </div>
          <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500">Ưu tiên nhánh
            <input type="number" min="0" max="10000" value={priority} onChange={event => setPriority(event.target.value)} className="input-field mt-2" />
            <span className="mt-1 block font-normal normal-case text-slate-400">Số nhỏ được xét trước. Record dừng tại nhánh IF đầu tiên khớp.</span>
          </label>
          {advanced && <CalculationExpressionBuilder value={advancedExpression} onChange={setAdvancedExpression} fields={fields} loading={loadingFields} />}
          <div className={advanced ? 'hidden' : 'space-y-4'}>
          {loadingFields && <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">Đang lấy các trường từ form trong workflow...</div>}
          {!loadingFields && !fields.length && <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700"><AlertTriangle size={17} className="mt-0.5 shrink-0" /><span>Workflow chưa có trường dữ liệu. Hãy thêm field vào Start, Review, Approval hoặc Assignment Step trước khi tạo IF.</span></div>}

          {clauses.map((clause, index) => {
            const field = fieldsByKey.get(clause.fieldKey);
            const operators = OPERATOR_BY_TYPE[field?.type] || [];
            const noValue = clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY';
            return <div key={index}>
              {index > 0 && <div className="mb-4 flex justify-center">
                <div className="inline-flex rounded-full bg-slate-100 p-1 text-xs font-semibold">
                  <button type="button" onClick={() => setLogicalOperator('AND')} className={`rounded-full px-4 py-1.5 ${logicalOperator === 'AND' ? 'bg-orange-500 text-white shadow-sm' : 'text-slate-500'}`}>VÀ (AND)</button>
                  <button type="button" onClick={() => setLogicalOperator('OR')} className={`rounded-full px-4 py-1.5 ${logicalOperator === 'OR' ? 'bg-orange-500 text-white shadow-sm' : 'text-slate-500'}`}>HOẶC (OR)</button>
                </div>
              </div>}
              <div className="grid grid-cols-[minmax(180px,1.35fr)_minmax(145px,1fr)_minmax(160px,1.2fr)_36px] items-center gap-3">
                <select value={clause.fieldKey} onChange={event => updateClause(index, 'fieldKey', event.target.value)} className="input-field bg-white">
                  <option value="">Chọn trường form</option>
                  {fields.map(item => <option key={item.fieldKey} value={item.fieldKey}>{item.label} ({item.fieldKey})</option>)}
                </select>
                <select value={clause.operator} onChange={event => updateClause(index, 'operator', event.target.value)} disabled={!field} className="input-field bg-white">
                  {operators.map(item => <option key={item} value={item}>{OPERATORS[item]}</option>)}
                </select>
                {noValue ? <div className="input-field bg-slate-50 text-sm text-slate-400">Không cần giá trị</div>
                  : field?.type === 'CHECKBOX' ? <select value={clause.expectedValue} onChange={event => updateClause(index, 'expectedValue', event.target.value)} className="input-field bg-white"><option value="">Chọn giá trị</option><option value="true">Có / Đúng</option><option value="false">Không / Sai</option></select>
                  : <input type={field?.type === 'NUMBER' ? 'number' : field?.type === 'DATE' ? 'date' : 'text'} value={clause.expectedValue} onChange={event => updateClause(index, 'expectedValue', event.target.value)} className="input-field" placeholder="Nhập giá trị" />}
                <button type="button" onClick={() => removeClause(index)} className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-500" title="Xóa điều kiện"><Trash2 size={17} /></button>
              </div>
            </div>;
          })}

          {!!fields.length && <button type="button" onClick={addClause} className="flex items-center gap-1.5 text-sm font-semibold text-orange-500 hover:text-orange-600"><Plus size={16} /> Thêm điều kiện</button>}

          </div>
          <div className="rounded-xl bg-slate-50 p-4 text-sm leading-6">
            <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-slate-500">Bản xem trước logic</p>
            <p><span className="font-bold text-orange-500">NẾU (IF)</span> {advanced ? 'biểu thức tính toán trả về true' : preview || '...'}</p>
            <p><span className="font-bold text-blue-500">THÌ</span> chuyển tới step: <span className="font-semibold">{targetStepLabel || connection.target}</span></p>
            <p className="mt-2 text-xs leading-5 text-slate-400">Có thể tạo nhiều nhánh IF để phân loại A/B/C hoặc lọc theo tiêu chí. Hệ thống chọn nhánh IF thỏa đầu tiên; nên thiết kế các điều kiện không giao nhau. Nếu không nhánh nào thỏa, ELSE sẽ được dùng hoặc request giữ tại step hiện tại.</p>
          </div>
        </div>}

        {type === 'ELSE' && <div className="rounded-lg bg-slate-50 p-4 text-sm leading-6 text-slate-600">ELSE không chứa biểu thức. Nhánh này chạy khi IF của cùng step không thỏa và phải nối tới một step khác nhánh IF.</div>}
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
      </div>

      <div className="flex shrink-0 items-center justify-between border-t border-slate-100 px-6 py-4">
        {editing && onDelete ? <button type="button" onClick={remove} disabled={saving} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-red-500 hover:bg-red-50"><Trash2 size={15} />Xóa connection</button> : <span />}
        <div className="flex gap-2"><button type="button" onClick={onClose} disabled={saving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50">Hủy</button><button type="button" onClick={submit} disabled={saving || invalidApprovalType || invalidReviewType || invalidCondition} className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-semibold text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50">{saving ? 'Đang lưu...' : type === 'IF' ? 'Áp dụng' : 'Lưu'}</button></div>
      </div>
    </div>
  </div>;
}
