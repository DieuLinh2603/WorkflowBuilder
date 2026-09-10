import { useEffect, useState } from 'react';
import { AlertCircle, Bell, CheckCircle2, Link2, Mail, MessageSquare, X } from 'lucide-react';
import { apiFetch } from '../../api';
import { CollapsibleNote } from '../shared/UXHelpers';
import { PanelFrame, SearchDropdown, SectionLabel, Toggle } from './StepPanelShared';

const DEFAULTS = {
  titleTemplate: 'Thông báo workflow',
  bodyTemplate: 'Yêu cầu {{requestCode}} của workflow {{workflowName}} đã chuyển tới bước thông báo.',
  recipientUserIds: [], recipientGroupIds: [], recipientRoles: [],
  channels: ['IN_APP'], triggers: ['STEP_ACTIVATED'], webhookUrl: '',
  includeRequester: true, includePreviousActor: false, includeNextStepActors: false, includeRecordRecipient: false,
};
const CHANNELS = [
  ['EMAIL', 'Email Notification', Mail], ['IN_APP', 'In-app Notification', Bell],
  ['TEAMS', 'Teams Notification', MessageSquare], ['WEBHOOK', 'Webhook Notification', Link2],
];
const TRIGGERS = [
  ['STEP_ACTIVATED', 'Khi bước được kích hoạt'], ['APPROVED', 'Khi được duyệt'],
  ['REJECTED', 'Khi bị từ chối'], ['COMPLETED', 'Khi hoàn tất'],
];
const CONTENT_TEMPLATES = [
  { key: 'UPDATE', label: 'Cập nhật chung', description: 'Thông báo yêu cầu vừa chuyển bước', triggers: ['STEP_ACTIVATED'], titleTemplate: 'Cập nhật yêu cầu {{requestCode}}', bodyTemplate: 'Yêu cầu {{requestCode}} trong workflow {{workflowName}} vừa được cập nhật. Vui lòng mở hệ thống để xem chi tiết.' },
  { key: 'ACTION', label: 'Cần xử lý', description: 'Nhắc người nhận mở và xử lý yêu cầu', triggers: ['STEP_ACTIVATED'], titleTemplate: 'Yêu cầu {{requestCode}} cần bạn xử lý', bodyTemplate: 'Bạn có một yêu cầu mới trong workflow {{workflowName}}. Vui lòng mở yêu cầu {{requestCode}} để kiểm tra và thực hiện bước tiếp theo.' },
  { key: 'APPROVED', label: 'Đã được duyệt', description: 'Thông báo kết quả phê duyệt', triggers: ['APPROVED'], titleTemplate: 'Yêu cầu {{requestCode}} đã được duyệt', bodyTemplate: 'Yêu cầu {{requestCode}} trong workflow {{workflowName}} đã được phê duyệt.' },
  { key: 'REJECTED', label: 'Bị từ chối', description: 'Thông báo kết quả từ chối', triggers: ['REJECTED'], titleTemplate: 'Yêu cầu {{requestCode}} đã bị từ chối', bodyTemplate: 'Yêu cầu {{requestCode}} trong workflow {{workflowName}} đã bị từ chối. Vui lòng mở hệ thống để xem chi tiết.' },
];
const TEMPLATE_VARIABLES = [['{{requestCode}}', 'Mã yêu cầu'], ['{{workflowName}}', 'Tên workflow']];

