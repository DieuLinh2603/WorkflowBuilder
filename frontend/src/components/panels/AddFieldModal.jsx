import { useEffect, useState } from 'react';
import { X } from 'lucide-react';

const EMPTY_FIELD = { label: '', fieldKey: '', type: 'TEXT', required: false, placeholder: '' };

export default function AddFieldModal({ isOpen, onClose, onSave, editingField }) {
  const [formData, setFormData] = useState(EMPTY_FIELD);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (isOpen) setFormData(editingField ? { ...EMPTY_FIELD, ...editingField } : EMPTY_FIELD);
  }, [isOpen, editingField]);

  if (!isOpen) return null;

  const change = (event) => {
    const { name, value, type, checked } = event.target;
    setFormData((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try { await onSave(formData); } finally { setSaving(false); }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
    <div className="w-full max-w-md overflow-hidden rounded-xl bg-white shadow-2xl">
      <div className="flex items-center justify-between border-b border-grayBorder px-6 py-4">
        <h2 className="font-bold text-slate-800">{editingField ? 'Sửa field' : 'Thêm field mới'}</h2>
        <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-700" aria-label="Đóng"><X size={19} /></button>
      </div>
      <form onSubmit={submit} className="space-y-4 p-6">
        <Field label="Tên field" required>
          <input name="label" value={formData.label} onChange={change} className="input-field" placeholder="Ví dụ: Số lượng thiết bị" required />
        </Field>
        <Field label="Field key">
          <input name="fieldKey" value={formData.fieldKey} onChange={change} className="input-field bg-gray-50" placeholder="Tự sinh nếu để trống" />
        </Field>
        <Field label="Loại dữ liệu">
          <select name="type" value={formData.type} onChange={change} className="input-field bg-white">
            <option value="TEXT">Text</option><option value="NUMBER">Number</option><option value="DATE">Date</option><option value="FILE">File đính kèm</option><option value="CHECKBOX">Checkbox</option>
          </select>
        </Field>
        <label className="flex items-center justify-between text-sm font-semibold text-slate-700">
          Bắt buộc nhập
          <input type="checkbox" name="required" checked={formData.required} onChange={change} className="h-4 w-4 accent-orange-500" />
        </label>
        <Field label="Placeholder gợi ý">
          <input name="placeholder" value={formData.placeholder} onChange={change} className="input-field" placeholder="Nhập gợi ý cho người dùng..." />
        </Field>
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
