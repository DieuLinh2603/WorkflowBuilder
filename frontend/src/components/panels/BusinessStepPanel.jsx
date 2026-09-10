import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, SlidersHorizontal, Trash2, UserRound, X } from 'lucide-react';
import { apiFetch } from '../../api';
import AddFieldModal from './AddFieldModal';
import { CompletionPolicy } from './StepPanelShared';

const DEFAULT_APPROVAL = {
  mode: 'MANUAL',
  approverMode: 'FIXED_USER',
  actorUserIds: [],
  fixedUserEmail: '',
  actorRole: '',
  dynamicActorSource: 'REQUEST_CREATOR_MANAGER',
  requesterDepartmentScope: false,
  completionMode: 'ANY',
  completionPercentage: 50,
  deadlineHours: 48,
  deadlineDate: null,
  escalationAction: 'REMIND',
  reminderChannels: ['EMAIL', 'TEAMS'],
  autoConditions: [],
  logicalOperator: 'AND'
};

const APPROVER_MODES = [
  { value: 'FIXED_USER', label: 'Fixed User' },
  { value: 'ROLE_BASED', label: 'Role Based' },
  { value: 'DYNAMIC', label: 'Dynamic' }
];

const DEADLINES = [
  { value: '', label: 'Không đặt deadline' },
  { value: 24, label: '1 ngày' },
  { value: 48, label: '2 ngày' },
  { value: 72, label: '3 ngày' },
  { value: 168, label: '7 ngày' },
  { value: 'CUSTOM', label: 'Tùy chỉnh số ngày...' }
];

const ESCALATIONS = [
  { value: 'REMIND', label: 'Nhắc nhở qua Email/Teams' },
  { value: 'ESCALATE_MANAGER', label: 'Tự động chuyển cấp cao hơn' },
  { value: 'AUTO_REJECT', label: 'Tự động reject yêu cầu' }
];

const CONDITION_OPERATORS = {
  EQ: 'Bằng (=)', NEQ: 'Khác (≠)', GT: 'Lớn hơn (>)', GTE: 'Lớn hơn hoặc bằng (≥)',
  LT: 'Nhỏ hơn (<)', LTE: 'Nhỏ hơn hoặc bằng (≤)', CONTAINS: 'Có chứa', NOT_CONTAINS: 'Không chứa',
  IS_EMPTY: 'Đang trống', NOT_EMPTY: 'Không trống'
};

