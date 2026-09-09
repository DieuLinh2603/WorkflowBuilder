import { AlertTriangle, Calculator, Plus, Trash2 } from 'lucide-react';

const CALCULATIONS = [
  ['SUM', 'Tổng', 'Cộng tất cả toán hạng'],
  ['ADD', 'Phép cộng', 'Cộng các trường hoặc giá trị'],
  ['SUBTRACT', 'Phép trừ', 'Toán hạng thứ nhất trừ toán hạng thứ hai'],
  ['MULTIPLY', 'Phép nhân', 'Nhân các toán hạng'],
  ['DIVIDE', 'Phép chia', 'Toán hạng thứ nhất chia toán hạng thứ hai'],
  ['MOD', 'Chia lấy dư', 'Lấy phần dư của phép chia'],
  ['AVG', 'Trung bình', 'Trung bình các toán hạng'],
  ['MIN', 'Nhỏ nhất', 'Giá trị nhỏ nhất'],
  ['MAX', 'Lớn nhất', 'Giá trị lớn nhất'],
  ['COUNT', 'Đếm', 'Đếm các giá trị không rỗng'],
  ['ABS', 'Giá trị tuyệt đối', 'Bỏ dấu âm của một giá trị'],
  ['ROUND', 'Làm tròn', 'Làm tròn một giá trị'],
];

const COMPARISONS = [
  ['EQ', 'Bằng (=)'], ['NEQ', 'Khác (≠)'], ['GT', 'Lớn hơn (>)'],
  ['GTE', 'Lớn hơn hoặc bằng (≥)'], ['LT', 'Nhỏ hơn (<)'], ['LTE', 'Nhỏ hơn hoặc bằng (≤)'],
];

const FIXED_TWO = new Set(['SUBTRACT', 'DIVIDE', 'MOD']);
const FIXED_ONE = new Set(['ABS', 'ROUND']);
const fieldOperand = (fieldKey = '') => ({ type: 'FIELD', fieldKey });
const valueOperand = (value = '') => ({ type: 'VALUE', value });

export function createCalculationExpression() {
  return {
    type: 'COMPARE', operator: 'GT',
    left: { type: 'SUM', operands: [fieldOperand(), fieldOperand()] },
    right: valueOperand(''),
  };
}

function operandsOf(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node.operands)) return node.operands;
  if (Array.isArray(node.fields)) return node.fields.map(fieldOperand);
  if (node.operand) return [node.operand];
  return [];
}

function editableExpression(value) {
  if (!value || value.type !== 'COMPARE' || !CALCULATIONS.some(([type]) => type === value.left?.type)) return null;
  const operands = operandsOf(value.left);
  if (!operands.every(operand => ['FIELD', 'VALUE', 'LITERAL'].includes(operand?.type))) return null;
  return { ...value, left: { ...value.left, operands } };
}

function requiredOperandCount(type) {
  if (FIXED_ONE.has(type)) return 1;
  if (FIXED_TWO.has(type)) return 2;
  return 2;
}

export function isCalculationExpressionValid(value, fields) {
  const expression = editableExpression(value);
  if (!expression || !COMPARISONS.some(([operator]) => operator === expression.operator)) return false;
  const fieldKeys = new Set(fields.map(field => field.fieldKey));
  const numericFieldKeys = new Set(fields.filter(field => field.type === 'NUMBER').map(field => field.fieldKey));
  const operands = expression.left.operands;
  const required = requiredOperandCount(expression.left.type);
  if (operands.length < required || (FIXED_ONE.has(expression.left.type) && operands.length !== 1) || (FIXED_TWO.has(expression.left.type) && operands.length !== 2)) return false;
  const validOperand = operand => operand?.type === 'FIELD'
    ? (expression.left.type === 'COUNT' ? fieldKeys : numericFieldKeys).has(operand.fieldKey)
    : ['VALUE', 'LITERAL'].includes(operand?.type) && String(operand.value ?? '').trim() !== '';
  const validRight = expression.right?.type === 'FIELD'
    ? numericFieldKeys.has(expression.right.fieldKey)
    : ['VALUE', 'LITERAL'].includes(expression.right?.type) && String(expression.right.value ?? '').trim() !== '';
  return operands.every(validOperand) && validRight;
}

