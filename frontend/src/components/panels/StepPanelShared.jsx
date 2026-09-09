import { useEffect, useMemo, useRef, useState } from 'react';
import { CalendarDays, ChevronDown, Trash2, UserRound, X } from 'lucide-react';

export function PanelFrame({ icon: Icon, title, subtitle, onClose, onDelete, children, footer, wide = false }) {
  return <aside className={`${wide ? 'fixed inset-x-5 top-4 bottom-4 rounded-xl border shadow-2xl' : 'h-full w-[560px] min-w-[500px] max-w-[55vw] border-l shadow-[-4px_0_18px_-8px_rgba(15,23,42,0.18)]'} z-40 flex shrink-0 flex-col bg-white border-grayBorder`}>
    <header className="flex items-center justify-between border-b border-grayBorder px-6 py-5">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-orange-50 text-orange-500"><Icon size={19} /></div>
        <div><h2 className="text-base font-bold text-slate-800">{title}</h2>{subtitle && <p className="mt-1 text-xs text-gray-400">{subtitle}</p>}</div>
      </div>
      <div className="flex items-center gap-1">{onDelete && <button type="button" onClick={onDelete} className="rounded-full p-2 text-gray-400 hover:bg-red-50 hover:text-red-500" title="Xóa step"><Trash2 size={17} /></button>}<button type="button" onClick={onClose} className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700"><X size={17} /></button></div>
    </header>
    <div className="flex-1 overflow-y-auto px-6 py-5 [&_input]:text-sm [&_select]:text-sm [&_textarea]:text-sm">{children}</div>
    {footer && <footer className="border-t border-grayBorder bg-white px-6 py-5">{footer}</footer>}
  </aside>;
}

export function SectionLabel({ children }) { return <h3 className="mb-2.5 text-[11px] font-bold uppercase tracking-wide text-slate-500">{children}</h3>; }

export function SearchDropdown({ value, onInput, onSelect, items, placeholder }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const query = (value || '').trim().toLocaleLowerCase('vi');
  const filtered = items.filter((item) => `${item.label} ${item.value} ${item.description || ''}`.toLocaleLowerCase('vi').includes(query));
  useEffect(() => {
    const close = (event) => { if (!rootRef.current?.contains(event.target)) setOpen(false); };
    document.addEventListener('mousedown', close); return () => document.removeEventListener('mousedown', close);
  }, []);
  return <div ref={rootRef} className="relative">
    <UserRound size={15} className="pointer-events-none absolute left-3 top-[18px] z-10 -translate-y-1/2 text-orange-500" />
    <input value={value || ''} onFocus={() => setOpen(true)} onClick={() => setOpen(true)} onChange={(e) => { onInput(e.target.value); setOpen(true); }} placeholder={placeholder} autoComplete="off" className="w-full rounded-lg border border-grayBorder bg-white py-3 pl-9 pr-8 text-sm outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100" />
    <button type="button" onClick={() => setOpen(v => !v)} className="absolute right-2 top-[18px] -translate-y-1/2 text-gray-400"><ChevronDown size={14} /></button>
    {open && <div className="absolute z-50 mt-1 max-h-52 w-full overflow-y-auto rounded-lg border border-grayBorder bg-white py-1 shadow-xl">
      {filtered.length ? filtered.map(item => <button key={item.id} type="button" onMouseDown={e => e.preventDefault()} onClick={() => { onSelect(item); setOpen(false); }} className="block w-full border-b border-slate-50 px-3 py-2.5 text-left hover:bg-orange-50">
        <span className="block text-xs font-semibold text-slate-700">{item.label}</span><span className="block text-[10px] text-gray-400">{item.description}</span>
      </button>) : <p className="px-3 py-4 text-center text-xs text-gray-400">Không tìm thấy dữ liệu phù hợp</p>}
    </div>}
  </div>;
}