export default function NotificationStepPanel({ workflowId, workflowName, step, onClose, onDelete, onConfigureStart }) {
  const [config, setConfig] = useState(DEFAULTS);
  const [startConfig, setStartConfig] = useState(null);
  const [contentTemplate, setContentTemplate] = useState('CUSTOM');
  const [users, setUsers] = useState([]), [groups, setGroups] = useState([]);
  const [userQuery, setUserQuery] = useState(''), [groupQuery, setGroupQuery] = useState('');
  const [message, setMessage] = useState(''), [saving, setSaving] = useState(false);

  useEffect(() => {
    Promise.all([
      apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config`).then(json),
      apiFetch('/api/users/active').then(json), apiFetch('/api/groups').then(json),
      apiFetch(`/api/workflows/${workflowId}/steps`).then(json),
    ]).then(async ([saved, activeUsers, activeGroups, steps]) => {
      const startStep = steps.find(item => item.type === 'START');
      const loadedStartConfig = startStep
        ? await apiFetch(`/api/workflows/${workflowId}/steps/${startStep.id}/start-config`).then(json)
        : null;
      const loadedConfig = { ...DEFAULTS, ...saved,
        channels: saved.channels?.length ? saved.channels : DEFAULTS.channels,
        triggers: saved.triggers?.length ? saved.triggers : DEFAULTS.triggers };
      setConfig(loadedConfig);
      setContentTemplate(CONTENT_TEMPLATES.find(template => template.titleTemplate === loadedConfig.titleTemplate && template.bodyTemplate === loadedConfig.bodyTemplate)?.key || 'CUSTOM');
      setStartConfig(loadedStartConfig);
      setUsers(Array.isArray(activeUsers) ? activeUsers : []);
      setGroups(Array.isArray(activeGroups) ? activeGroups : []);
    }).catch(e => setMessage(e.message));
  }, [workflowId, step.id]);

  const toggle = (field, value) => setConfig(current => ({ ...current,
    [field]: (current[field] || []).includes(value)
      ? current[field].filter(item => item !== value) : [...(current[field] || []), value] }));
  const save = async () => {
    if (!config.channels?.length) return setMessage('Hãy chọn ít nhất một kênh thông báo.');
    if (!config.triggers?.length) return setMessage('Hãy chọn ít nhất một thời điểm gửi.');
    if (!config.titleTemplate?.trim()) return setMessage('Hãy nhập tiêu đề thông báo.');
    if (!config.bodyTemplate?.trim()) return setMessage('Hãy nhập nội dung thông báo.');
    if (config.channels.includes('WEBHOOK') && !config.webhookUrl?.trim()) return setMessage('Hãy nhập URL webhook đích.');
    if (config.includeRecordRecipient && startConfig?.submissionMode !== 'BATCH') return setMessage('Người nhận theo dòng chỉ dùng được khi START Step ở chế độ Danh sách CSV.');
    if (config.includeRecordRecipient && !startConfig?.recordRecipientFieldKey) return setMessage('Hãy chọn cột email/user-id tại START Step trước khi bật người nhận theo dòng CSV.');
    setSaving(true); setMessage('');
    try {
      const response = await apiFetch(`/api/workflows/${workflowId}/steps/${step.id}/config/notification`, { method: 'PUT', body: JSON.stringify(config) });
      setMessage(response.ok ? 'Đã lưu cấu hình Notification Step.' : await error(response));
    } catch (e) { setMessage(e.message || 'Không thể lưu cấu hình'); }
    finally { setSaving(false); }
  };
  const selectedUsers = users.filter(user => config.recipientUserIds?.includes(user.id));
  const selectedGroups = groups.filter(group => config.recipientGroupIds?.includes(group.id));
  const addUser = item => { setConfig(c => ({ ...c, recipientUserIds: [...(c.recipientUserIds || []), item.id] })); setUserQuery(''); };
  const addGroup = item => { setConfig(c => ({ ...c, recipientGroupIds: [...(c.recipientGroupIds || []), item.id] })); setGroupQuery(''); };
  const insertToken = (field, token) => setConfig(current => ({ ...current, [field]: `${current[field] || ''}${current[field]?.endsWith(' ') || !current[field] ? '' : ' '}${token}` }));
  const applyContentTemplate = template => {
    setContentTemplate(template.key);
    setConfig(current => ({ ...current, titleTemplate: template.titleTemplate, bodyTemplate: template.bodyTemplate, triggers: template.triggers }));
  };
  const recipientField = startConfig?.fields?.find(field => field.fieldKey === startConfig.recordRecipientFieldKey);
  const recipientReady = startConfig?.submissionMode === 'BATCH' && !!recipientField;

  return <PanelFrame wide icon={Bell} title="Cấu hình: Notification Step" subtitle="Gửi thông báo tự động" onClose={onClose} onDelete={onDelete}
    footer={<div className="flex items-center justify-end gap-3"><button type="button" onClick={onClose} className="rounded-lg border border-grayBorder px-5 py-2 text-xs">Hủy</button><button type="button" onClick={save} disabled={saving} className="btn-primary px-5 py-2 text-xs disabled:opacity-60">{saving ? 'Đang lưu...' : 'Lưu cấu hình'}</button></div>}>
    <div className="space-y-6">
      <section><SectionLabel>Kênh gửi thông báo</SectionLabel><div className="grid grid-cols-4 gap-3">
        {CHANNELS.map(([value, label, icon]) => <Channel key={value} value={value} label={label} icon={icon} active={config.channels?.includes(value)} onClick={() => toggle('channels', value)} />)}
      </div>{config.channels?.includes('WEBHOOK') && <div className="mt-3"><label className="mb-1.5 block text-[11px] font-semibold text-slate-600">URL webhook đích <span className="text-orange-500">*</span></label><input value={config.webhookUrl || ''} onChange={e => setConfig(c => ({ ...c, webhookUrl: e.target.value }))} className="input-field text-xs" placeholder="https://api.example.com/webhook" /></div>}
      <CollapsibleNote summary="Yêu cầu cấu hình kỹ thuật theo từng kênh" className="mt-3">
        Email cần SMTP đã cấu hình; Teams dùng endpoint do Admin quản trị trong Cài đặt; Webhook dùng URL nhập trực tiếp ở trên.
      </CollapsibleNote></section>

      <section><SectionLabel>Gửi khi nào (Trigger)</SectionLabel><div className="flex flex-wrap gap-5 text-xs">
        {TRIGGERS.map(([value, label]) => <Check key={value} label={label} checked={config.triggers?.includes(value)} onChange={() => toggle('triggers', value)} />)}
      </div><p className="mt-2 text-[10px] text-gray-400">Sự kiện là kết quả của bước ngay trước khi luồng đi vào Notification Step.</p></section>

      <section><SectionLabel>Người nhận</SectionLabel><div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg border border-grayBorder px-3 py-3"><Toggle checked={!!config.includeRequester} onChange={v => setConfig(c => ({ ...c, includeRequester: v }))} label="Người tạo request" /></div>
        <div className="rounded-lg border border-grayBorder px-3 py-3"><Toggle checked={!!config.includePreviousActor} onChange={v => setConfig(c => ({ ...c, includePreviousActor: v }))} label="Actor bước trước" /></div>
        <div className="rounded-lg border border-grayBorder px-3 py-3"><Toggle checked={!!config.includeNextStepActors} onChange={v => setConfig(c => ({ ...c, includeNextStepActors: v }))} label="Actor bước kế tiếp" /></div>
        <div className={`rounded-lg border px-3 py-3 ${config.includeRecordRecipient ? 'border-orange-300 bg-orange-50/30' : 'border-grayBorder'}`}><Toggle checked={!!config.includeRecordRecipient} onChange={v => setConfig(c => ({ ...c, includeRecordRecipient: v }))} label="Người nhận theo dòng CSV" /></div>
      </div>
      {config.includeRecordRecipient && <div className={`mt-3 flex items-start gap-3 rounded-xl border px-4 py-3 ${recipientReady ? 'border-emerald-200 bg-emerald-50/60' : 'border-amber-200 bg-amber-50'}`}>
        {recipientReady ? <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-emerald-600"/> : <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600"/>}
        <div className="min-w-0 flex-1">
          {recipientReady ? <><p className="text-xs font-bold text-emerald-800">Cột nhận diện người nhận: {recipientField.label} ({recipientField.fieldKey})</p><p className="mt-1 text-[11px] leading-5 text-emerald-700">Mỗi dòng phải chứa email hoặc user ID của một tài khoản đang hoạt động. Với Data Pipeline, hãy mapping cột email vào field này.</p></> : <><p className="text-xs font-bold text-amber-800">Chưa xác định được cột email/user ID</p><p className="mt-1 text-[11px] leading-5 text-amber-700">Mở START Step, chọn chế độ “Danh sách CSV”, sau đó chọn một field TEXT tại mục “Cột email/user-id của người nhận”.</p>{onConfigureStart && <button type="button" onClick={onConfigureStart} className="mt-2 rounded-lg border border-amber-300 bg-white px-3 py-1.5 text-[11px] font-bold text-amber-700 hover:bg-amber-100">Cấu hình START Step</button>}</>}
        </div>
      </div>}
      <div className="mt-4 grid grid-cols-2 gap-4">
        <RecipientBox label="User cụ thể" selected={selectedUsers} remove={id => setConfig(c => ({ ...c, recipientUserIds: c.recipientUserIds.filter(x => x !== id) }))}><SearchDropdown value={userQuery} onInput={setUserQuery} onSelect={addUser} items={users.filter(u => !config.recipientUserIds.includes(u.id)).map(u => ({ id: u.id, value: u.id, label: u.displayName, description: u.email }))} placeholder="Tìm user..." /></RecipientBox>
        <RecipientBox label="Group" selected={selectedGroups.map(g => ({ ...g, displayName: g.name }))} remove={id => setConfig(c => ({ ...c, recipientGroupIds: c.recipientGroupIds.filter(x => x !== id) }))}><SearchDropdown value={groupQuery} onInput={setGroupQuery} onSelect={addGroup} items={groups.filter(g => !config.recipientGroupIds.includes(g.id)).map(g => ({ id: g.id, value: g.id, label: g.name, description: `${g.memberCount || 0} thành viên` }))} placeholder="Tìm group..." /></RecipientBox>
      </div><div className="mt-4"><p className="mb-2 text-[11px] font-semibold text-slate-600">System role</p><div className="flex flex-wrap gap-4">{['ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER'].map(role => <label key={role} className="flex items-center gap-2 text-[11px] text-gray-600"><input type="checkbox" checked={(config.recipientRoles || []).includes(role)} onChange={e => setConfig(c => ({ ...c, recipientRoles: e.target.checked ? [...(c.recipientRoles || []), role] : (c.recipientRoles || []).filter(x => x !== role) }))} className="accent-orange-500" />{role}</label>)}</div></div></section>

      <section>
        <SectionLabel>Nội dung thông báo</SectionLabel>
        <div className="rounded-xl border border-grayBorder p-4">
          <div><p className="text-sm font-bold text-slate-700">1. Chọn mẫu phù hợp</p><p className="mt-1 text-[11px] text-gray-500">Mẫu sẽ điền sẵn tiêu đề, nội dung và thời điểm gửi. Bạn vẫn có thể sửa lại bên dưới.</p></div>
          <div className="mt-3 grid grid-cols-2 gap-2 lg:grid-cols-4">{CONTENT_TEMPLATES.map(template => <button type="button" key={template.key} onClick={() => applyContentTemplate(template)} className={`rounded-lg border p-3 text-left transition ${contentTemplate === template.key ? 'border-orange-400 bg-orange-50 ring-1 ring-orange-100' : 'border-slate-200 hover:border-orange-200'}`}><span className="block text-xs font-bold text-slate-700">{template.label}</span><span className="mt-1 block text-[10px] leading-4 text-gray-400">{template.description}</span></button>)}</div>

          <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,3fr)_minmax(280px,2fr)]">
            <div className="space-y-4">
              <div>
                <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2"><label className="text-xs font-bold text-slate-700">2. Tiêu đề</label><VariableButtons onInsert={token => insertToken('titleTemplate', token)}/></div>
                <input maxLength={160} value={config.titleTemplate || ''} onChange={event => { setContentTemplate('CUSTOM'); setConfig(current => ({ ...current, titleTemplate: event.target.value })); }} className="input-field text-sm" placeholder="Ví dụ: Yêu cầu mới cần bạn xử lý"/>
                <p className="mt-1 text-right text-[10px] text-gray-400">{config.titleTemplate?.length || 0}/160</p>
              </div>
              <div>
                <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2"><label className="text-xs font-bold text-slate-700">3. Nội dung chi tiết</label><VariableButtons onInsert={token => insertToken('bodyTemplate', token)}/></div>
                <textarea maxLength={1000} value={config.bodyTemplate || ''} onChange={event => { setContentTemplate('CUSTOM'); setConfig(current => ({ ...current, bodyTemplate: event.target.value })); }} className="min-h-[150px] w-full resize-y rounded-lg border border-grayBorder p-3 text-sm leading-6 outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100" placeholder="Viết thông tin người nhận cần biết hoặc hành động cần thực hiện..."/>
                <div className="mt-1 flex justify-between gap-3 text-[10px] text-gray-400"><span>Bấm biến màu cam để chèn dữ liệu tự động.</span><span>{config.bodyTemplate?.length || 0}/1000</span></div>
              </div>
            </div>

            <div>
              <p className="mb-1.5 text-xs font-bold text-slate-700">Xem trước</p>
              <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-50 shadow-sm">
                <div className="flex items-center gap-2 border-b border-slate-200 bg-white px-4 py-3 text-[11px] font-semibold text-slate-500"><Bell size={15} className="text-orange-500"/>Thông báo trong hệ thống</div>
                <div className="p-4"><p className="break-words text-sm font-bold text-slate-800">{preview(config.titleTemplate, workflowName) || 'Tiêu đề thông báo'}</p><p className="mt-2 whitespace-pre-wrap break-words text-xs leading-5 text-slate-600">{preview(config.bodyTemplate, workflowName) || 'Nội dung thông báo sẽ hiển thị tại đây.'}</p><div className="mt-4 border-t border-slate-200 pt-3 text-[10px] text-slate-400">Vừa xong · Nhấn để xem yêu cầu</div></div>
              </div>
            </div>
          </div>
        </div>
      </section>
      {message && <p className={`rounded-lg px-3 py-2 text-xs ${message.startsWith('Đã') ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>{message}</p>}
    </div>
  </PanelFrame>;
}

function Channel({ icon: Icon, label, active, onClick }) { return <button type="button" onClick={onClick} className={`flex items-center justify-between rounded-lg border px-4 py-3 text-left transition ${active ? 'border-orange-400 bg-orange-50/40' : 'border-grayBorder bg-gray-50 hover:border-orange-200'}`}><span className="flex items-center gap-2 text-xs font-semibold"><Icon size={16} className={active ? 'text-orange-500' : 'text-gray-400'} />{label}</span><span className={`relative h-5 w-9 rounded-full ${active ? 'bg-orange-500' : 'bg-gray-300'}`}><span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white ${active ? 'right-0.5' : 'left-0.5'}`} /></span></button>; }
function Check({ label, checked, onChange }) { return <label className={`flex cursor-pointer items-center gap-2 ${checked ? 'text-slate-700' : 'text-gray-500'}`}><input type="checkbox" checked={!!checked} onChange={onChange} className="accent-orange-500" />{label}</label>; }
function RecipientBox({ label, selected, remove, children }) { return <div><p className="mb-2 text-[11px] font-semibold text-slate-600">{label}</p><div className="mb-2 flex flex-wrap gap-1">{selected.map(item => <span key={item.id} className="flex items-center gap-1 rounded-full bg-orange-50 px-2 py-1 text-[10px] text-orange-700">{item.displayName}<button type="button" onClick={() => remove(item.id)}><X size={10} /></button></span>)}</div>{children}</div>; }
function VariableButtons({ onInsert }) { return <div className="flex flex-wrap gap-1.5">{TEMPLATE_VARIABLES.map(([token, label]) => <button type="button" key={token} onClick={() => onInsert(token)} className="rounded-full border border-orange-200 bg-orange-50 px-2.5 py-1 text-[10px] font-semibold text-orange-600 hover:bg-orange-100">+ {label}</button>)}</div>; }
function preview(value, workflowName) { return String(value || '').replaceAll('{{requestCode}}', 'REQ-2026-001').replaceAll('{{workflowName}}', workflowName || 'Workflow mẫu'); }
async function json(response) { if (!response.ok) throw new Error(await error(response)); return response.json(); }
async function error(response) { const body = await response.json().catch(() => null); return body?.message || 'Không thể lưu cấu hình'; }
