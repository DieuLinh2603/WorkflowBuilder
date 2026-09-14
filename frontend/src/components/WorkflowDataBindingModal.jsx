import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, DatabaseZap, X } from 'lucide-react';
import { apiError, apiFetch } from '../api';

const normalizeKey = value => String(value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

export default function WorkflowDataBindingModal({ workflow, onClose }) {
  const [datasets, setDatasets] = useState([]);
  const [bindings, setBindings] = useState([]);
  const [datasetId, setDatasetId] = useState('');
  const [triggerMode, setTriggerMode] = useState('AUTO_ON_DATASET_SUCCESS');
  const [outputFields, setOutputFields] = useState([]);
  const [workflowFields, setWorkflowFields] = useState([]);
  const [mapping, setMapping] = useState({});
  const [filter, setFilter] = useState('[]');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [runningBindingId, setRunningBindingId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadBindings = async () => {
    const response = await apiFetch(`/api/workflow-data-bindings?workflowId=${workflow.id}`, { toast: false });
    if (!response.ok) throw new Error(await apiError(response));
    setBindings(await response.json());
  };

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const [datasetResponse, bindingResponse, stepResponse] = await Promise.all([
          apiFetch('/api/datasets', { toast: false }),
          apiFetch(`/api/workflow-data-bindings?workflowId=${workflow.id}`, { toast: false }),
          apiFetch(`/api/workflows/${workflow.id}/steps`, { toast: false }),
        ]);
        if (!datasetResponse.ok) throw new Error(await apiError(datasetResponse));
        if (!bindingResponse.ok) throw new Error(await apiError(bindingResponse));
        if (!stepResponse.ok) throw new Error(await apiError(stepResponse));

        const datasetItems = await datasetResponse.json();
        const stepItems = await stepResponse.json();
        const startStep = stepItems.find(item => item.type === 'START');
        if (!startStep) throw new Error('Workflow không có START Step.');

        const fieldResponse = await apiFetch(`/api/workflows/${workflow.id}/steps/${startStep.id}/fields`, { toast: false });
        if (!fieldResponse.ok) throw new Error(await apiError(fieldResponse));
        if (!active) return;

        setDatasets(datasetItems);
        setBindings(await bindingResponse.json());
        setWorkflowFields(await fieldResponse.json());
        setDatasetId(current => current || datasetItems[0]?.id || '');
      } catch (reason) {
        if (active) setError(reason.message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => { active = false; };
  }, [workflow.id]);

  useEffect(() => {
    let active = true;
    const dataset = datasets.find(item => item.id === datasetId);
    if (!dataset) {
      setOutputFields([]);
      setMapping({});
      return () => { active = false; };
    }

    (async () => {
      setError('');
      const response = await apiFetch(`/api/pipelines/${dataset.pipelineId}`, { toast: false });
      if (!response.ok) {
        if (active) setError(await apiError(response));
        return;
      }
      const pipeline = await response.json();
      if (!active) return;
      const fields = Array.isArray(pipeline.outputSchema) ? pipeline.outputSchema : [];
      const suggested = {};
      fields.forEach(output => {
        const sourceKey = output.fieldKey;
        const match = workflowFields.find(field => normalizeKey(field.fieldKey) === normalizeKey(sourceKey));
        if (sourceKey && match) suggested[sourceKey] = match.fieldKey;
      });
      const existing = bindings.find(binding => binding.datasetId === datasetId);
      setOutputFields(fields);
      setMapping(existing?.mapping || suggested);
      setTriggerMode(existing?.triggerMode || 'AUTO_ON_DATASET_SUCCESS');
      setFilter(JSON.stringify(existing?.filter || [], null, 2));
    })().catch(reason => active && setError(reason.message));
    return () => { active = false; };
  }, [datasetId, datasets, workflowFields, bindings]);

  const mappedCount = useMemo(() => Object.values(mapping).filter(Boolean).length, [mapping]);
  const currentBinding = useMemo(() => bindings.find(binding => binding.datasetId === datasetId), [bindings, datasetId]);

  const updateMapping = (sourceKey, targetKey) => {
    setMapping(current => {
      const next = { ...current };
      if (targetKey) next[sourceKey] = targetKey;
      else delete next[sourceKey];
      return next;
    });
  };

  const save = async () => {
    setError('');
    setNotice('');
    if (!mappedCount) return setError('Hãy mapping ít nhất một cột Pipeline vào field của START Step.');
    let parsedFilter;
    try {
      parsedFilter = JSON.parse(filter);
      if (!Array.isArray(parsedFilter)) throw new Error('Filter phải là một danh sách JSON.');
    } catch (reason) {
      return setError(`Filter không hợp lệ: ${reason.message}`);
    }

    setSaving(true);
    try {
      const response = await apiFetch(currentBinding ? `/api/workflow-data-bindings/${currentBinding.id}` : '/api/workflow-data-bindings', {
        method: currentBinding ? 'PUT' : 'POST',
        successMessage: currentBinding ? 'Đã cập nhật Data Binding.' : 'Đã tạo Data Binding.',
        body: JSON.stringify({ datasetId, workflowId: workflow.id, triggerMode, mapping, filter: parsedFilter, active: true }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      await loadBindings();
      setNotice(triggerMode === 'MANUAL'
        ? 'Đã lưu Binding. Khi muốn gửi dữ liệu, hãy bấm “Đưa dữ liệu mới vào Workflow” ở phần Bindings hiện tại.'
        : 'Đã lưu Binding. Dữ liệu mới sẽ tự động được đưa vào Workflow sau mỗi lần Pipeline chạy thành công.');
    } catch (reason) {
      setError(reason.message);
    } finally {
      setSaving(false);
    }
  };

  const runBinding = async bindingId => {
    setError('');
    setNotice('');
    setRunningBindingId(bindingId);
    try {
      const response = await apiFetch(`/api/workflow-data-bindings/${bindingId}/run`, {
        method: 'POST', toast: false,
      });
      if (!response.ok) throw new Error(await apiError(response));
      const result = await response.json();
      setNotice(result.status === 'NO_CHANGES'
        ? 'Không có dòng mới hoặc thay đổi để đưa vào Workflow.'
        : `Đã đưa ${result.batch?.total || 0} dòng mới/thay đổi vào Workflow.`);
      await loadBindings();
    } catch (reason) {
      setError(reason.message);
    } finally {
      setRunningBindingId('');
    }
  };

  return <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/40 p-4" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <div role="dialog" aria-modal="true" aria-labelledby="data-binding-title" className="max-h-[90vh] w-full max-w-4xl overflow-auto rounded-2xl bg-white p-6 shadow-xl">
      <div className="flex items-start justify-between gap-4">
        <div><h2 id="data-binding-title" className="flex items-center gap-2 text-xl font-bold"><DatabaseZap className="text-emerald-600" size={21}/>Data Binding</h2><p className="mt-1 text-sm text-gray-500">Dataset → filter → mapping → một workflow batch.</p></div>
        <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700" aria-label="Đóng"><X size={19}/></button>
      </div>

      {error && <p className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600">{error}</p>}
      {notice && <p className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>}

      <div className="mt-5 grid gap-4 md:grid-cols-2">
        <label className="text-xs font-bold text-gray-500">DATASET
          <select className="input-field mt-1" value={datasetId} onChange={event => setDatasetId(event.target.value)} disabled={loading}>
            {!datasets.length && <option value="">Chưa có Dataset</option>}
            {datasets.map(dataset => <option key={dataset.id} value={dataset.id}>{dataset.name} · v{dataset.latestVersion}</option>)}
          </select>
        </label>
        <label className="text-xs font-bold text-gray-500">TRIGGER
          <select className="input-field mt-1" value={triggerMode} onChange={event => setTriggerMode(event.target.value)}>
            <option value="AUTO_ON_DATASET_SUCCESS">Tự động khi Pipeline chạy thành công</option>
            <option value="MANUAL">Thủ công — tôi bấm nút đưa dữ liệu vào Workflow</option>
          </select>
        </label>
      </div>
      <div className={`mt-3 rounded-xl border px-4 py-3 text-xs leading-5 ${triggerMode === 'MANUAL' ? 'border-blue-200 bg-blue-50 text-blue-800' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>
        {triggerMode === 'MANUAL'
          ? <><b>Bạn quyết định thời điểm chạy.</b><span className="mt-1 block">1. Lưu Binding và mapping. 2. Bấm nút <b>“Đưa dữ liệu mới vào Workflow”</b> ngay bên dưới. Hệ thống lấy Dataset mới nhất và chỉ gửi những dòng chưa xử lý hoặc đã thay đổi.</span></>
          : <><b>Hệ thống tự chạy.</b> Mỗi khi Pipeline chạy xong, các dòng mới hoặc thay đổi sẽ tự động được đưa vào Workflow; bạn không cần bấm thêm nút nào.</>}
      </div>

      {triggerMode === 'MANUAL' && <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border-2 border-emerald-300 bg-emerald-50 px-4 py-4">
        <div><p className="text-sm font-bold text-emerald-800">Thao tác chạy thủ công</p><p className="mt-1 text-xs text-emerald-700">{currentBinding ? `Binding đã sẵn sàng · đã xử lý đến Dataset v${currentBinding.lastConsumedVersion}` : 'Hãy hoàn tất mapping và lưu Binding trước.'}</p></div>
        <button type="button" disabled={!currentBinding || saving || runningBindingId === currentBinding?.id} onClick={() => currentBinding && runBinding(currentBinding.id)} className="rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-40">{runningBindingId === currentBinding?.id ? 'Đang đưa dữ liệu...' : 'Đưa dữ liệu mới vào Workflow'}</button>
      </div>}

      <section className="mt-5 overflow-hidden rounded-xl border border-grayBorder">
        <div className="flex items-center justify-between bg-slate-50 px-4 py-3">
          <div><h3 className="text-sm font-bold text-slate-700">Mapping dữ liệu</h3><p className="mt-0.5 text-xs text-gray-500">Chọn field START nhận giá trị tương ứng từ mỗi cột Pipeline.</p></div>
          <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">{mappedCount}/{outputFields.length} cột</span>
        </div>
        <div className="grid grid-cols-[minmax(0,1fr)_36px_minmax(0,1fr)] gap-3 border-t border-grayBorder bg-white px-4 py-2 text-[10px] font-bold uppercase text-gray-400"><span>Pipeline output</span><span/><span>Workflow START field</span></div>
        {outputFields.map(field => <div key={field.fieldKey} className="grid grid-cols-[minmax(0,1fr)_36px_minmax(0,1fr)] items-center gap-3 border-t border-slate-100 px-4 py-3">
          <div className="min-w-0"><p className="truncate text-sm font-semibold text-slate-700">{field.fieldKey}</p><p className="text-[10px] text-gray-400">{field.type || 'STRING'}{field.required ? ' · bắt buộc' : ''}</p></div>
          <ArrowRight size={16} className="text-gray-300"/>
          <select className="input-field py-2 text-sm" value={mapping[field.fieldKey] || ''} onChange={event => updateMapping(field.fieldKey, event.target.value)}>
            <option value="">Không sử dụng cột này</option>
            {workflowFields.map(target => <option key={target.id} value={target.fieldKey}>{target.label} ({target.fieldKey}) · {target.type}</option>)}
          </select>
        </div>)}
        {!loading && !outputFields.length && <p className="border-t border-slate-100 px-4 py-6 text-center text-sm text-gray-500">Pipeline chưa có Output Schema. Hãy cấu hình và chạy Pipeline trước.</p>}
        {!loading && !workflowFields.length && <p className="border-t border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700">START Step chưa có field để nhận dữ liệu.</p>}
      </section>

      <label className="mt-5 block text-xs font-bold text-gray-500">FILTER (TÙY CHỌN)
        <textarea className="mt-1 h-24 w-full rounded-lg border border-grayBorder p-3 font-mono text-xs" value={filter} onChange={event => setFilter(event.target.value)} placeholder='[{"field":"amount","operator":"GT","value":1000000}]'/>
      </label>

      <button type="button" disabled={!datasetId || loading || saving || !mappedCount} onClick={save} className="btn-primary mt-4 disabled:opacity-40">{saving ? 'Đang lưu...' : currentBinding ? 'Cập nhật binding' : 'Tạo binding'}</button>

      <section className="mt-7 border-t border-grayBorder pt-5">
        <h3 className="font-bold text-slate-700">Bindings hiện tại</h3>
        {bindings.map(binding => <div key={binding.id} className="mt-2 flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm">
          <span><b className="text-slate-700">{datasets.find(dataset => dataset.id === binding.datasetId)?.name || binding.datasetId}</b><small className="mt-1 block text-gray-400">{binding.triggerMode === 'MANUAL' ? 'Bạn tự chọn thời điểm đưa dữ liệu vào Workflow' : 'Tự động sau khi Pipeline chạy'} · {Object.keys(binding.mapping || {}).length} cột · đã xử lý đến Dataset v{binding.lastConsumedVersion}</small></span>
          {binding.triggerMode === 'MANUAL' && <button type="button" disabled={runningBindingId === binding.id} onClick={() => runBinding(binding.id)} className="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700 hover:bg-emerald-100 disabled:opacity-50">{runningBindingId === binding.id ? 'Đang đưa dữ liệu...' : 'Đưa dữ liệu mới vào Workflow'}</button>}
        </div>)}
        {!bindings.length && <p className="mt-2 text-sm text-gray-400">Chưa có binding nào cho workflow này.</p>}
      </section>
    </div>
  </div>;
}