export default function CalculationExpressionBuilder({ value, onChange, fields, loading }) {
  const expression = editableExpression(value);
  if (!expression) return <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
    <div className="flex gap-2 text-sm text-amber-800"><AlertTriangle size={17} className="mt-0.5 shrink-0"/><div><p className="font-semibold">Biểu thức đã lưu chưa thể hiển thị bằng trình dựng.</p><p className="mt-1 text-xs leading-5">Bạn có thể tạo lại biểu thức bằng giao diện trực quan. Dữ liệu cũ chỉ được thay thế sau khi bấm Áp dụng.</p></div></div>
    <button type="button" onClick={() => onChange(createCalculationExpression())} className="mt-3 rounded-lg bg-amber-600 px-3 py-2 text-xs font-semibold text-white hover:bg-amber-700">Tạo lại biểu thức</button>
  </div>;

  const calculation = expression.left.type;
  const operands = expression.left.operands;
  const numericFields = fields.filter(field => field.type === 'NUMBER');
  const operandFields = calculation === 'COUNT' ? fields : numericFields;
  const setExpression = next => onChange(next);
  const updateLeft = values => setExpression({ ...expression, left: { ...expression.left, ...values } });
  const updateOperand = (index, values) => updateLeft({ operands: operands.map((operand, operandIndex) => operandIndex === index ? { ...operand, ...values } : operand) });
  const changeOperandType = (index, type) => updateOperand(index, type === 'FIELD' ? fieldOperand() : valueOperand());
  const removeOperand = index => updateLeft({ operands: operands.filter((_, operandIndex) => operandIndex !== index) });
  const addOperand = () => updateLeft({ operands: [...operands, fieldOperand()] });
  const changeCalculation = type => {
    const count = requiredOperandCount(type);
    const nextOperands = Array.from({ length: count }, (_, index) => operands[index] || fieldOperand());
    updateLeft({ type, operands: nextOperands, ...(type === 'ROUND' ? { scale: expression.left.scale ?? 0 } : {}) });
  };

  return <div className="space-y-4 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
    <div className="flex items-start gap-3">
      <span className="rounded-lg bg-orange-100 p-2 text-orange-600"><Calculator size={18}/></span>
      <div><p className="text-sm font-semibold text-slate-700">Tạo phép tính trên các trường</p><p className="mt-0.5 text-[11px] text-slate-500">Chọn phép tính, các toán hạng và điều kiện so sánh. Hệ thống sẽ tự tạo biểu thức.</p></div>
    </div>

    {loading ? <div className="rounded-lg bg-white px-4 py-3 text-sm text-slate-500">Đang tải các trường...</div> : <>
      {!numericFields.length && <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs leading-5 text-amber-700"><AlertTriangle size={15} className="mt-0.5 shrink-0"/><span>Workflow chưa có trường kiểu Number. Hãy thêm trường Number để thực hiện phép tính.</span></div>}
      <label className="block"><span className="mb-1.5 block text-xs font-semibold text-slate-600">Phép tính</span>
        <select value={calculation} onChange={event => changeCalculation(event.target.value)} className="input-field bg-white">
          {CALCULATIONS.map(([type, label, description]) => <option key={type} value={type}>{label} — {description}</option>)}
        </select>
      </label>

      <div>
        <div className="mb-2 flex items-center justify-between"><span className="text-xs font-semibold text-slate-600">Các trường và giá trị tham gia</span><span className="text-[10px] text-slate-400">{operands.length} toán hạng</span></div>
        <div className="space-y-2">
          {operands.map((operand, index) => <div key={index} className="grid grid-cols-[110px_minmax(0,1fr)_36px] gap-2 rounded-lg border border-slate-200 bg-white p-2">
            <select value={operand.type === 'FIELD' ? 'FIELD' : 'VALUE'} onChange={event => changeOperandType(index, event.target.value)} className="rounded-lg border border-grayBorder bg-white px-2 py-2 text-xs outline-none focus:border-orange-400">
              <option value="FIELD">Trường</option><option value="VALUE">Giá trị</option>
            </select>
            {operand.type === 'FIELD'
              ? <select value={operand.fieldKey || ''} onChange={event => updateOperand(index, { fieldKey: event.target.value })} className="input-field bg-white"><option value="">Chọn trường dữ liệu...</option>{operandFields.map(field => <option key={field.fieldKey} value={field.fieldKey}>{field.label} ({field.fieldKey})</option>)}</select>
              : <input type="number" value={operand.value ?? ''} onChange={event => updateOperand(index, { type: 'VALUE', value: event.target.value })} className="input-field" placeholder="Nhập số"/>}
            <button type="button" onClick={() => removeOperand(index)} disabled={FIXED_ONE.has(calculation) || FIXED_TWO.has(calculation) || operands.length <= 2} title="Xóa toán hạng" className="flex h-10 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-500 disabled:cursor-not-allowed disabled:opacity-30"><Trash2 size={16}/></button>
          </div>)}
        </div>
        {!FIXED_ONE.has(calculation) && !FIXED_TWO.has(calculation) && <button type="button" onClick={addOperand} className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-orange-600 hover:text-orange-700"><Plus size={15}/>Thêm trường hoặc giá trị</button>}
        {calculation === 'ROUND' && <label className="mt-3 block max-w-[220px]"><span className="mb-1 block text-xs font-semibold text-slate-600">Số chữ số thập phân</span><input type="number" min="0" max="10" value={expression.left.scale ?? 0} onChange={event => updateLeft({ scale: Math.max(0, Math.min(10, Number(event.target.value) || 0)) })} className="input-field bg-white"/></label>}
      </div>

      <div className="grid grid-cols-1 gap-3 border-t border-slate-200 pt-4 sm:grid-cols-[minmax(180px,1fr)_minmax(0,1.4fr)]">
        <label><span className="mb-1.5 block text-xs font-semibold text-slate-600">Điều kiện so sánh</span><select value={expression.operator} onChange={event => setExpression({ ...expression, operator: event.target.value })} className="input-field bg-white">{COMPARISONS.map(([operator, label]) => <option key={operator} value={operator}>{label}</option>)}</select></label>
        <div><span className="mb-1.5 block text-xs font-semibold text-slate-600">So sánh với</span><div className="grid grid-cols-[110px_minmax(0,1fr)] gap-2">
          <select value={expression.right?.type === 'FIELD' ? 'FIELD' : 'VALUE'} onChange={event => setExpression({ ...expression, right: event.target.value === 'FIELD' ? fieldOperand() : valueOperand() })} className="rounded-lg border border-grayBorder bg-white px-2 py-2 text-xs outline-none focus:border-orange-400"><option value="VALUE">Giá trị</option><option value="FIELD">Trường</option></select>
          {expression.right?.type === 'FIELD'
            ? <select value={expression.right.fieldKey || ''} onChange={event => setExpression({ ...expression, right: fieldOperand(event.target.value) })} className="input-field bg-white"><option value="">Chọn trường Number...</option>{numericFields.map(field => <option key={field.fieldKey} value={field.fieldKey}>{field.label} ({field.fieldKey})</option>)}</select>
            : <input type="number" value={expression.right?.value ?? ''} onChange={event => setExpression({ ...expression, right: valueOperand(event.target.value) })} className="input-field" placeholder="Nhập số để so sánh"/>}
        </div></div>
      </div>
      <FormulaPreview expression={expression} fields={fields}/>
    </>}
  </div>;
}

function FormulaPreview({ expression, fields }) {
  const labels = new Map(fields.map(field => [field.fieldKey, field.label || field.fieldKey]));
  const operandText = operand => operand.type === 'FIELD' ? (labels.get(operand.fieldKey) || 'Chọn trường') : (String(operand.value ?? '').trim() || '...');
  const symbols = { ADD: '+', SUBTRACT: '−', MULTIPLY: '×', DIVIDE: '÷', MOD: '%', EQ: '=', NEQ: '≠', GT: '>', GTE: '≥', LT: '<', LTE: '≤' };
  const args = expression.left.operands.map(operandText);
  const left = symbols[expression.left.type]
    ? `(${args.join(` ${symbols[expression.left.type]} `)})`
    : `${CALCULATIONS.find(([type]) => type === expression.left.type)?.[1] || expression.left.type}(${args.join(', ')})`;
  return <div className="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3"><p className="text-[10px] font-bold uppercase tracking-wide text-blue-500">Công thức xem trước</p><p className="mt-1 break-words text-sm font-semibold text-slate-700">{left} {symbols[expression.operator] || expression.operator} {operandText(expression.right)}</p></div>;
}