export function ActorSelector({ config, setConfig, users, label = 'Người xử lý' }) {
  const [userQuery, setUserQuery] = useState('');
  const modes = [['FIXED_USER','Fixed User'],['ROLE_BASED','Role Based'],['DYNAMIC','Dynamic']];
  const selected = users.filter(u => config.actorUserIds?.includes(u.id));
  const roles = useMemo(() => [...new Set(users.map(u => u.jobTitle?.trim()).filter(Boolean))].sort((a,b) => a.localeCompare(b,'vi')), [users]);
  const userItems = users.filter(u => !config.actorUserIds?.includes(u.id)).map(u => ({ id:u.id,value:u.email,label:u.displayName,description:`${u.email} · ${u.jobTitle || 'Chưa có chức danh'}` }));
  const addUser = item => { setConfig(c => ({...c,fixedUserEmail:'',actorUserIds:[...(c.actorUserIds || []),item.id]})); setUserQuery(''); };
  const removeUser = id => setConfig(c => ({...c,fixedUserEmail:'',actorUserIds:(c.actorUserIds || []).filter(value => value !== id)}));
  return <section>
    <SectionLabel>{label}</SectionLabel>
    <div className="grid grid-cols-3 gap-1 rounded-lg bg-gray-100 p-1">{modes.map(([value,text]) => <button key={value} type="button" onClick={() => setConfig(c => ({...c,approverMode:value}))} className={`rounded-md px-2 py-2 text-[11px] font-medium ${config.approverMode===value?'bg-white text-orange-600 shadow-sm':'text-gray-500'}`}>{text}</button>)}</div>
    <div className="mt-3">
      {config.approverMode==='FIXED_USER' && <><div className="mb-2 flex flex-wrap gap-1.5">{selected.map(user => <span key={user.id} className="flex items-center gap-1 rounded-full bg-orange-50 px-2.5 py-1 text-[10px] font-medium text-orange-700">{user.displayName}<button type="button" onClick={() => removeUser(user.id)} aria-label={`Bỏ ${user.displayName}`}><X size={11}/></button></span>)}</div><SearchDropdown value={userQuery} onInput={setUserQuery} onSelect={addUser} items={userItems} placeholder="Tìm tên hoặc email rồi chọn nhiều người..." /><p className="mt-2 text-[10px] text-gray-400">Đã chọn {selected.length} người. Mỗi người sẽ nhận một task riêng.</p></>}
      {config.approverMode==='ROLE_BASED' && <><SearchDropdown value={config.actorRole || ''} onInput={value => setConfig(c => ({...c,actorRole:value}))} onSelect={item => setConfig(c => ({...c,actorRole:item.value}))} items={roles.map(r => ({id:r,value:r,label:r,description:`${users.filter(u=>u.jobTitle===r).length} tài khoản active`}))} placeholder="Tìm chức danh trong công ty..." /><p className="mt-2 text-[10px] leading-4 text-gray-400">Dữ liệu lấy từ Chức danh (job title) của user.</p></>}
      {config.approverMode==='DYNAMIC' && <select value={config.dynamicActorSource || 'REQUEST_CREATOR_MANAGER'} onChange={e => setConfig(c => ({...c,dynamicActorSource:e.target.value}))} className="input-field bg-white text-xs"><option value="REQUEST_CREATOR_MANAGER">Quản lý trực tiếp của người tạo request</option><option value="PREVIOUS_ACTOR_MANAGER">Quản lý của người xử lý bước trước</option><option value="REQUEST_CREATOR">Người tạo request</option></select>}
    </div>
  </section>;
}

