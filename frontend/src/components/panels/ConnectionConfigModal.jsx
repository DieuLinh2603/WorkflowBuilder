import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Plus, Trash2, X } from 'lucide-react';
import { apiFetch } from '../../api';
import CalculationExpressionBuilder, { createCalculationExpression, isCalculationExpressionValid } from './CalculationExpressionBuilder';

const OPERATORS = {
  EQ: 'Bằng (=)', NEQ: 'Khác (≠)', GT: 'Lớn hơn (>)', GTE: 'Lớn hơn hoặc bằng (≥)',
  LT: 'Nhỏ hơn (<)', LTE: 'Nhỏ hơn hoặc bằng (≤)', CONTAINS: 'Có chứa', NOT_CONTAINS: 'Không chứa',
  IS_EMPTY: 'Đang trống', NOT_EMPTY: 'Không trống'
};

const OPERATOR_BY_TYPE = {
  TEXT: ['EQ', 'NEQ', 'CONTAINS', 'IS_EMPTY', 'NOT_EMPTY'],
  NUMBER: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'NOT_EMPTY'],
  DATE: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'NOT_EMPTY'],
  DATETIME: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'NOT_EMPTY'],
  SELECT: ['EQ', 'NEQ', 'IS_EMPTY', 'NOT_EMPTY'],
  RADIO: ['EQ', 'NEQ', 'IS_EMPTY', 'NOT_EMPTY'],
  MULTI_CHOICE: ['CONTAINS', 'NOT_CONTAINS', 'IS_EMPTY', 'NOT_EMPTY'],
  USER_PICKER: ['EQ', 'NEQ', 'CONTAINS', 'NOT_CONTAINS', 'IS_EMPTY', 'NOT_EMPTY'],
  CHECKBOX: ['EQ', 'NEQ', 'IS_EMPTY', 'NOT_EMPTY'],
  FILE: ['IS_EMPTY', 'NOT_EMPTY']
};

const emptyClause = (field) => ({
  fieldKey: field?.fieldKey || '',
  operator: OPERATOR_BY_TYPE[field?.type]?.[0] || 'EQ',
  expectedValue: ''
});
const emptyGroup = (field, operator = 'AND') => ({ operator, clauses: [emptyClause(field)] });
const isGroupExpression = expression => expression?.builderMode === 'CONDITION_GROUPS'
  && ['AND', 'OR'].includes(expression.type) && Array.isArray(expression.children);
const isCombinedExpression = expression => expression?.builderMode === 'COMBINED_CONDITION'
  && ['AND', 'OR'].includes(expression.type) && Array.isArray(expression.children);
