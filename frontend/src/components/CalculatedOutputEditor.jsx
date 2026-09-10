import { useState } from 'react';

export default function CalculatedOutputEditor({ value = [], onChange, fields = [], disabled = false }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const update = (index, changes) => onChange(value.map((item, i) => i === index ? { ...item, ...changes } : item));
  const insert = text => {
    if (!value[activeIndex]) return;
    update(activeIndex, { formula: (value[activeIndex].formula || '') + text });
  };
  const keys = [...new Set([...fields.map(field => typeof field === 'string' ? field : field.fieldKey), ...value.slice(0, activeIndex).map(item => item.fieldKey)].filter(key => key && !key.startsWith('_')))];
  return <section className="space-y-3">
    <h3 className="text-sm font-bold text-slate-800">Cột output tính toán</h3>
    <p className="text-xs leading-5 text-slate-500">Dùng [mã_cột], số và + − * /, có thể kết hợp dấu ngoặc. Ví dụ: [so_luong] * [don_gia], SUM([thanh_tien]), AVG([diem]). SUM/AVG tính toàn bộ các dòng được xử lý; yêu cầu đơn dùng giá trị hoặc danh sách số của cột.</p>
    {value.map((item, index) => <div key={index} className="space-y-2 rounded-xl border border-slate-200 bg-white p-3" onFocus={() => setActiveIndex(index)}>
      <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-slate-600">Tên hiển thị<input aria-label={`Tên output ${index + 1}`} maxLength={200} value={item.label || ''} onChange={event => update(index, { label: event.target.value })} disabled={disabled} placeholder="Thành tiền" className="input-field mt-1 text-xs"/></label>
        <label className="text-xs text-slate-600">Mã cột mới<input aria-label={`Mã output ${index + 1}`} maxLength={100} value={item.fieldKey || ''} onChange={event => update(index, { fieldKey: event.target.value })} disabled={disabled} placeholder="thanh_tien" className="input-field mt-1 text-xs"/></label>
      </div>
      <label className="block text-xs text-slate-600">Công thức<textarea aria-label={`Công thức output ${index + 1}`} rows={2} maxLength={1000} value={item.formula || ''} onChange={event => update(index, { formula: event.target.value })} disabled={disabled} placeholder="[so_luong] * [don_gia]" className="input-field mt-1 font-mono text-xs"/></label>
      {!disabled && <button type="button" onClick={() => { onChange(value.filter((_, i) => i !== index)); setActiveIndex(0); }} className="text-xs text-red-500">Xóa output</button>}
    </div>)}
    {!disabled && <>
      {!!value.length && <div className="space-y-2 rounded-lg bg-slate-50 p-3">
        <p className="text-[11px] text-slate-500">Chèn vào cuối công thức output {Math.min(activeIndex + 1, value.length)}</p>
        <select aria-label="Chèn cột vào công thức" value="" onChange={event => insert(`[${event.target.value}]`)} className="input-field bg-white text-xs"><option value="">Chọn cột nguồn...</option>{keys.map(key => <option key={key} value={key}>{key}</option>)}</select>
        <div className="flex flex-wrap gap-1">{[' + ', ' - ', ' * ', ' / ', '(', ')', 'SUM(', 'AVG('].map(token => <button key={token} type="button" onClick={() => insert(token)} className="rounded border border-slate-200 bg-white px-2 py-1 text-xs">{token.trim()}</button>)}</div>
      </div>}
      <button type="button" disabled={value.length >= 50} onClick={() => { setActiveIndex(value.length); onChange([...value, { fieldKey: '', label: '', formula: '' }]); }} className="w-full rounded-lg border border-dashed border-orange-400 py-2 text-xs font-semibold text-orange-600 disabled:opacity-40">+ Thêm cột output tính toán</button>
      <p className="text-[11px] leading-5 text-slate-400">Các output tính từ trên xuống; công thức có thể dùng output phía trên. Đặt mã mới để giữ dữ liệu nguồn. Dữ liệu trống, không phải số hoặc chia cho 0 sẽ báo lỗi.</p>
    </>}
  </section>;
}

export function CalculatedOutputTable({ outputs = [], rows = [] }) {
  if (!rows.length || !outputs.length) return null;
  return <div className="mt-3 max-h-80 overflow-auto rounded-lg border border-slate-200"><table className="w-full text-left text-xs"><thead className="sticky top-0 bg-slate-100"><tr><th className="px-3 py-2">Dòng</th>{outputs.map(output => <th key={output.fieldKey} className="px-3 py-2">{output.label || output.fieldKey}</th>)}</tr></thead><tbody>{rows.map((row, i) => <tr key={i} className="border-t border-slate-100"><td className="px-3 py-2">{i + 1}</td>{outputs.map(output => <td key={output.fieldKey} className="px-3 py-2">{String(row[output.fieldKey] ?? '')}</td>)}</tr>)}</tbody></table></div>;
}
