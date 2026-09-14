import { useEffect, useState } from 'react';
import { X } from 'lucide-react';

const EMPTY_FIELD = { label: '', fieldKey: '', type: 'TEXT', required: false, placeholder: '', options: [], allowMultiple: false };

export default function AddFieldModal({ isOpen, onClose, onSave, editingField }) {
  const [formData, setFormData] = useState(EMPTY_FIELD);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [optionMode, setOptionMode] = useState('MANUAL');
  const [bulkOptions, setBulkOptions] = useState('');

  useEffect(() => {
    if (isOpen) {
      setError('');
      setOptionMode('MANUAL');
      setFormData(editingField
        ? { ...EMPTY_FIELD, ...editingField, options: Array.isArray(editingField.options) ? editingField.options : [] }
        : { ...EMPTY_FIELD });
      setBulkOptions((editingField?.options || []).map(option => `${option.label} | ${option.value}`).join('\n'));
    }
  }, [isOpen, editingField]);

  if (!isOpen) return null;

  const change = (event) => {
    const { name, value, type, checked } = event.target;
    setFormData((current) => {
      const next = { ...current, [name]: type === 'checkbox' ? checked : value };
      if (name === 'type' && ['SELECT', 'MULTI_CHOICE', 'RADIO'].includes(value) && !next.options.length)
        next.options = [{ label: '', value: '' }];
      if (name === 'type' && value !== 'USER_PICKER') next.allowMultiple = false;
      return next;
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    const options = (optionMode === 'BULK' ? parseBulkOptions(bulkOptions) : (formData.options || []))
      .map(option => ({ label: option.label.trim(), value: option.value.trim() }));
    if (['SELECT', 'MULTI_CHOICE', 'RADIO'].includes(formData.type)) {
      if (!options.length) return setError('Hãy thêm ít nhất một lựa chọn.');
      if (options.some(option => !option.label || !option.value)) return setError('Nhãn và giá trị của lựa chọn không được để trống.');
      if (new Set(options.map(option => option.value)).size !== options.length) return setError('Giá trị của các lựa chọn không được trùng nhau.');
    }
    setError('');
    setSaving(true);
    try { await onSave({ ...formData, options }); } finally { setSaving(false); }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
    <div className="flex max-h-[calc(100vh-2rem)] w-full max-w-md flex-col overflow-hidden rounded-xl bg-white shadow-2xl">
      <div className="flex items-center justify-between border-b border-grayBorder px-6 py-4">
        <h2 className="font-bold text-slate-800">{editingField ? 'Sửa field' : 'Thêm field mới'}</h2>
        <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-700" aria-label="Đóng"><X size={19} /></button>
      </div>
      <form onSubmit={submit} className="space-y-4 overflow-y-auto p-6">
        <Field label="Tên field" required>
          <input name="label" value={formData.label} onChange={change} className="input-field" placeholder="Ví dụ: Số lượng thiết bị" required />
        </Field>
        <Field label="Field key">
          <input name="fieldKey" value={formData.fieldKey} onChange={change} className="input-field bg-gray-50" placeholder="Tự sinh nếu để trống" />
        </Field>
        <Field label="Loại dữ liệu">
          <select name="type" value={formData.type} onChange={change} className="input-field bg-white">
            <option value="TEXT">Text</option><option value="NUMBER">Number</option><option value="DATE">Date</option><option value="DATETIME">Ngày và giờ</option><option value="FILE">File đính kèm</option><option value="CHECKBOX">Checkbox</option><option value="SELECT">Select (dropdown)</option><option value="MULTI_CHOICE">Multi-choice</option><option value="RADIO">Radio</option><option value="USER_PICKER">User picker</option>
          </select>
        </Field>
        {['SELECT', 'MULTI_CHOICE', 'RADIO'].includes(formData.type) && <Field label="Danh sách lựa chọn" required>
          <div className="mb-3 flex rounded-lg bg-slate-100 p-1 text-xs font-semibold"><button type="button" onClick={()=>setOptionMode('MANUAL')} className={`flex-1 rounded-md px-3 py-2 ${optionMode==='MANUAL'?'bg-white text-orange-600 shadow-sm':'text-slate-500'}`}>Nhập từng lựa chọn</button><button type="button" onClick={()=>{setBulkOptions((formData.options||[]).map(option=>`${option.label} | ${option.value}`).join('\n'));setOptionMode('BULK')}} className={`flex-1 rounded-md px-3 py-2 ${optionMode==='BULK'?'bg-white text-orange-600 shadow-sm':'text-slate-500'}`}>Import danh sách</button></div>
          {optionMode==='BULK'?<div><textarea rows={8} value={bulkOptions} onChange={event=>setBulkOptions(event.target.value)} className="input-field resize-y font-mono text-xs" placeholder={'Miền Bắc | NORTH\nMiền Trung | CENTRAL\nMiền Nam | SOUTH'}/><p className="mt-2 text-[11px] font-normal leading-5 text-slate-500">Mỗi dòng là một lựa chọn. Dùng định dạng <b>Nhãn | Giá trị</b>; nếu không có dấu <b>|</b>, hệ thống dùng cùng nội dung cho nhãn và giá trị.</p><button type="button" onClick={()=>{const parsed=parseBulkOptions(bulkOptions);setFormData(current=>({...current,options:parsed.length?parsed:[{label:'',value:''}]}));setOptionMode('MANUAL')}} className="mt-2 text-xs font-semibold text-orange-600">Chuyển thành danh sách để chỉnh sửa</button></div>
          :<div className="space-y-2">{formData.options.map((option,index)=><div key={index} className="grid grid-cols-[1fr_1fr_auto] gap-2"><input value={option.label} onChange={e=>setFormData(current=>({...current,options:current.options.map((item,i)=>i===index?{...item,label:e.target.value}:item)}))} className="input-field" placeholder="Nhãn hiển thị" required/><input value={option.value} onChange={e=>setFormData(current=>({...current,options:current.options.map((item,i)=>i===index?{...item,value:e.target.value}:item)}))} className="input-field" placeholder="Giá trị" required/><button type="button" onClick={()=>setFormData(current=>({...current,options:current.options.filter((_,i)=>i!==index)}))} className="px-2 text-red-500">×</button></div>)}<button type="button" onClick={()=>setFormData(current=>({...current,options:[...current.options,{label:'',value:''}]}))} className="w-full rounded-lg border border-dashed border-orange-300 py-2 text-xs font-semibold text-orange-500">+ Thêm lựa chọn</button></div>}
        </Field>}
        {formData.type === 'USER_PICKER' && <label className="flex items-center justify-between text-sm font-semibold text-slate-700">Cho phép chọn nhiều người<input type="checkbox" name="allowMultiple" checked={formData.allowMultiple} onChange={change} className="h-4 w-4 accent-orange-500"/></label>}
        <label className="flex items-center justify-between text-sm font-semibold text-slate-700">
          Bắt buộc nhập
          <input type="checkbox" name="required" checked={formData.required} onChange={change} className="h-4 w-4 accent-orange-500" />
        </label>
        <Field label="Placeholder gợi ý">
          <input name="placeholder" value={formData.placeholder} onChange={change} className="input-field" placeholder="Nhập gợi ý cho người dùng..." />
        </Field>
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>}
        <div className="flex gap-3 border-t border-grayBorder pt-4">
          <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-grayBorder py-2 text-sm font-medium text-gray-700">Hủy</button>
          <button type="submit" disabled={saving} className="btn-primary flex-1 py-2 text-sm disabled:opacity-60">{saving ? 'Đang lưu...' : editingField ? 'Cập nhật' : 'Thêm field'}</button>
        </div>
      </form>
    </div>
  </div>;
}

function Field({ label, required, children }) {
  return <label className="block text-sm font-semibold text-slate-700">{label}{required && <span className="text-red-500"> *</span>}<div className="mt-1.5">{children}</div></label>;
}

function parseBulkOptions(value) {
  return String(value || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => {
    const separator = line.indexOf('|');
    if (separator < 0) return { label: line, value: line };
    return { label: line.slice(0, separator).trim(), value: line.slice(separator + 1).trim() };
  });
}