const parseStoredExpression = clause => {
  const stored = clause?.expression ?? clause?.expressionJson;
  if (!stored) return null;
  if (typeof stored !== 'string') return stored;
  try { return JSON.parse(stored); } catch { return null; }
};
const expressionClause = expression => ({
  fieldKey: expression?.left?.fieldKey || '', operator: expression?.operator || 'EQ',
  expectedValue: expression?.right?.value ?? ''
});
const groupsFromExpression = expression => expression.children.map(group => ({
  operator: ['AND', 'OR'].includes(group?.type) ? group.type : 'AND',
  clauses: (group?.children || []).filter(child => child?.type === 'COMPARE').map(expressionClause)
})).filter(group => group.clauses.length);
const clauseExpression = clause => ({
  type: 'COMPARE', operator: clause.operator,
  left: { type: 'FIELD', fieldKey: clause.fieldKey },
  ...(['IS_EMPTY', 'NOT_EMPTY'].includes(clause.operator) ? {} : { right: { type: 'VALUE', value: String(clause.expectedValue) } })
});
const groupsExpression = (groups, operator) => ({
  type: operator, builderMode: 'CONDITION_GROUPS',
  children: groups.map(group => ({ type: group.operator, children: group.clauses.map(clauseExpression) }))
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
  const assignmentSource = sourceStepType === 'ASSIGNMENT';
  const systemSource = sourceStepType === 'SYSTEM_ACTION';
  const resultSource = approvalSource || reviewSource || assignmentSource || systemSource;
  const forcedTypes = ['APPROVE', 'REJECT', 'REVIEW_PASS', 'REVIEW_FAIL', 'ASSIGNMENT_DONE', 'ASSIGNMENT_FAIL', 'SYSTEM_SUCCESS', 'SYSTEM_FAIL'];
  const forced = !editing && forcedTypes.includes(connection?.sourceHandle)
    ? connection.sourceHandle : null;
  const [type, setType] = useState(connection?.currentType || forced || (resultSource ? '' : 'DEFAULT'));
  const storedExpression = connection?.clauses?.map(parseStoredExpression).find(Boolean) || null;
  const storedCombined = isCombinedExpression(storedExpression);
  const storedGroupExpression = storedCombined
    ? storedExpression.children.find(isGroupExpression)
    : isGroupExpression(storedExpression) ? storedExpression : null;
  const storedCalculationExpression = storedCombined
    ? storedExpression.children.find(item => !isGroupExpression(item))
    : storedExpression && !storedGroupExpression ? storedExpression : null;
  const storedGroups = storedGroupExpression ? groupsFromExpression(storedGroupExpression) : null;
  const [logicalOperator, setLogicalOperator] = useState(storedGroupExpression?.type || connection?.logicalOperator || 'AND');
  const [combineOperator, setCombineOperator] = useState(storedCombined ? storedExpression.type : 'AND');
  const [conditionMode, setConditionMode] = useState(storedCombined ? 'COMBINED' : storedCalculationExpression ? 'CALCULATION' : 'BASIC');
  const [priority, setPriority] = useState(connection?.priority ?? 100);
  const simpleClauses = connection?.clauses?.filter(item => !item.expression)
    .map(({ fieldKey, operator, expectedValue }) => ({ fieldKey, operator, expectedValue: expectedValue ?? '' })) || [];
  const [groups, setGroups] = useState(storedGroups?.length ? storedGroups
    : [{ operator: connection?.logicalOperator || 'AND', clauses: simpleClauses.length ? simpleClauses : [emptyClause()] }]);
  const [advancedExpression, setAdvancedExpression] = useState(storedCalculationExpression || createCalculationExpression());
  const [fields, setFields] = useState([]);
  const [loadingFields, setLoadingFields] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!workflowId || (resultSource && !systemSource)) return;
    let active = true;
    setLoadingFields(true);
    readJson(apiFetch(`/api/workflows/${workflowId}/fields`))
      .then(items => {
        if (!active) return;
        const list = Array.isArray(items) ? items : [];
        setFields(list);
        setGroups(current => current.map((group, groupIndex) => ({ ...group,
          clauses: group.clauses.map((clause, clauseIndex) => clause.fieldKey || !list.length || groupIndex > 0 || clauseIndex > 0
            ? clause : emptyClause(list[0]))
        })));
      })
      .catch(reason => active && setError(reason.message || 'Không thể tải các trường của workflow'))
      .finally(() => active && setLoadingFields(false));
    return () => { active = false; };
  }, [workflowId, resultSource, systemSource]);

  const fieldsByKey = useMemo(() => new Map(fields.map(field => [field.fieldKey, field])), [fields]);
  const invalidClause = clause => {
    const field = fieldsByKey.get(clause.fieldKey);
    if (!field) return true;
    if (clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY') return false;
    return String(clause.expectedValue ?? '').trim() === '';
  };
  const invalidApprovalType = approvalSource && type !== 'APPROVE' && type !== 'REJECT';
  const invalidReviewType = reviewSource && type !== 'REVIEW_PASS' && type !== 'REVIEW_FAIL';
  const invalidAssignmentType = assignmentSource && type !== 'ASSIGNMENT_DONE' && type !== 'ASSIGNMENT_FAIL';
  const invalidSystemType = systemSource && !['SYSTEM_SUCCESS', 'SYSTEM_FAIL', 'IF', 'ELSE'].includes(type);
  const basicEnabled = conditionMode === 'BASIC' || conditionMode === 'COMBINED';
  const calculationEnabled = conditionMode === 'CALCULATION' || conditionMode === 'COMBINED';
  const invalidBasic = !groups.length || groups.some(group => !group.clauses.length || group.clauses.some(invalidClause));
  const invalidCalculation = !isCalculationExpressionValid(advancedExpression, fields);
  const invalidCondition = type === 'IF' && (loadingFields || !fields.length
    || (basicEnabled && invalidBasic) || (calculationEnabled && invalidCalculation));

  const updateClause = (groupIndex, index, property, value) => setGroups(current => current.map((group, currentGroupIndex) => currentGroupIndex !== groupIndex ? group : ({ ...group, clauses: group.clauses.map((clause, clauseIndex) => {
    if (clauseIndex !== index) return clause;
    if (property === 'fieldKey') return emptyClause(fieldsByKey.get(value));
    return { ...clause, [property]: value };
  }) })));
  const addClause = groupIndex => setGroups(current => current.map((group, index) => index === groupIndex ? ({ ...group, clauses: [...group.clauses, emptyClause(fields[0])] }) : group));
  const removeClause = (groupIndex, clauseIndex) => setGroups(current => current.map((group, index) => index !== groupIndex ? group : ({ ...group, clauses: group.clauses.length === 1 ? [emptyClause(fields[0])] : group.clauses.filter((_, index) => index !== clauseIndex) })));
  const updateGroupOperator = (groupIndex, operator) => setGroups(current => current.map((group, index) => index === groupIndex ? { ...group, operator } : group));
  const addGroup = () => setGroups(current => [...current, emptyGroup(fields[0])]);
  const removeGroup = groupIndex => setGroups(current => current.length === 1 ? current : current.filter((_, index) => index !== groupIndex));

  const submit = async () => {
    setSaving(true); setError('');
    try {
      const basicExpression = groupsExpression(groups, logicalOperator);
      const conditionExpression = conditionMode === 'BASIC' ? basicExpression
        : conditionMode === 'CALCULATION' ? advancedExpression
        : { type: combineOperator, builderMode: 'COMBINED_CONDITION', children: [basicExpression, advancedExpression] };
      await onSave({
        fromStepId: connection.source,
        toStepId: connection.target,
        type,
        logicalOperator,
        priority: Number(priority),
        clauses: type === 'IF' ? [{ expression: conditionExpression }] : []
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

  const previewClause = clause => {
    const field = fieldsByKey.get(clause.fieldKey);
    const value = clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY' ? '' : ` ${clause.expectedValue || '...'}`;
    return `${field?.label || 'Chọn trường'} ${OPERATORS[clause.operator] || clause.operator}${value}`;
  };
  const preview = groups.map(group => `(${group.clauses.map(previewClause).join(group.operator === 'AND' ? ' VÀ ' : ' HOẶC ')})`)
    .join(logicalOperator === 'AND' ? ' VÀ ' : ' HOẶC ');

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
          <select value={type} onChange={event => setType(event.target.value)} disabled={!!forced && !(systemSource && forced === 'SYSTEM_SUCCESS')} className="input-field mt-2 bg-white">
            {approvalSource
              ? <><option value="">Chọn nhánh...</option><option value="APPROVE">APPROVE — Được duyệt</option><option value="REJECT">REJECT — Bị từ chối</option></>
              : assignmentSource
                ? <><option value="ASSIGNMENT_DONE">HOÀN THÀNH</option><option value="ASSIGNMENT_FAIL">THẤT BẠI</option></>
              : systemSource
                ? <><option value="SYSTEM_SUCCESS">THÀNH CÔNG — không rẽ điều kiện</option><option value="IF">IF — theo field kết quả API</option><option value="ELSE">ELSE — khi IF không thỏa</option><option value="SYSTEM_FAIL">THẤT BẠI — API hoặc mapping lỗi</option></>
              : reviewSource
                ? <><option value="">Chọn kết quả...</option><option value="REVIEW_PASS">ĐẠT — Chuyển tiếp</option><option value="REVIEW_FAIL">KHÔNG ĐẠT — Chuyển sang xử lý lỗi</option></>
              : <><option value="DEFAULT">Luồng mặc định</option><option value="IF">IF — khi điều kiện đúng</option><option value="ELSE">ELSE — khi IF không thỏa</option></>}
          </select>
        </label>

        {type === 'IF' && <div className="space-y-4">
          <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div><p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-slate-500">Thành phần điều kiện</p><div className="flex flex-wrap gap-2">
                <label className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold ${basicEnabled ? 'border-orange-300 bg-orange-50 text-orange-700' : 'border-slate-200 bg-white text-slate-500'}`}><input type="checkbox" checked={basicEnabled} disabled={basicEnabled && !calculationEnabled} onChange={event => setConditionMode(event.target.checked ? (calculationEnabled ? 'COMBINED' : 'BASIC') : 'CALCULATION')} className="accent-orange-500"/>Điều kiện cơ bản</label>
                <label className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold ${calculationEnabled ? 'border-orange-300 bg-orange-50 text-orange-700' : 'border-slate-200 bg-white text-slate-500'}`}><input type="checkbox" checked={calculationEnabled} disabled={calculationEnabled && !basicEnabled} onChange={event => setConditionMode(event.target.checked ? (basicEnabled ? 'COMBINED' : 'CALCULATION') : 'BASIC')} className="accent-orange-500"/>Phép tính</label>
              </div></div>
              <label className="w-28 text-[11px] font-bold uppercase tracking-wide text-slate-500">Ưu tiên
                <input type="number" min="0" max="10000" value={priority} onChange={event => setPriority(event.target.value)} className="input-field mt-2 bg-white" />
              </label>
            </div>
            {conditionMode === 'COMBINED' && <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 pt-3"><span className="text-xs font-semibold text-slate-600">Kết hợp hai phần bằng</span><LogicToggle value={combineOperator} onChange={setCombineOperator}/></div>}
          </div>
          <div className={basicEnabled ? 'space-y-4' : 'hidden'}>
          {loadingFields && <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">Đang lấy các trường từ form trong workflow...</div>}
          {!loadingFields && !fields.length && <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700"><AlertTriangle size={17} className="mt-0.5 shrink-0" /><span>Workflow chưa có trường dữ liệu. Hãy thêm field vào Start, Review, Approval hoặc Assignment Step trước khi tạo IF.</span></div>}

          {groups.length > 1 && <div className="rounded-xl border border-blue-100 bg-blue-50/60 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xs font-semibold text-blue-700">Kết hợp giữa các nhóm bằng</span><LogicToggle value={logicalOperator} onChange={setLogicalOperator}/></div>
          </div>}

          {groups.map((group, groupIndex) => <div key={groupIndex} className="rounded-xl border border-slate-200 bg-slate-50/60 p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <div><p className="text-sm font-bold text-slate-700">Nhóm điều kiện {groupIndex + 1}</p><p className="mt-0.5 text-[10px] text-slate-400">Các điều kiện trong nhóm được kết hợp bằng {group.operator}</p></div>
              <div className="flex items-center gap-2"><LogicToggle value={group.operator} onChange={operator => updateGroupOperator(groupIndex, operator)}/>{groups.length > 1 && <button type="button" onClick={() => removeGroup(groupIndex)} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-500" title="Xóa nhóm"><Trash2 size={15}/></button>}</div>
            </div>
            <div className="space-y-3">{group.clauses.map((clause, clauseIndex) => {
              const field = fieldsByKey.get(clause.fieldKey);
              const operators = OPERATOR_BY_TYPE[field?.type] || [];
              const noValue = clause.operator === 'IS_EMPTY' || clause.operator === 'NOT_EMPTY';
              return <div key={clauseIndex}>
                {clauseIndex > 0 && <p className="mb-2 text-center text-[10px] font-bold text-orange-500">{group.operator === 'AND' ? 'VÀ' : 'HOẶC'}</p>}
                <div className="grid grid-cols-[minmax(180px,1.35fr)_minmax(145px,1fr)_minmax(160px,1.2fr)_36px] items-center gap-3">
                  <select value={clause.fieldKey} onChange={event => updateClause(groupIndex, clauseIndex, 'fieldKey', event.target.value)} className="input-field bg-white">
                    <option value="">Chọn trường form</option>{fields.map(item => <option key={item.fieldKey} value={item.fieldKey}>{item.label} ({item.fieldKey})</option>)}
                  </select>
                  <select value={clause.operator} onChange={event => updateClause(groupIndex, clauseIndex, 'operator', event.target.value)} disabled={!field} className="input-field bg-white">{operators.map(item => <option key={item} value={item}>{OPERATORS[item]}</option>)}</select>
                  {noValue ? <div className="input-field bg-slate-50 text-sm text-slate-400">Không cần giá trị</div>
                    : field?.type === 'CHECKBOX' ? <select value={clause.expectedValue} onChange={event => updateClause(groupIndex, clauseIndex, 'expectedValue', event.target.value)} className="input-field bg-white"><option value="">Chọn giá trị</option><option value="true">Có / Đúng</option><option value="false">Không / Sai</option></select>
                    : ['SELECT','RADIO','MULTI_CHOICE'].includes(field?.type) ? <select value={clause.expectedValue} onChange={event=>updateClause(groupIndex,clauseIndex,'expectedValue',event.target.value)} className="input-field bg-white"><option value="">Chọn giá trị</option>{(field.options||[]).map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select>
                    : <input type={field?.type === 'NUMBER' ? 'number' : field?.type === 'DATE' ? 'date' : field?.type === 'DATETIME' ? 'datetime-local' : 'text'} value={clause.expectedValue} onChange={event => updateClause(groupIndex, clauseIndex, 'expectedValue', event.target.value)} className="input-field" placeholder="Nhập giá trị" />}
                  <button type="button" onClick={() => removeClause(groupIndex, clauseIndex)} className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-500" title="Xóa điều kiện"><Trash2 size={17}/></button>
                </div>
              </div>;
            })}</div>
            {!!fields.length && <button type="button" onClick={() => addClause(groupIndex)} className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-orange-500 hover:text-orange-600"><Plus size={15}/>Thêm điều kiện vào nhóm</button>}
          </div>)}

          {!!fields.length && <button type="button" onClick={addGroup} className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-dashed border-blue-300 py-2.5 text-sm font-semibold text-blue-600 hover:bg-blue-50"><Plus size={16}/>Thêm nhóm điều kiện</button>}

          </div>
          {calculationEnabled && <CalculationExpressionBuilder value={advancedExpression} onChange={setAdvancedExpression} fields={fields} loading={loadingFields} />}
          <div className="rounded-xl bg-slate-50 p-4 text-sm leading-6">
            <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-slate-500">Bản xem trước logic</p>
            <p><span className="font-bold text-orange-500">NẾU (IF)</span> {conditionMode === 'BASIC' ? preview || '...'
              : conditionMode === 'CALCULATION' ? 'biểu thức tính toán trả về true'
              : `(${preview || '...'}) ${combineOperator === 'AND' ? 'VÀ' : 'HOẶC'} (biểu thức tính toán trả về true)`}</p>
            <p><span className="font-bold text-blue-500">THÌ</span> chuyển tới step: <span className="font-semibold">{targetStepLabel || connection.target}</span></p>
            <p className="mt-2 text-xs leading-5 text-slate-400">Có thể tạo nhiều nhánh IF để phân loại A/B/C hoặc lọc theo tiêu chí. Hệ thống chọn nhánh IF thỏa đầu tiên; nên thiết kế các điều kiện không giao nhau. Mỗi step có IF bắt buộc phải có một nhánh ELSE để xử lý khi không điều kiện nào thỏa.</p>
          </div>
        </div>}

        {type === 'ELSE' && <div className="rounded-lg bg-slate-50 p-4 text-sm leading-6 text-slate-600">ELSE không chứa biểu thức. Nhánh này chạy khi IF của cùng step không thỏa và phải nối tới một step khác nhánh IF.</div>}
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
      </div>

      <div className="flex shrink-0 items-center justify-between border-t border-slate-100 px-6 py-4">
        {editing && onDelete ? <button type="button" onClick={remove} disabled={saving} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-red-500 hover:bg-red-50"><Trash2 size={15} />Xóa connection</button> : <span />}
        <div className="flex gap-2"><button type="button" onClick={onClose} disabled={saving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50">Hủy</button><button type="button" onClick={submit} disabled={saving || invalidApprovalType || invalidReviewType || invalidAssignmentType || invalidSystemType || invalidCondition} className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-semibold text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50">{saving ? 'Đang lưu...' : type === 'IF' ? 'Áp dụng' : 'Lưu'}</button></div>
      </div>
    </div>
  </div>;
}

function LogicToggle({ value, onChange }) {
  return <div className="inline-flex rounded-full bg-white p-1 text-xs font-semibold shadow-sm ring-1 ring-slate-200">
    <button type="button" onClick={() => onChange('AND')} className={`rounded-full px-3 py-1.5 ${value === 'AND' ? 'bg-orange-500 text-white' : 'text-slate-500 hover:text-slate-700'}`}>VÀ (AND)</button>
    <button type="button" onClick={() => onChange('OR')} className={`rounded-full px-3 py-1.5 ${value === 'OR' ? 'bg-orange-500 text-white' : 'text-slate-500 hover:text-slate-700'}`}>HOẶC (OR)</button>
  </div>;
}