const OPERATORS_BY_FIELD_TYPE = {
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

const emptyAutoCondition = (field) => ({
  fieldKey: field?.fieldKey || '',
  operator: OPERATORS_BY_FIELD_TYPE[field?.type]?.[0] || 'EQ',
  expectedValue: ''
});

export default function BusinessStepPanel({ workflowId, step, onClose, onDelete }) {
  if (step.type === 'NOTIFICATION') {
    return <NotificationStepPanel workflowId={workflowId} step={step} onClose={onClose} onDelete={onDelete} />;
  }
  return <ApprovalStepPanel workflowId={workflowId} step={step} onClose={onClose} onDelete={onDelete} />;
}

function ApprovalStepPanel({ workflowId, step, onClose, onDelete }) {
  const [config, setConfig] = useState(DEFAULT_APPROVAL);
  const [users, setUsers] = useState([]);
  const [fields, setFields] = useState([]);
  const [workflowFields, setWorkflowFields] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [fieldModalOpen, setFieldModalOpen] = useState(false);
  const [fixedUserQuery, setFixedUserQuery] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setMessage(null);
    Promise.all([
      apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(readJson),
      apiFetch('/api/users/active').then(readJson),
      apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields`).then(readJson),
      apiFetch(`/api/workflows/${workflowId}/fields`).then(readJson)
    ]).then(([savedConfig, activeUsers, customFields, allFields]) => {
      if (!active) return;
      setConfig({ ...DEFAULT_APPROVAL, ...savedConfig });
      setUsers(Array.isArray(activeUsers) ? activeUsers : []);
      setFields(Array.isArray(customFields) ? customFields : []);
      setWorkflowFields(Array.isArray(allFields) ? allFields : []);
    }).catch((error) => {
      if (active) setMessage({ type: 'error', text: error.message });
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [workflowId, step.id]);

  const selectedUsers = useMemo(
    () => users.filter((user) => config.actorUserIds?.includes(user.id)),
    [users, config.actorUserIds]
  );
  const companyRoles = useMemo(
    () => [...new Set(users.map((user) => user.jobTitle?.trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi')),
    [users]
  );
  const conditionFields = useMemo(() => {
    const currentStepFieldIds = new Set(fields.map(field => field.id));
    return workflowFields.filter(field => !currentStepFieldIds.has(field.id));
  }, [fields, workflowFields]);
  const conditionFieldsByKey = useMemo(
    () => new Map(conditionFields.map(field => [field.fieldKey, field])),
    [conditionFields]
  );
  const deadlinePreset = config.deadlineDate ? 'CUSTOM' : ['', 24, 48, 72, 168].includes(config.deadlineHours ?? '')
    ? String(config.deadlineHours ?? '') : 'CUSTOM';
  const set = (key, value) => setConfig((current) => ({ ...current, [key]: value }));

  const changeMode = (mode) => setConfig(current => ({
    ...current,
    mode,
    autoConditions: mode === 'AUTO' && !current.autoConditions?.length
      ? [emptyAutoCondition(conditionFields[0])]
      : current.autoConditions || []
  }));

  const updateAutoCondition = (index, key, value) => setConfig(current => ({
    ...current,
    autoConditions: (current.autoConditions || []).map((condition, conditionIndex) => {
      if (conditionIndex !== index) return condition;
      if (key === 'fieldKey') return emptyAutoCondition(conditionFieldsByKey.get(value));
      return { ...condition, [key]: value };
    })
  }));

  const addAutoCondition = () => setConfig(current => ({
    ...current,
    autoConditions: [...(current.autoConditions || []), emptyAutoCondition(conditionFields[0])]
  }));

  const removeAutoCondition = index => setConfig(current => ({
    ...current,
    autoConditions: (current.autoConditions || []).filter((_, conditionIndex) => conditionIndex !== index)
  }));

  const addFixedUser = item => {
    setConfig(current => ({ ...current, fixedUserEmail: '', actorUserIds: [...(current.actorUserIds || []), item.id] }));
    setFixedUserQuery('');
  };
  const removeFixedUser = id => setConfig(current => ({
    ...current, fixedUserEmail: '', actorUserIds: (current.actorUserIds || []).filter(value => value !== id)
  }));

  const save = async () => {
    setMessage(null);
    if (config.mode === 'AUTO') {
      if (!config.autoConditions?.length) {
        setMessage({ type: 'error', text: 'Auto Approval yêu cầu ít nhất một điều kiện.' });
        return;
      }
      const invalid = config.autoConditions.some(condition => {
        const noValue = ['IS_EMPTY', 'NOT_EMPTY'].includes(condition.operator);
        return !conditionFieldsByKey.has(condition.fieldKey) || (!noValue && String(condition.expectedValue ?? '').trim() === '');
      });
      if (invalid) {
        setMessage({ type: 'error', text: 'Hãy cấu hình đầy đủ field, toán tử và giá trị cho các điều kiện tự động.' });
        return;
      }
    }
    setSaving(true);
    try {
      const body = {
        ...config,
        mode: config.mode,
        actorUserIds: config.actorUserIds || [],
        autoConditions: (config.autoConditions || []).map(condition => ({
          ...condition,
          expectedValue: ['IS_EMPTY', 'NOT_EMPTY'].includes(condition.operator) ? null : String(condition.expectedValue)
        })),
        reminderChannels: config.mode === 'MANUAL' && config.escalationAction === 'REMIND' ? ['EMAIL', 'TEAMS'] : []
      };
      const response = await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/approval`, {
        method: 'PUT',
        body: JSON.stringify(body)
      });
      if (!response.ok) throw new Error(await errorMessage(response, 'Không thể lưu cấu hình Approval Step'));
      const saved = await response.json();
      setConfig((current) => ({ ...current, ...saved }));
      setMessage({ type: 'success', text: 'Đã lưu cấu hình Approval Step.' });
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    } finally {
      setSaving(false);
    }
  };

  const addField = async (fieldData) => {
    const response = await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields`, {
      method: 'POST',
      body: JSON.stringify(fieldData)
    });
    if (!response.ok) {
      setMessage({ type: 'error', text: await errorMessage(response, 'Không thể thêm field') });
      return;
    }
    const created = await response.json();
    setFields((current) => [...current, created]);
    setWorkflowFields((current) => [...current, created]);
    setFieldModalOpen(false);
  };

  const deleteField = async (fieldId) => {
    if (!window.confirm('Bạn có chắc muốn xóa field này?')) return;
    const response = await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/fields/${fieldId}`, { method: 'DELETE' });
    if (!response.ok) {
      setMessage({ type: 'error', text: await errorMessage(response, 'Không thể xóa field') });
      return;
    }
    setFields((current) => current.filter((field) => field.id !== fieldId));
    setWorkflowFields((current) => current.filter((field) => field.id !== fieldId));
  };

  return (
    <aside className="h-full w-[560px] min-w-[500px] max-w-[55vw] shrink-0 border-l border-grayBorder bg-white shadow-[-4px_0_18px_-8px_rgba(15,23,42,0.18)] z-10 flex flex-col [&_input]:text-sm [&_select]:text-sm [&_textarea]:text-sm">
      <PanelHeader onClose={onClose} onDelete={onDelete} />

      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-5">
        {loading ? <p className="text-sm text-gray-500">Đang tải cấu hình...</p> : (
          <>
            <section>
              <SectionLabel>Hình thức phê duyệt</SectionLabel>
              <div className="grid grid-cols-2 gap-3">
                <ModeChoice active={config.mode === 'MANUAL'} onClick={() => changeMode('MANUAL')} title="MANUAL" description="Tạo task cho người phê duyệt" />
                <ModeChoice active={config.mode === 'AUTO'} onClick={() => changeMode('AUTO')} title="AUTO" description="Tự quyết định theo điều kiện" />
              </div>
            </section>

            {config.mode === 'MANUAL' ? <>
            <div className="flex gap-2 rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-[11px] leading-5 text-blue-700">
              <AlertTriangle size={15} className="mt-0.5 shrink-0" />
              <span>Nhánh <b>REJECT</b> có thể nối về một bước nhập liệu hoặc xử lý trước đó. Khi bị từ chối, request quay lại bước đó và sẽ tạo lượt phê duyệt mới sau khi xử lý xong.</span>
            </div>
            <section>
              <SectionLabel>Approver</SectionLabel>
              <div className="grid grid-cols-3 gap-1 bg-gray-100 rounded-lg p-1">
                {APPROVER_MODES.map((mode) => (
                  <button
                    key={mode.value}
                    type="button"
                    onClick={() => set('approverMode', mode.value)}
                    className={`px-2 py-2 rounded-md text-[11px] font-medium transition ${config.approverMode === mode.value ? 'bg-white text-slate-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                  >
                    {mode.label}
                  </button>
                ))}
              </div>

              <div className="mt-3">
                {config.approverMode === 'FIXED_USER' && (
                  <>
                    <label className="block text-[11px] text-gray-500 mb-1.5">Tìm và chọn người phê duyệt</label>
                    {!!selectedUsers.length && <div className="mb-2 flex flex-wrap gap-1.5">{selectedUsers.map(user => <span key={user.id} className="flex items-center gap-1 rounded-full bg-orange-50 px-2.5 py-1 text-[10px] font-medium text-orange-700">{user.displayName}<button type="button" onClick={() => removeFixedUser(user.id)} aria-label={`Bỏ ${user.displayName}`}><X size={11}/></button></span>)}</div>}
                    <SearchDropdown
                      value={fixedUserQuery}
                      onInput={setFixedUserQuery}
                      onSelect={addFixedUser}
                      placeholder="Nhập tên hoặc email rồi chọn nhiều người..."
                      items={users.filter(user => !config.actorUserIds?.includes(user.id)).map((user) => ({ id: user.id, value: user.email, label: user.displayName, description: `${user.email} · ${user.jobTitle || 'Chưa có chức danh'}` }))}
                    />
                    <p className="mt-2 text-[11px] text-gray-500">Đã chọn {selectedUsers.length} người. Mỗi người sẽ nhận một task phê duyệt riêng.</p>
                  </>
                )}

                {config.approverMode === 'ROLE_BASED' && (
                  <div className="space-y-3">
                    <p className="text-[11px] text-gray-500">Chọn chức danh trong công ty</p>
                    <SearchDropdown
                      value={config.actorRole || ''}
                      onInput={(value) => set('actorRole', value)}
                      onSelect={(item) => set('actorRole', item.value)}
                      placeholder="Tìm Giám đốc, Trưởng phòng A..."
                      items={companyRoles.map((role) => ({ id: role, value: role, label: role, description: `${users.filter((user) => user.jobTitle === role).length} tài khoản active` }))}
                    />
                    <p className="text-[11px] leading-4 text-gray-500">Danh sách lấy từ trường Chức danh (job title) của user. Tất cả user active có đúng chức danh này sẽ nhận task phê duyệt.</p>
                  </div>
                )}

                {config.approverMode === 'DYNAMIC' && (
                  <div className="space-y-3">
                    <p className="text-[11px] text-gray-500">Lấy người duyệt theo dữ liệu động</p>
                    <Select value={config.dynamicActorSource || 'REQUEST_CREATOR_MANAGER'} onChange={(event) => set('dynamicActorSource', event.target.value)}>
                      <option value="REQUEST_CREATOR_MANAGER">Quản lý trực tiếp của người tạo request</option>
                      <option value="PREVIOUS_ACTOR_MANAGER">Quản lý của người xử lý bước trước</option>
                      <option value="REQUEST_CREATOR">Người tạo request</option>
                    </Select>
                    <p className="text-[11px] leading-4 text-gray-500">Người duyệt được xác định tự động từ dữ liệu tổ chức tại thời điểm request đi tới bước này.</p>
                    <div className="flex gap-2 rounded-lg bg-amber-50 border border-amber-100 p-3 text-[11px] leading-4 text-amber-800">
                      <AlertTriangle size={15} className="shrink-0 mt-0.5" />
                      <span>Cần đảm bảo dữ liệu quản lý trực tiếp được cập nhật đầy đủ để tránh không tìm thấy người duyệt.</span>
                    </div>
                  </div>
                )}
              </div>
            </section>

            <CompletionPolicy config={config} setConfig={setConfig} />

            <section>
              <SectionLabel>Deadline</SectionLabel>
              <Select value={deadlinePreset} onChange={(event) => {
                const value = event.target.value;
                if (value === 'CUSTOM') setConfig((current) => ({ ...current, deadlineHours: null, deadlineDate: current.deadlineDate || localDateAfter(4) }));
                else setConfig((current) => ({ ...current, deadlineHours: value ? Number(value) : null, deadlineDate: null }));
              }}>
                {DEADLINES.map((deadline) => <option key={deadline.value || 'none'} value={deadline.value}>{deadline.label}</option>)}
              </Select>
              {deadlinePreset === 'CUSTOM' && (
                <label className="mt-3 block text-xs text-gray-600">
                  <span className="mb-1.5 block font-medium">Chọn ngày deadline</span>
                  <input
                    type="date"
                    min={localDateAfter(0)}
                    value={config.deadlineDate || ''}
                    onChange={(event) => set('deadlineDate', event.target.value || null)}
                    className="w-full rounded-lg border border-grayBorder bg-white px-3 py-2.5 outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100"
                  />
                  <span className="mt-1.5 block text-[10px] text-gray-400">Task sẽ hết hạn lúc 23:59:59 của ngày được chọn.</span>
                </label>
              )}
            </section>

            <section>
              <SectionLabel>Escalation Rule (Quá hạn)</SectionLabel>
              <div className="space-y-2">
                {ESCALATIONS.map((action) => (
                  <label key={action.value} className="flex items-center gap-2 text-xs text-gray-700 cursor-pointer">
                    <input
                      type="radio"
                      name={`escalation-${step.id}`}
                      value={action.value}
                      checked={config.escalationAction === action.value}
                      onChange={(event) => set('escalationAction', event.target.value)}
                      className="accent-orange-500"
                    />
                    {action.label}
                  </label>
                ))}
              </div>
            </section>

            <section>
              <SectionLabel>Custom Input Fields (Yêu cầu nhập liệu)</SectionLabel>
              <div className="space-y-2">
                {fields.map((field) => (
                  <div key={field.id} className="group flex items-center justify-between rounded-lg bg-slate-50 border border-slate-100 px-3 py-2">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-slate-700 truncate">{field.label}</p>
                      <p className="text-[10px] text-gray-400 truncate">{field.fieldKey} | {field.type}</p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className={`text-[10px] ${field.required ? 'text-orange-500' : 'text-gray-400'}`}>{field.required ? 'Bắt buộc' : 'Tùy chọn'}</span>
                      <button type="button" onClick={() => deleteField(field.id)} className="text-gray-400 hover:text-red-500" aria-label={`Xóa ${field.label}`}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                ))}
                {fields.length === 0 && <p className="text-[11px] text-gray-400">Chưa có field yêu cầu nhập liệu.</p>}
                <button type="button" onClick={() => setFieldModalOpen(true)} className="w-full py-2 border border-dashed border-orange-400 text-orange-500 rounded-lg text-xs font-medium hover:bg-orange-50 transition">
                  + &nbsp;Thêm field
                </button>
              </div>
            </section>
            </> : <AutoApprovalConditions
              conditions={config.autoConditions || []}
              fields={conditionFields}
              fieldsByKey={conditionFieldsByKey}
              logicalOperator={config.logicalOperator || 'AND'}
              setLogicalOperator={value => set('logicalOperator', value)}
              onUpdate={updateAutoCondition}
              onAdd={addAutoCondition}
              onRemove={removeAutoCondition}
            />}
          </>
        )}

        {message && (
          <div className={`rounded-lg px-3 py-2 text-xs ${message.type === 'success' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
            {message.text}
          </div>
        )}
      </div>

      <div className="p-5 border-t border-grayBorder bg-white">
        <button type="button" onClick={save} disabled={loading || saving} className="btn-primary w-full py-2.5 text-sm disabled:opacity-60">
          {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
        </button>
      </div>

      <AddFieldModal isOpen={fieldModalOpen} onClose={() => setFieldModalOpen(false)} onSave={addField} />
    </aside>
  );
}

function ModeChoice({ active, onClick, title, description }) {
  return <button type="button" onClick={onClick} className={`rounded-xl border p-3 text-left transition ${active ? 'border-orange-500 bg-orange-50 ring-1 ring-orange-100' : 'border-grayBorder bg-white hover:border-orange-200'}`}>
    <span className="flex items-start gap-2"><span className={`mt-0.5 h-3.5 w-3.5 shrink-0 rounded-full border ${active ? 'border-4 border-orange-500' : 'border-gray-300'}`}/><span><b className="block text-xs text-slate-700">{title}</b><small className="mt-1 block text-[10px] leading-4 text-gray-400">{description}</small></span></span>
  </button>;
}

function AutoApprovalConditions({ conditions, fields, fieldsByKey, logicalOperator, setLogicalOperator, onUpdate, onAdd, onRemove }) {
  return <section className="space-y-4">
    <div>
      <SectionLabel>Điều kiện tự động phê duyệt</SectionLabel>
      <div className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-xs leading-5 text-blue-700">
        Hệ thống không tạo task cho người duyệt. Tất cả điều kiện đúng sẽ đi nhánh <b>APPROVE</b>; không thỏa sẽ đi nhánh <b>REJECT</b>.
      </div>
    </div>

    {!fields.length ? <div className="flex gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-700"><AlertTriangle size={16} className="mt-0.5 shrink-0"/><span>Chưa có field dữ liệu từ các step khác. Hãy thêm field ở Start hoặc một step nhập liệu trước Approval này.</span></div> : <>
      {conditions.length > 1 && <div className="flex justify-center"><div className="inline-flex rounded-full bg-slate-100 p-1 text-xs font-semibold"><button type="button" onClick={() => setLogicalOperator('AND')} className={`rounded-full px-4 py-1.5 ${logicalOperator === 'AND' ? 'bg-orange-500 text-white shadow-sm' : 'text-slate-500'}`}>VÀ (AND)</button><button type="button" onClick={() => setLogicalOperator('OR')} className={`rounded-full px-4 py-1.5 ${logicalOperator === 'OR' ? 'bg-orange-500 text-white shadow-sm' : 'text-slate-500'}`}>HOẶC (OR)</button></div></div>}

      <div className="space-y-3">{conditions.map((condition, index) => {
        const field = fieldsByKey.get(condition.fieldKey);
        const operators = OPERATORS_BY_FIELD_TYPE[field?.type] || [];
        const noValue = ['IS_EMPTY', 'NOT_EMPTY'].includes(condition.operator);
        return <div key={index} className="rounded-xl border border-slate-200 bg-white p-3">
          {index > 0 && <p className="mb-2 text-center text-[10px] font-bold text-orange-500">{logicalOperator === 'AND' ? 'VÀ' : 'HOẶC'}</p>}
          <div className="grid grid-cols-[1.2fr_1fr] gap-2">
            <select value={condition.fieldKey || ''} onChange={event => onUpdate(index, 'fieldKey', event.target.value)} className="input-field bg-white text-xs"><option value="">Chọn field</option>{fields.map(item => <option key={item.id} value={item.fieldKey}>{item.label} ({item.fieldKey})</option>)}</select>
            <select value={condition.operator || ''} onChange={event => onUpdate(index, 'operator', event.target.value)} disabled={!field} className="input-field bg-white text-xs">{operators.map(operator => <option key={operator} value={operator}>{CONDITION_OPERATORS[operator]}</option>)}</select>
          </div>
          <div className="mt-2 flex gap-2">
            {noValue ? <div className="input-field flex-1 bg-slate-50 text-xs text-slate-400">Không cần nhập giá trị</div>
              : field?.type === 'CHECKBOX' ? <select value={condition.expectedValue ?? ''} onChange={event => onUpdate(index, 'expectedValue', event.target.value)} className="input-field flex-1 bg-white text-xs"><option value="">Chọn giá trị</option><option value="true">Có / Đúng</option><option value="false">Không / Sai</option></select>
              : ['SELECT','RADIO','MULTI_CHOICE'].includes(field?.type) ? <select value={condition.expectedValue??''} onChange={event=>onUpdate(index,'expectedValue',event.target.value)} className="input-field flex-1 bg-white text-xs"><option value="">Chọn giá trị</option>{(field.options||[]).map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select>
              : <input type={field?.type === 'NUMBER' ? 'number' : field?.type === 'DATE' ? 'date' : field?.type === 'DATETIME' ? 'datetime-local' : 'text'} value={condition.expectedValue ?? ''} onChange={event => onUpdate(index, 'expectedValue', event.target.value)} disabled={!field} className="input-field flex-1 text-xs" placeholder="Giá trị so sánh"/>}
            <button type="button" onClick={() => onRemove(index)} className="flex w-10 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:border-red-200 hover:bg-red-50 hover:text-red-500" title="Xóa điều kiện"><Trash2 size={15}/></button>
          </div>
        </div>;
      })}</div>

      <button type="button" onClick={onAdd} className="w-full rounded-lg border border-dashed border-orange-400 py-2.5 text-xs font-semibold text-orange-500 hover:bg-orange-50">+ Thêm điều kiện</button>
    </>}

    <div className="rounded-xl bg-slate-50 p-4 text-xs leading-5 text-slate-600"><b>Yêu cầu trên canvas:</b> Auto Approval phải có đủ connection <span className="font-bold text-emerald-600">APPROVE</span> và <span className="font-bold text-red-500">REJECT</span>.</div>
  </section>;
}

function NotificationStepPanel({ workflowId, step, onClose }) {
  const [config, setConfig] = useState({ titleTemplate: '', bodyTemplate: '', recipientUserIds: [], includeRequester: true });
  const [message, setMessage] = useState(null);
  useEffect(() => {
    apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(readJson).then((value) => setConfig((current) => ({ ...current, ...value }))).catch((error) => setMessage(error.message));
  }, [workflowId, step.id]);
  const set = (key, value) => setConfig((current) => ({ ...current, [key]: value }));
  const save = async () => {
    const response = await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/notification`, { method: 'PUT', body: JSON.stringify(config) });
    setMessage(response.ok ? 'Đã lưu cấu hình.' : await errorMessage(response, 'Không thể lưu cấu hình'));
  };
  return <aside className="h-full w-[560px] min-w-[500px] max-w-[55vw] shrink-0 border-l border-grayBorder bg-white flex flex-col">
    <div className="p-4 border-b flex justify-between"><b>Notification in-app</b><button onClick={onClose}><X size={18} /></button></div>
    <div className="p-5 flex-1 overflow-auto space-y-4">
      <label className="block text-sm font-semibold">Tiêu đề<input className="input-field mt-1" value={config.titleTemplate || ''} onChange={(event) => set('titleTemplate', event.target.value)} /></label>
      <label className="block text-sm font-semibold">Nội dung<textarea className="input-field mt-1" value={config.bodyTemplate || ''} onChange={(event) => set('bodyTemplate', event.target.value)} /></label>
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={!!config.includeRequester} onChange={(event) => set('includeRequester', event.target.checked)} />Gửi requester</label>
      {message && <p className="text-xs text-gray-500">{message}</p>}
    </div>
    <div className="p-4 border-t"><button onClick={save} className="btn-primary w-full py-2">Lưu cấu hình</button></div>
  </aside>;
}

function PanelHeader({ onClose, onDelete }) {
  return <div className="px-5 py-4 border-b border-grayBorder flex items-center justify-between">
    <div className="flex items-center gap-3">
      <SlidersHorizontal size={16} className="text-orange-500" />
      <h2 className="font-bold text-sm text-slate-800">Cấu hình: Approval Step</h2>
    </div>
    <div className="flex items-center gap-1">{onDelete && <button type="button" onClick={onDelete} className="rounded-full p-2 text-gray-400 hover:bg-red-50 hover:text-red-500" aria-label="Xóa step"><Trash2 size={17} /></button>}<button type="button" onClick={onClose} className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700" aria-label="Đóng panel"><X size={17} /></button></div>
  </div>;
}

function SectionLabel({ children }) {
  return <h3 className="mb-2 text-[10px] font-bold uppercase tracking-wide text-slate-500">{children}</h3>;
}

function Select({ children, ...props }) {
  return <div className="relative">
    <UserRound size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-orange-500 pointer-events-none" />
    <select {...props} className="w-full appearance-none rounded-lg border border-grayBorder bg-white py-2.5 pl-9 pr-8 text-xs text-slate-700 outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100">
      {children}
    </select>
    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">⌄</span>
  </div>;
}

function SearchDropdown({ value, onInput, onSelect, items, placeholder }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const query = value.trim().toLocaleLowerCase('vi');
  const filtered = items.filter((item) => {
    const searchable = `${item.label} ${item.value} ${item.description || ''}`.toLocaleLowerCase('vi');
    return !query || searchable.includes(query);
  });

  useEffect(() => {
    const closeOnOutsideClick = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    return () => document.removeEventListener('mousedown', closeOnOutsideClick);
  }, []);

  return <div ref={rootRef} className="relative">
    <UserRound size={15} className="absolute left-3 top-[18px] -translate-y-1/2 text-orange-500 z-10 pointer-events-none" />
    <input
      value={value}
      onFocus={() => setOpen(true)}
      onClick={() => setOpen(true)}
      onChange={(event) => { onInput(event.target.value); setOpen(true); }}
      placeholder={placeholder}
      autoComplete="off"
      className="w-full rounded-lg border border-grayBorder bg-white py-2.5 pl-9 pr-8 text-xs text-slate-700 outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100"
    />
    <button type="button" onClick={() => setOpen((current) => !current)} className="absolute right-2 top-[18px] -translate-y-1/2 px-1 text-gray-400" aria-label="Mở danh sách">⌄</button>
    {open && (
      <div className="absolute z-30 mt-1 w-full max-h-52 overflow-y-auto rounded-lg border border-grayBorder bg-white py-1 shadow-xl">
        {filtered.length ? filtered.map((item) => (
          <button
            key={item.id}
            type="button"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => { onSelect(item); setOpen(false); }}
            className="block w-full border-b border-slate-50 px-3 py-2.5 text-left last:border-0 hover:bg-orange-50"
          >
            <span className="block text-xs font-semibold text-slate-700">{item.label}</span>
            {item.description && <span className="mt-0.5 block text-[10px] text-gray-400">{item.description}</span>}
          </button>
        )) : <p className="px-3 py-4 text-center text-xs text-gray-400">Không tìm thấy dữ liệu phù hợp</p>}
      </div>
    )}
  </div>;
}

function localDateAfter(days) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

async function readJson(response) {
  if (!response.ok) throw new Error(await errorMessage(response, 'Không thể tải dữ liệu'));
  return response.json();
}

async function errorMessage(response, fallback) {
  const body = await response.json().catch(() => null);
  return body?.message || fallback;
}
