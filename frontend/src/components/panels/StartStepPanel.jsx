import { useState, useEffect } from 'react';
import { Pencil, X, Trash2, Zap } from 'lucide-react';
import { apiError, apiFetch } from '../../api';
import useStartStepConfig from '../../hooks/useStartStepConfig';
import AddFieldModal from './AddFieldModal';
import { SearchDropdown } from './StepPanelShared';

const SYSTEM_ROLES = [
  ['ADMIN', 'Quản trị viên'],
  ['WORKFLOW_OWNER', 'Workflow Owner'],
  ['EDITOR', 'Editor'],
  ['VIEWER', 'Viewer'],
];

export default function StartStepPanel({ workflowId, step, onClose }) {
  const { config, error: configError, fetchConfig, updateConfig, addField, updateField, deleteField } = useStartStepConfig(workflowId, step?.id);
  const [instruction, setInstruction] = useState('');
  const [requesterScope, setRequesterScope] = useState('ALL_EMPLOYEES');
  const [allowedUserIds, setAllowedUserIds] = useState([]);
  const [allowedGroupIds, setAllowedGroupIds] = useState([]);
  const [allowedRoles, setAllowedRoles] = useState([]);
  const [groups, setGroups] = useState([]);
  const [users, setUsers] = useState([]);
  const [userQuery, setUserQuery] = useState('');
  const [groupQuery, setGroupQuery] = useState('');
  const [allowRequesterWithdrawal, setAllowRequesterWithdrawal] = useState(true);
  const [submissionMode, setSubmissionMode] = useState('SINGLE');
  const [recordRecipientFieldKey, setRecordRecipientFieldKey] = useState('');
  const [maxBatchRows, setMaxBatchRows] = useState(500);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingField, setEditingField] = useState(null);
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (step?.id) {
      fetchConfig();
      apiFetch('/api/groups', { toast: false })
        .then(async response => {
          if (!response.ok) throw new Error(await apiError(response, 'Không thể tải danh sách nhóm'));
          return response.json();
        })
        .then(data => setGroups(Array.isArray(data) ? data : []))
        .catch(reason => setMessage(reason.message));
      apiFetch('/api/users/active', { toast: false })
        .then(async response => {
          if (!response.ok) throw new Error(await apiError(response, 'Không thể tải danh sách user'));
          return response.json();
        })
        .then(data => setUsers(Array.isArray(data) ? data : []))
        .catch(reason => setMessage(reason.message));
    }
  }, [step?.id, fetchConfig]);

  useEffect(() => {
    setInstruction(config.instructionForCreator || '');
    setRequesterScope(config.requesterScope || 'ALL_EMPLOYEES');
    setAllowedUserIds(config.allowedUserIds || []);
    setAllowedGroupIds(config.allowedGroupIds || []);
    setAllowedRoles(config.allowedRoles || []);
    setAllowRequesterWithdrawal(config.allowRequesterWithdrawal !== false);
    setSubmissionMode(config.submissionMode || 'SINGLE');
    setRecordRecipientFieldKey(config.recordRecipientFieldKey || '');
    setMaxBatchRows(config.maxBatchRows || 500);
  }, [config]);

  const handleSaveConfig = async () => {
    if (requesterScope === 'SPECIFIC_GROUP_ROLE' && !allowedUserIds.length && !allowedGroupIds.length && !allowedRoles.length) {
      setMessage('Hãy chọn ít nhất một user, nhóm hoặc vai trò được phép tạo request.');
      return;
    }
    setSaving(true);
    setMessage('');
    const success = await updateConfig(instruction, requesterScope, allowedUserIds, allowedGroupIds, allowedRoles,
      allowRequesterWithdrawal, submissionMode, recordRecipientFieldKey, maxBatchRows);
    setSaving(false);
    if (success) {
      setMessage('Đã lưu cấu hình Start Step và quyền tạo request.');
    }
  };

  const selectedGroups = groups.filter(group => allowedGroupIds.includes(group.id));
  const selectedUsers = users.filter(user => allowedUserIds.includes(user.id));
  const addUser = item => {
    setAllowedUserIds(current => current.includes(item.id) ? current : [...current, item.id]);
    setUserQuery('');
  };
  const addGroup = item => {
    setAllowedGroupIds(current => current.includes(item.id) ? current : [...current, item.id]);
    setGroupQuery('');
  };
  const toggleRole = role => setAllowedRoles(current => current.includes(role)
    ? current.filter(item => item !== role) : [...current, role]);

  const handleAddField = async (fieldData) => {
    const success = editingField ? await updateField(editingField.id, fieldData) : await addField(fieldData);
    if (success) {
      setIsModalOpen(false);
      setEditingField(null);
    }
    return success;
  };

  const handleDeleteField = async (fieldId) => {
    if (confirm('Bạn có chắc muốn xóa field này?')) {
      await deleteField(fieldId);
    }
  };

  if (!step) return null;

  return (
    <div className="h-full w-[540px] min-w-[500px] max-w-[55vw] shrink-0 border-l border-grayBorder bg-white shadow-[-4px_0_15px_-3px_rgba(0,0,0,0.05)] z-10 flex flex-col [&_input]:text-sm [&_select]:text-sm [&_textarea]:text-sm">
      <div className="p-4 border-b border-grayBorder flex items-start justify-between bg-orange-50/30">
        <div className="flex gap-3">
          <div className="w-8 h-8 rounded-full bg-orange-100 text-orange-600 flex items-center justify-center shrink-0 mt-1">
            <Zap size={16} />
          </div>
          <div>
            <h2 className="font-bold text-gray-800 text-sm">Cấu hình: Start Step</h2>
            <p className="text-[10px] text-gray-500 mt-1">Điểm bắt đầu và thu thập thông tin ban đầu của workflow</p>
          </div>
        </div>
        <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors shrink-0">
          <X size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-6">
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-1">
            Hướng dẫn cho người tạo request <span className="text-gray-400 font-normal">(Tuỳ chọn)</span>
          </label>
          <textarea
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            className="input-field py-2 text-sm min-h-[80px]"
            placeholder="VD: Vui lòng đính kèm báo giá trước khi gửi"
          />
        </div>

        <div className="border-t border-grayBorder pt-5">
          <div className="mb-3">
            <label className="block text-sm font-semibold text-gray-700">REQUEST FORM (CUSTOM INPUT FIELDS)</label>
            <p className="text-[10px] text-gray-500 mt-0.5">Đây là dữ liệu gốc của request — mọi bước sau (Review, Approval, Assignment...) đều đọc lại từ đây</p>
          </div>

          <div className="space-y-2">
            {config.fields.map(field => (
              <div key={field.id} className="flex items-center justify-between bg-white border border-grayBorder p-2.5 rounded-lg group hover:border-orange-200">
                <div>
                  <p className="text-sm font-semibold text-gray-800">{field.label}</p>
                  <p className="text-[10px] text-gray-400">{field.fieldKey} | {field.type}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-[10px] px-1.5 py-0.5 rounded ${field.required ? 'bg-orange-100 text-orange-700' : 'bg-gray-100 text-gray-500'}`}>
                    {field.required ? 'Bắt buộc' : 'Tùy chọn'}
                  </span>
                  <button type="button" onClick={()=>{setEditingField(field);setIsModalOpen(true)}} className="flex items-center gap-1 rounded-md border border-orange-200 px-2 py-1 text-[10px] font-semibold text-orange-600 hover:bg-orange-50" title={`Chỉnh sửa ${field.label}`}><Pencil size={11}/>Chỉnh sửa</button>
                  <button onClick={() => handleDeleteField(field.id)} className="text-gray-400 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}

            <button 
              onClick={() => { setEditingField(null); setIsModalOpen(true); }}
              className="w-full py-2.5 border border-dashed border-orange-300 text-orange-500 font-medium text-sm rounded-lg hover:bg-orange-50 transition-colors flex items-center justify-center gap-2"
            >
              + Thêm field
            </button>
          </div>
        </div>

        <div className="border-t border-grayBorder pt-5">
          <label className="block text-sm font-semibold text-gray-700">CHẾ ĐỘ NHẬP DỮ LIỆU</label>
          <div className="mt-3 grid grid-cols-2 gap-2">
            {[['SINGLE', 'Form đơn'], ['BATCH', 'Danh sách CSV']].map(([value, label]) => <button key={value} type="button" onClick={() => setSubmissionMode(value)} className={`rounded-lg border px-3 py-3 text-xs font-semibold ${submissionMode === value ? 'border-orange-500 bg-orange-50 text-orange-700' : 'border-grayBorder text-gray-600'}`}>{label}</button>)}
          </div>
          {submissionMode === 'BATCH' && <div className="mt-3 space-y-3 rounded-lg border border-orange-200 bg-orange-50/20 p-3">
            <label className="block"><span className="mb-1 block text-xs font-semibold text-slate-700">Cột email/user-id của người nhận</span><select value={recordRecipientFieldKey} onChange={event => setRecordRecipientFieldKey(event.target.value)} className="input-field"><option value="">Không gửi động theo từng dòng</option>{config.fields.filter(field => field.type === 'TEXT').map(field => <option key={field.id} value={field.fieldKey}>{field.label} ({field.fieldKey})</option>)}</select><span className="mt-1 block text-[10px] text-gray-500">Notification/End Step có thể dùng cột này để chỉ báo cho các dòng thực sự đi tới bước đó.</span></label>
            <label className="block"><span className="mb-1 block text-xs font-semibold text-slate-700">Số dòng tối đa mỗi lần</span><input type="number" min="1" max="2000" value={maxBatchRows} onChange={event => setMaxBatchRows(Number(event.target.value))} className="input-field" /></label>
          </div>}
        </div>

        <div className="border-t border-grayBorder pt-5 pb-4">
          <div className="mb-3">
            <label className="block text-sm font-semibold text-gray-700">NGƯỜI ĐƯỢC TẠO REQUEST</label>
            <p className="text-[10px] text-gray-500 mt-0.5">Ai có quyền bấm "Tạo Request" cho workflow này</p>
          </div>

          <div className="space-y-2">
            <label className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${requesterScope === 'ALL_EMPLOYEES' ? 'border-orange-500 bg-orange-50/30' : 'border-grayBorder hover:bg-gray-50'}`}>
              <input 
                type="radio" 
                name="scope" 
                value="ALL_EMPLOYEES" 
                checked={requesterScope === 'ALL_EMPLOYEES'}
                onChange={(e) => setRequesterScope(e.target.value)}
                className="mt-1 text-orange-500 focus:ring-orange-500"
              />
              <div>
                <p className="text-sm font-semibold text-gray-800">Tất cả nhân viên</p>
                <p className="text-[10px] text-gray-500">Mọi nhân viên trong tổ chức đều có thể khởi tạo request mới</p>
              </div>
            </label>

            <label className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${requesterScope === 'SPECIFIC_GROUP_ROLE' ? 'border-orange-500 bg-orange-50/30' : 'border-grayBorder hover:bg-gray-50'}`}>
              <input 
                type="radio" 
                name="scope" 
                value="SPECIFIC_GROUP_ROLE" 
                checked={requesterScope === 'SPECIFIC_GROUP_ROLE'}
                onChange={(e) => setRequesterScope(e.target.value)}
                className="mt-1 text-orange-500 focus:ring-orange-500"
              />
              <div>
                <p className="text-sm font-semibold text-gray-800">Chỉ user/nhóm/vai trò cụ thể</p>
                <p className="text-[10px] text-gray-500">Giới hạn quyền tạo request cho các user, nhóm hoặc vai trò được chỉ định</p>
              </div>
            </label>

            {requesterScope === 'SPECIFIC_GROUP_ROLE' && <div className="space-y-4 rounded-lg border border-orange-200 bg-orange-50/20 p-4">
              <div>
                <p className="mb-2 text-xs font-semibold text-slate-700">User được phép</p>
                {!!selectedUsers.length && <div className="mb-2 flex flex-wrap gap-1.5">
                  {selectedUsers.map(user => <span key={user.id} className="flex items-center gap-1 rounded-full bg-blue-100 px-2.5 py-1 text-[10px] font-medium text-blue-700">
                    {user.displayName}<button type="button" onClick={() => setAllowedUserIds(current => current.filter(id => id !== user.id))}><X size={11}/></button>
                  </span>)}
                </div>}
                <SearchDropdown
                  value={userQuery}
                  onInput={setUserQuery}
                  onSelect={addUser}
                  items={users.filter(user => user.active !== false && !allowedUserIds.includes(user.id)).map(user => ({
                    id: user.id, value: user.email, label: user.displayName,
                    description: `${user.email} · ${user.jobTitle || 'Chưa có chức danh'}`,
                  }))}
                  placeholder="Tìm theo tên hoặc email rồi chọn user..."
                />
              </div>

              <div>
                <p className="mb-2 text-xs font-semibold text-slate-700">Nhóm được phép</p>
                {!!selectedGroups.length && <div className="mb-2 flex flex-wrap gap-1.5">
                  {selectedGroups.map(group => <span key={group.id} className="flex items-center gap-1 rounded-full bg-orange-100 px-2.5 py-1 text-[10px] font-medium text-orange-700">
                    {group.name}<button type="button" onClick={() => setAllowedGroupIds(current => current.filter(id => id !== group.id))}><X size={11}/></button>
                  </span>)}
                </div>}
                <SearchDropdown
                  value={groupQuery}
                  onInput={setGroupQuery}
                  onSelect={addGroup}
                  items={groups.filter(group => !allowedGroupIds.includes(group.id)).map(group => ({
                    id: group.id, value: group.id, label: group.name,
                    description: `${group.memberCount || 0} thành viên`,
                  }))}
                  placeholder="Tìm nhóm được phép tạo request..."
                />
              </div>

              <div>
                <p className="mb-2 text-xs font-semibold text-slate-700">Vai trò hệ thống được phép</p>
                <div className="grid grid-cols-2 gap-2">
                  {SYSTEM_ROLES.map(([role, label]) => <label key={role} className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2.5 text-xs ${allowedRoles.includes(role) ? 'border-orange-400 bg-orange-50 text-orange-700' : 'border-grayBorder bg-white text-gray-600'}`}>
                    <input type="checkbox" checked={allowedRoles.includes(role)} onChange={() => toggleRole(role)} className="accent-orange-500"/>
                    <span>{label}</span>
                  </label>)}
                </div>
              </div>
              <p className="text-[10px] leading-4 text-gray-500">Người dùng chỉ cần được chọn trực tiếp, thuộc một nhóm hoặc có một vai trò đã chọn.</p>
            </div>}
          </div>
        </div>

        <div className="border-t border-grayBorder pt-5 pb-4">
          <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-grayBorder p-3 hover:bg-gray-50">
            <input type="checkbox" checked={allowRequesterWithdrawal} onChange={event => setAllowRequesterWithdrawal(event.target.checked)} className="mt-1 h-4 w-4 accent-orange-500" />
            <span><span className="block text-sm font-semibold text-gray-800">Cho phép người gửi thu hồi request</span><span className="mt-1 block text-[10px] leading-4 text-gray-500">Chỉ áp dụng khi request còn đang chạy. Task chờ xử lý sẽ bị hủy và lịch sử vẫn được giữ lại.</span></span>
          </label>
        </div>
        {(message || configError) && <p className={`rounded-lg px-3 py-2 text-xs ${(message || '').startsWith('Đã') ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>{message || configError}</p>}
      </div>

      <div className="p-4 border-t border-grayBorder bg-gray-50 flex gap-3">
        <button onClick={onClose} className="flex-1 bg-white border border-grayBorder text-gray-700 py-2 rounded-lg font-medium text-sm hover:bg-gray-50 transition-colors">
          Hủy
        </button>
        <button onClick={handleSaveConfig} disabled={saving} className="flex-1 btn-primary py-2 text-sm disabled:opacity-60">
          {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
        </button>
      </div>

      <AddFieldModal
        isOpen={isModalOpen}
        onClose={() => { setIsModalOpen(false); setEditingField(null); }}
        onSave={handleAddField}
        editingField={editingField}
      />
    </div>
  );
}