export function DeadlinePicker({ config, setConfig }) {
  const preset = config.deadlineDate ? 'CUSTOM' : ['',24,48,72,168].includes(config.deadlineHours ?? '') ? String(config.deadlineHours ?? '') : 'CUSTOM';
  return <section><SectionLabel>Deadline</SectionLabel>
    <div className="relative"><CalendarDays size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-orange-500" /><select value={preset} onChange={e => {const value=e.target.value;setConfig(c => value==='CUSTOM'?{...c,deadlineHours:null,deadlineDate:c.deadlineDate||localDateAfter(4)}:{...c,deadlineHours:value?Number(value):null,deadlineDate:null});}} className="w-full appearance-none rounded-lg border border-grayBorder bg-white py-2.5 pl-9 pr-8 text-xs outline-none focus:border-orange-400"><option value="">Không đặt deadline</option><option value="24">1 ngày</option><option value="48">2 ngày</option><option value="72">3 ngày</option><option value="168">7 ngày</option><option value="CUSTOM">Chọn ngày trên lịch...</option></select></div>
    {preset==='CUSTOM' && <input type="date" min={localDateAfter(0)} value={config.deadlineDate || ''} onChange={e => setConfig(c => ({...c,deadlineDate:e.target.value||null}))} className="mt-2 w-full rounded-lg border border-grayBorder px-3 py-2.5 text-xs outline-none focus:border-orange-400" />}
  </section>;
}

export function CompletionPolicy({ config, setConfig, label = 'Điều kiện chuyển bước' }) {
  const mode = config.completionMode || 'ANY';
  const select = completionMode => setConfig(current => ({ ...current, completionMode }));
  return <section><SectionLabel>{label}</SectionLabel><div className="space-y-2">
    <PolicyChoice active={mode === 'ANY'} onClick={() => select('ANY')} title="Một người hoàn thành" text="Qua bước tiếp theo ngay khi có người xử lý đầu tiên." />
    <PolicyChoice active={mode === 'ALL'} onClick={() => select('ALL')} title="Tất cả hoàn thành" text="Chờ toàn bộ người được giao xử lý xong." />
    <PolicyChoice active={mode === 'PERCENTAGE'} onClick={() => select('PERCENTAGE')} title="Theo tỷ lệ hoàn thành" text="Qua bước khi đạt tỷ lệ số người đã xử lý." />
  </div>{mode === 'PERCENTAGE' && <label className="mt-3 block rounded-lg border border-orange-200 bg-orange-50/40 p-3">
    <span className="mb-2 flex items-center justify-between text-xs font-semibold text-slate-700"><span>Tỷ lệ yêu cầu</span><b className="text-orange-600">{config.completionPercentage || 50}%</b></span>
    <input type="range" min="1" max="100" value={config.completionPercentage || 50} onChange={event => setConfig(current => ({ ...current, completionPercentage: Number(event.target.value) }))} className="w-full accent-orange-500" />
    <input type="number" min="1" max="100" value={config.completionPercentage || 50} onChange={event => setConfig(current => ({ ...current, completionPercentage: Math.max(1, Math.min(100, Number(event.target.value) || 1)) }))} className="input-field mt-2 bg-white" />
    <span className="mt-1 block text-[10px] leading-4 text-gray-500">Số người cần hoàn thành được làm tròn lên. Ví dụ 50% của 3 người là 2 người.</span>
  </label>}</section>;
}

function PolicyChoice({ active, onClick, title, text }) {
  return <button type="button" onClick={onClick} className={`w-full rounded-lg border p-3 text-left ${active ? 'border-orange-500 bg-orange-50/40' : 'border-grayBorder bg-white'}`}><span className="flex items-start gap-2"><span className={`mt-0.5 h-3 w-3 shrink-0 rounded-full border ${active ? 'border-4 border-orange-500' : 'border-gray-300'}`} /><span><b className="block text-xs text-slate-700">{title}</b><small className="mt-1 block text-[10px] leading-4 text-gray-400">{text}</small></span></span></button>;
}

export function Toggle({ checked, onChange, label }) { return <label className="inline-flex w-fit cursor-pointer items-center gap-2.5 text-sm font-medium text-slate-700"><input type="checkbox" checked={checked} onChange={e=>onChange(e.target.checked)} className="peer sr-only"/><span className="relative h-6 w-11 shrink-0 rounded-full bg-gray-200 transition-colors peer-checked:bg-orange-500 after:absolute after:left-0.5 after:top-0.5 after:h-5 after:w-5 after:rounded-full after:bg-white after:shadow-sm after:transition-transform peer-checked:after:translate-x-5"/><span>{label}</span></label>; }

export function localDateAfter(days){const d=new Date();d.setDate(d.getDate()+days);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
