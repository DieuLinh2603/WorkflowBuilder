import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Braces, CalendarClock, Check, ChevronLeft, ChevronRight, Database,
  Eye, FileSpreadsheet, Filter, GitMerge, PauseCircle, Plus, RefreshCw, RotateCw, Trash2,
} from 'lucide-react';
import { apiError, apiFetch } from '../api';
import DeletePipelineModal from '../components/DeletePipelineModal';

const STEPS = [
  { label: 'Nguồn dữ liệu', icon: Database },
  { label: 'Ghép & xử lý', icon: GitMerge },
  { label: 'Dữ liệu đầu ra', icon: FileSpreadsheet },
  { label: 'Lịch chạy', icon: CalendarClock },
];
const emptyDefinition = { sources: [], joins: [], transforms: [] };
const outputTypes = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE'];
const operators = [
  ['EQUALS', 'Bằng'], ['NOT_EQUALS', 'Khác'], ['GT', 'Lớn hơn'], ['GTE', 'Lớn hơn hoặc bằng'],
  ['LT', 'Nhỏ hơn'], ['LTE', 'Nhỏ hơn hoặc bằng'], ['CONTAINS', 'Có chứa'],
  ['IS_EMPTY', 'Để trống'], ['NOT_EMPTY', 'Không trống'],
];

const pretty = value => JSON.stringify(value, null, 2);
const splitKeys = value => String(value || '').split(',').map(x => x.trim()).filter(Boolean);
const uniqueKey = (source, used) => {
  const base = String(source || 'field').replace(/[^a-zA-Z0-9_]/g, '_').replace(/^_+/, '') || 'field';
  let key = base;
  let number = 2;
  while (used.has(key)) key = `${base}_${number++}`;
  used.add(key);
  return key;
};
const inferredType = type => type === 'NUMBER' ? 'DECIMAL' : outputTypes.includes(type) ? type : 'STRING';
const preferredBusinessField = (fields, definition) => (definition.joins || []).some(join => join.cardinality === 'ONE_TO_MANY')
  ? fields.find(field => /\.id$/i.test(field.source || '')) : null;

export default function PipelineDesignerPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [pipeline, setPipeline] = useState(null);
  const [definition, setDefinition] = useState(emptyDefinition);
  const [schema, setSchema] = useState([]);
  const [connectors, setConnectors] = useState([]);
  const [runs, setRuns] = useState([]);
  const [discovery, setDiscovery] = useState(null);
  const [sourceDiscoveries, setSourceDiscoveries] = useState({});
  const [preview, setPreview] = useState(null);
  const [outputPreview, setOutputPreview] = useState(null);
  const [step, setStep] = useState(0);
  const [mode, setMode] = useState('GUIDED');
  const [advancedDefinition, setAdvancedDefinition] = useState('');
  const [advancedSchema, setAdvancedSchema] = useState('');
  const [files, setFiles] = useState({});
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [deleteState, setDeleteState] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const [pipelineResponse, runResponse, connectorResponse] = await Promise.all([
          apiFetch(`/api/pipelines/${id}`), apiFetch(`/api/pipelines/${id}/runs`), apiFetch('/api/connectors'),
        ]);
        if (!pipelineResponse.ok) throw new Error(await apiError(pipelineResponse));
        const loaded = await pipelineResponse.json();
        setPipeline({ ...loaded, timezone: 'Asia/Ho_Chi_Minh' });
        setDefinition({ ...emptyDefinition, ...(loaded.definition || {}) });
        setSchema(loaded.outputSchema || []);
        if (runResponse.ok) setRuns(await runResponse.json());
        if (connectorResponse.ok) setConnectors(await connectorResponse.json());
      } catch (reason) {
        setError(reason.message);
      }
    })();
  }, [id]);

  const availableFields = useMemo(() => {
    const fields = new Set(Object.keys(discovery?.detectedSchema || {}));
    schema.forEach(field => fields.add(field.source || field.fieldKey));
    return [...fields];
  }, [discovery, schema]);

  const requestDefinition = () => ({
    ...definition,
    transforms: (definition.transforms || []).map(transform => {
      if (!['FILTER', 'DERIVE'].includes(transform.type) || !('value' in transform)) return transform;
      const type = discovery?.detectedSchema?.[transform.field];
      return { ...transform, value: coerceValue(transform.value, type) };
    }),
  });
  const payload = () => ({ ...pipeline, definition: requestDefinition(), outputSchema: schema });

  const refreshMetadata = async () => {
    const [a, b] = await Promise.all([apiFetch(`/api/pipelines/${id}`), apiFetch(`/api/pipelines/${id}/runs`)]);
    if (a.ok) setPipeline(await a.json());
    if (b.ok) setRuns(await b.json());
  };
  useEffect(() => {
    if (busy !== 'run') return undefined;
    const timer = window.setInterval(refreshMetadata, 1500);
    return () => window.clearInterval(timer);
  }, [busy, id]);
  const saveDraft = async (toast = true) => {
    setError('');
    const response = await apiFetch(`/api/pipelines/${id}`, { method: 'PUT', toast, body: JSON.stringify(payload()) });
    if (!response.ok) throw new Error(await apiError(response));
    const saved = await response.json();
    setPipeline(saved);
    return saved;
  };
  const discover = async (syncOutput = false, page = 0) => {
    setBusy('discover'); setError(''); setPreview(null);
    try {
      const response = await apiFetch(`/api/pipelines/${id}/discover`, {
        method: 'POST', successMessage: 'Đã lấy dữ liệu mẫu thành công.',
        body: JSON.stringify({ definition: requestDefinition(), page, size: 20 }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      const result = await response.json();
      setDiscovery(result);
      if (!schema.length) inferOutput(result);
      else if (syncOutput) {
        const hasAggregate = requestDefinition().transforms.some(transform => ['SUM', 'AVERAGE'].includes(transform.type));
        if (hasAggregate) inferOutput(result);
        else mergeOutput(result);
      }
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const discoverSource = async (index, page = 0) => {
    setBusy(`discover-source-${index}`); setError(''); setPreview(null);
    try {
      const response = await apiFetch(`/api/pipelines/${id}/discover`, {
        method: 'POST', successMessage: `Đã kiểm tra nguồn ${index + 1}.`,
        body: JSON.stringify({ definition: { sources: [definition.sources[index]], joins: [], transforms: [] }, page, size: 20 }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      const result = await response.json();
      setSourceDiscoveries(current => ({ ...current, [index]: result }));
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const inferOutput = result => {
    const used = new Set();
    const inferred = Object.entries(result.detectedSchema || {}).map(([source, type]) => ({
      fieldKey: uniqueKey(source, used), source, type: inferredType(type), required: false,
    }));
    setSchema(inferred);
    const idField = preferredBusinessField(inferred, definition) || inferred.find(field => field.fieldKey === 'id') || inferred[0];
    if (idField && (!pipeline.businessKey || pipeline.businessKey === 'id')) setPipeline(current => ({ ...current, businessKey: idField.fieldKey }));
  };
  const mergeOutput = result => {
    const detected = Object.entries(result.detectedSchema || {});
    const suggestedSource = detected.map(([source]) => source).find(source => /\.id$/i.test(source));
    const existingSuggested = schema.find(field => field.source === suggestedSource);
    if ((definition.joins || []).some(join => join.cardinality === 'ONE_TO_MANY') && pipeline.businessKey === 'id' && suggestedSource) {
      const suggestedKey = existingSuggested?.fieldKey || uniqueKey(suggestedSource, new Set(schema.map(field => field.fieldKey)));
      setPipeline(current => ({ ...current, businessKey: suggestedKey }));
    }
    setSchema(current => {
      const migrated = current.map(field => {
        const source = field.source || field.fieldKey;
        if (result.detectedSchema?.[source]) return field;
        const matches = detected.map(([key]) => key).filter(key => key.endsWith(`.${source}`));
        return matches.length === 1 ? { ...field, source: matches[0] } : field;
      });
      const existingSources = new Set(migrated.map(field => field.source || field.fieldKey));
      const usedKeys = new Set(migrated.map(field => field.fieldKey));
      const added = detected.filter(([source]) => !existingSources.has(source)).map(([source, type]) => ({ fieldKey: uniqueKey(source, usedKeys), source, type: inferredType(type), required: false }));
      return [...migrated, ...added];
    });
  };
  const previewPipeline = async () => {
    setBusy('preview'); setError('');
    try {
      await saveDraft(false);
      const response = await apiFetch(`/api/pipelines/${id}/preview`, { method: 'POST', successMessage: 'Pipeline hợp lệ và đã sẵn sàng để kích hoạt.' });
      if (!response.ok) throw new Error(await apiError(response));
      setPreview(await response.json());
      await refreshMetadata();
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const previewOutput = async (page = 0) => {
    setBusy('preview-output'); setError(''); setPreview(null);
    try {
      const response = await apiFetch(`/api/pipelines/${id}/preview-output`, {
        method: 'POST', successMessage: 'Đã kiểm tra dữ liệu đầu ra.',
        body: JSON.stringify({ definition: requestDefinition(), outputSchema: schema, businessKey: pipeline.businessKey, page, size: 20 }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      setOutputPreview(await response.json());
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const action = async name => {
    setBusy(name); setError('');
    try {
      const response = await apiFetch(`/api/pipelines/${id}/${name}`, {
        method: 'POST', toast: name !== 'run', successMessage: 'Đã kích hoạt pipeline.',
      });
      if (!response.ok) throw new Error(await apiError(response));
      if (name === 'run') {
        const result = await response.json();
        if (result.status === 'PAUSED') {
          window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'info', message: 'Lượt chạy pipeline đã được tạm ngừng.' } }));
        } else if (result.status !== 'SUCCESS' && result.status !== 'NO_CHANGES') {
          const stage = result.errorStage ? ` tại bước ${result.errorStage}` : '';
          const retry = result.status === 'RETRY' ? ' Hệ thống sẽ tự thử lại.' : '';
          throw new Error(`Pipeline chưa chạy xong${stage}: ${result.errorMessage || 'Không xác định được lỗi.'}${retry}`);
        }
        if (result.status !== 'PAUSED') {
          const message = result.status === 'NO_CHANGES'
            ? 'Pipeline đã chạy xong, không có dữ liệu thay đổi.'
            : `Pipeline đã chạy xong: ${result.outputCount || 0} dòng, ${result.changedCount || 0} dòng mới hoặc thay đổi.`;
          window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
        }
      }
      await refreshMetadata();
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const pauseRun = async () => {
    setError('');
    const response = await apiFetch(`/api/pipelines/${id}/pause`, { method: 'POST', toast: false });
    if (!response.ok) setError(await apiError(response, 'Không thể tạm ngừng pipeline.'));
    else window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message: 'Đã tạm ngừng lượt chạy pipeline.' } }));
    await refreshMetadata();
  };
  const resumeRun = async runId => {
    setBusy(`resume-${runId}`); setError('');
    try {
      const response = await apiFetch(`/api/pipelines/${id}/runs/${runId}/resume`, { method: 'POST', toast: false });
      if (!response.ok) throw new Error(await apiError(response, 'Không thể tiếp tục lượt chạy pipeline.'));
      const result = await response.json();
      const message = result.status === 'NO_CHANGES' ? 'Pipeline đã chạy xong và không có dữ liệu thay đổi.' : result.status === 'SUCCESS' ? 'Pipeline đã tiếp tục và chạy xong.' : 'Đã tiếp tục lượt chạy pipeline.';
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
      await refreshMetadata();
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const askDeletePipeline = async () => {
    setError(''); setDeleteState({ pipeline, loading: true, impact: null, confirmation: '', deleting: false });
    const response = await apiFetch(`/api/pipelines/${id}/deletion-impact`, { toast: false });
    if (!response.ok) { setDeleteState(null); return setError(await apiError(response, 'Không thể kiểm tra dữ liệu liên quan đến pipeline.')); }
    const impact = await response.json();
    setDeleteState(current => current ? { ...current, loading: false, impact } : null);
  };
  const deletePipeline = async () => {
    if (!deleteState?.impact?.canDelete || deleteState.confirmation !== pipeline.name) return;
    setDeleteState(current => ({ ...current, deleting: true }));
    const response = await apiFetch(`/api/pipelines/${id}`, { method: 'DELETE', successMessage: `Đã xóa pipeline “${pipeline.name}” cùng dữ liệu liên quan.` });
    if (!response.ok) { setDeleteState(current => ({ ...current, deleting: false })); return setError(await apiError(response)); }
    setDeleteState(null); navigate('/pipelines');
  };

  const addSource = () => {
    setSourceDiscoveries({});
    const type = connectors.some(item => item.connectorType === 'REST') ? 'REST' : 'CSV';
    const number = definition.sources.length + 1;
    setDefinition(current => ({ ...current, sources: [...current.sources, { alias: `source_${number}`, type, recordPath: '', pagination: { type: 'NONE' } }] }));
  };
  const updateSource = (index, values) => { setSourceDiscoveries({}); setDefinition(current => {
    const oldAlias = current.sources[index]?.alias;
    const nextAlias = values.alias;
    return {
      ...current,
      sources: current.sources.map((item, i) => i === index ? { ...item, ...values } : item),
      joins: nextAlias && nextAlias !== oldAlias ? current.joins.map(join => join.rightAlias === oldAlias ? { ...join, rightAlias: nextAlias } : join) : current.joins,
    };
  }); };
  const removeSource = index => { setSourceDiscoveries({}); setDefinition(current => {
    const removed = current.sources[index]?.alias;
    return { ...current, sources: current.sources.filter((_, i) => i !== index), joins: current.joins.filter(join => join.rightAlias !== removed) };
  }); };
  const uploadCsv = async index => {
    const source = definition.sources[index], file = files[index];
    if (!source.alias?.trim()) return setError('Vui lòng nhập tên gợi nhớ trước khi tải file CSV.');
    if (!/^[a-zA-Z][a-zA-Z0-9_]{0,99}$/.test(source.alias.trim())) return setError('Tên gợi nhớ phải bắt đầu bằng chữ và chỉ gồm chữ, số, dấu gạch dưới.');
    if (!file) return setError('Vui lòng chọn một file CSV.');
    if (!file.name.toLowerCase().endsWith('.csv')) return setError('Chỉ chấp nhận file có phần mở rộng .csv.');
    setBusy(`upload-${index}`); setError('');
    try {
      const body = new FormData(); body.append('file', file);
      const response = await apiFetch(`/api/pipelines/${id}/files?alias=${encodeURIComponent(source.alias)}`, { method: 'POST', body });
      if (!response.ok) throw new Error(await apiError(response));
      const uploaded = await response.json();
      updateSource(index, { fileVersionId: uploaded.id, fileName: uploaded.fileName, delimiter: source.delimiter || 'AUTO' });
    } catch (reason) { setError(reason.message); } finally { setBusy(''); }
  };
  const addJoin = () => setDefinition(current => ({ ...current, joins: [...current.joins, { rightAlias: current.sources[1]?.alias || '', type: 'INNER', leftKeys: [], rightKeys: [], cardinality: 'ONE_TO_ONE' }] }));
  const updateJoin = (index, values) => setDefinition(current => ({ ...current, joins: current.joins.map((item, i) => i === index ? { ...item, ...values } : item) }));
  const removeJoin = index => setDefinition(current => ({ ...current, joins: current.joins.filter((_, i) => i !== index) }));
  const addTransform = type => {
    const field = availableFields[0] || '';
    const safeField = field.replace(/[^a-zA-Z0-9_]/g, '_') || 'value';
    const base = type === 'FILTER'
      ? { type, field, operator: 'EQUALS', value: '' }
      : type === 'CAST'
        ? { type, field, targetType: 'STRING' }
        : ['SUM', 'AVERAGE'].includes(type)
          ? { type, field, groupBy: [], target: `${type === 'SUM' ? 'total' : 'average'}_${safeField}` }
          : { type, field };
    setDefinition(current => ({ ...current, transforms: [...current.transforms, base] }));
  };
  const updateTransform = (index, values) => setDefinition(current => ({ ...current, transforms: current.transforms.map((item, i) => i === index ? { ...item, ...values } : item) }));
  const removeTransform = index => setDefinition(current => ({ ...current, transforms: current.transforms.filter((_, i) => i !== index) }));
  const updateSchema = (index, values) => {
    setOutputPreview(null);
    const oldKey = schema[index]?.fieldKey;
    if (Object.prototype.hasOwnProperty.call(values, 'fieldKey') && oldKey !== values.fieldKey) {
      setPipeline(current => ({ ...current, businessKey: splitKeys(current.businessKey).map(key => key === oldKey ? values.fieldKey : key).filter(Boolean).join(',') }));
    }
    setSchema(current => current.map((item, i) => i === index ? { ...item, ...values } : item));
  };
  const removeSchema = index => {
    setOutputPreview(null);
    const removedKey = schema[index]?.fieldKey;
    setSchema(current => current.filter((_, i) => i !== index));
    setPipeline(current => ({ ...current, businessKey: splitKeys(current.businessKey).filter(key => key !== removedKey).join(',') }));
  };
  const toggleBusinessKey = key => {
    setOutputPreview(null);
    const keys = splitKeys(pipeline.businessKey);
    const next = keys.includes(key) ? keys.filter(item => item !== key) : [...keys, key];
    setPipeline(current => ({ ...current, businessKey: next.join(',') }));
  };
  const openAdvanced = () => { setAdvancedDefinition(pretty(requestDefinition())); setAdvancedSchema(pretty(schema)); setMode('ADVANCED'); };
  const applyAdvanced = () => {
    try {
      const nextDefinition = JSON.parse(advancedDefinition), nextSchema = JSON.parse(advancedSchema);
      if (!Array.isArray(nextDefinition.sources) || !Array.isArray(nextSchema)) throw new Error('Cấu trúc JSON không hợp lệ.');
      setDefinition({ ...emptyDefinition, ...nextDefinition }); setSchema(nextSchema); setMode('GUIDED'); setError('');
    } catch (reason) { setError(`JSON không hợp lệ: ${reason.message}`); }
  };

  if (!pipeline) return <p className="p-6 text-sm text-gray-500">Đang tải Pipeline Designer...</p>;
  const isBusy = Boolean(busy);
  return <div className="space-y-5 pb-10">
    <header className="flex flex-wrap items-start justify-between gap-4 rounded-xl border bg-white p-5">
      <div className="min-w-[280px] flex-1"><div className="flex items-center gap-3"><input aria-label="Tên pipeline" className="input-field max-w-xl text-xl font-bold text-slate-900" value={pipeline.name || ''} onChange={event => setPipeline(current => ({ ...current, name: event.target.value }))}/><StatusBadge status={pipeline.status} /></div><textarea aria-label="Mô tả pipeline" className="input-field mt-3 min-h-16 max-w-3xl" placeholder="Mô tả mục đích, nguồn và dữ liệu đầu ra của pipeline" value={pipeline.description || ''} onChange={event => setPipeline(current => ({ ...current, description: event.target.value }))}/><p className="mt-2 text-xs text-gray-500">Dataset v{pipeline.latestVersion || 0} · {friendlySchedule(pipeline)}</p></div>
      <div className="flex flex-wrap gap-2">
        <button onClick={() => saveDraft().catch(reason => setError(reason.message))} disabled={isBusy} className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold disabled:opacity-50">Lưu bản nháp</button>
        <button onClick={previewPipeline} disabled={isBusy} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{busy === 'preview' ? 'Đang kiểm tra...' : 'Kiểm tra pipeline'}</button>
        <button onClick={() => action('publish')} disabled={isBusy || pipeline.status !== 'PREVIEWED'} className="btn-primary disabled:opacity-40">Kích hoạt</button>
        {busy === 'run' || ['QUEUED', 'RUNNING', 'RETRY'].includes(pipeline.activeRun?.status)
          ? <button onClick={pauseRun} className="flex items-center gap-1 rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-700"><PauseCircle size={15}/>Tạm ngừng</button>
          : <button onClick={() => action('run')} disabled={isBusy || pipeline.status !== 'PUBLISHED' || pipeline.activeRun?.status === 'PAUSED'} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">Chạy ngay</button>}
        <button onClick={askDeletePipeline} disabled={isBusy} className="flex items-center gap-1 rounded-lg border border-red-200 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-40"><Trash2 size={15}/>Xóa</button>
      </div>
    </header>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
    <div className="flex justify-end rounded-xl border bg-white p-1">
      <button onClick={() => setMode('GUIDED')} className={`rounded-lg px-4 py-2 text-xs font-bold ${mode === 'GUIDED' ? 'bg-orange-50 text-orange-600' : 'text-gray-500'}`}>Hướng dẫn từng bước</button>
      <button onClick={openAdvanced} className={`flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-bold ${mode === 'ADVANCED' ? 'bg-slate-900 text-white' : 'text-gray-500'}`}><Braces size={14} />Nâng cao (JSON)</button>
    </div>
    {mode === 'ADVANCED' ? <AdvancedEditor definition={advancedDefinition} schema={advancedSchema} setDefinition={setAdvancedDefinition} setSchema={setAdvancedSchema} onApply={applyAdvanced} /> : <>
      <StepNavigation step={step} onChange={setStep} />
      {step === 0 && <SourcesStep sources={definition.sources} connectors={connectors} files={files} busy={busy} previews={sourceDiscoveries} onFiles={setFiles} onAdd={addSource} onUpdate={updateSource} onRemove={removeSource} onUpload={uploadCsv} onDiscover={discoverSource} />}
      {step === 1 && <TransformStep definition={definition} discovery={discovery} busy={busy} onDiscover={() => discover(true, 0)} onAddJoin={addJoin} onUpdateJoin={updateJoin} onRemoveJoin={removeJoin} />}
      {step === 2 && <OutputStep definition={definition} fields={availableFields} schema={schema} businessKey={pipeline.businessKey} discovery={discovery} busy={busy} hasOneToMany={(definition.joins || []).some(join => join.cardinality === 'ONE_TO_MANY')} onAddTransform={addTransform} onUpdateTransform={updateTransform} onRemoveTransform={removeTransform} onUseSuggested={key => { setOutputPreview(null); setPipeline(current => ({ ...current, businessKey: key })); }} onPreview={() => previewOutput(0)} onInfer={() => discovery && inferOutput(discovery)} onUpdate={updateSchema} onRemove={removeSchema} onAdd={() => { setOutputPreview(null); setSchema(current => [...current, { fieldKey: '', source: '', type: 'STRING', required: false }]); }} onToggleKey={toggleBusinessKey} />}
      {step === 3 && <ScheduleStep pipeline={pipeline} onChange={values => setPipeline(current => ({ ...current, ...values, timezone: 'Asia/Ho_Chi_Minh' }))} />}
      <div className="flex items-center justify-between"><button disabled={step === 0} onClick={() => setStep(value => value - 1)} className="flex items-center gap-1 rounded-lg border bg-white px-4 py-2 text-sm font-semibold disabled:opacity-40"><ChevronLeft size={16} />Quay lại</button>{step < STEPS.length - 1 ? <button onClick={() => setStep(value => value + 1)} className="flex items-center gap-1 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white">Tiếp tục<ChevronRight size={16} /></button> : <button onClick={previewPipeline} disabled={isBusy} className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"><Eye size={16} />Kiểm tra pipeline</button>}</div>
    </>}
    {(outputPreview || preview || (step === 1 && discovery)) && <DataPreview title={outputPreview ? 'Dữ liệu đầu ra sau khi áp dụng cấu hình' : preview ? 'Kết quả kiểm tra chính thức' : 'Kết quả sau khi ghép và xử lý'} result={outputPreview || preview || discovery} onPage={outputPreview ? page => previewOutput(page) : preview ? null : page => discover(false, page)} />}
    <RunHistory runs={runs} busy={busy} onPause={pauseRun} onResume={resumeRun} />
    {deleteState && <DeletePipelineModal state={deleteState} setState={setDeleteState} onConfirm={deletePipeline}/>}
  </div>;
}

function StepNavigation({ step, onChange }) {
  return <div className="grid gap-2 rounded-xl border bg-white p-3 sm:grid-cols-4">{STEPS.map((item, index) => {
    const Icon = item.icon, active = step === index, done = step > index;
    return <button key={item.label} onClick={() => onChange(index)} className={`flex items-center gap-3 rounded-lg p-3 text-left ${active ? 'bg-orange-50 text-orange-700' : 'text-gray-500 hover:bg-slate-50'}`}><span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${active ? 'bg-orange-500 text-white' : done ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100'}`}>{done ? <Check size={16} /> : <Icon size={16} />}</span><span><small className="block text-[10px] font-bold uppercase">Bước {index + 1}</small><b className="text-xs">{item.label}</b></span></button>;
  })}</div>;
}

function SourcesStep({ sources, connectors, files, busy, previews, onFiles, onAdd, onUpdate, onRemove, onUpload, onDiscover }) {
  return <section className="rounded-xl border bg-white p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-bold text-slate-800">Bạn muốn lấy dữ liệu từ đâu?</h2><p className="mt-1 text-xs text-gray-500">Chọn kết nối có sẵn hoặc tải file CSV. Không cần sao chép connector ID.</p></div><button onClick={onAdd} className="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-bold"><Plus size={15} />Thêm nguồn</button></div>
    {!sources.length && <Empty text="Chưa có nguồn dữ liệu. Bấm “Thêm nguồn” để bắt đầu." />}
    <div className="mt-4 space-y-4">{sources.map((source, index) => {
      const matching = connectors.filter(item => item.connectorType === source.type);
      return <div key={index} className="rounded-xl border border-slate-200 p-4"><div className="mb-3 flex items-center justify-between"><b className="text-sm">Nguồn {index + 1}</b><button onClick={() => onRemove(index)} className="text-gray-400 hover:text-red-500"><Trash2 size={16} /></button></div><div className="grid gap-3 md:grid-cols-2">
        <Field label="Tên gợi nhớ"><input className="input-field" value={source.alias || ''} onChange={e => onUpdate(index, { alias: e.target.value })} placeholder="Ví dụ: employees" /></Field>
        <Field label="Loại nguồn"><select className="input-field" value={source.type || 'REST'} onChange={e => onUpdate(index, { type: e.target.value, connectorId: undefined })}><option value="REST">REST API</option><option value="POSTGRESQL">PostgreSQL</option><option value="CSV">File CSV</option></select></Field>
        {source.type !== 'CSV' && <Field label="Kết nối"><select className="input-field" value={source.connectorId || ''} onChange={e => onUpdate(index, { connectorId: e.target.value })}><option value="">-- Chọn connector --</option>{matching.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{!matching.length && <Hint>Chưa có connector {source.type}. Hãy tạo ở màn hình Connectors.</Hint>}</Field>}
        {source.type === 'REST' && <>
          <Field label="Danh sách nằm trong trường"><input className="input-field" value={source.recordPath || ''} onChange={e => onUpdate(index, { recordPath: e.target.value })} placeholder="Có thể để trống để hệ thống tự nhận diện" /><Hint>Ví dụ: data hoặc result.items. Nếu response chỉ có một mảng, hệ thống sẽ tự tìm.</Hint></Field>
          <Field label="Phân trang"><select className="input-field" value={source.pagination?.type || 'NONE'} onChange={e => onUpdate(index, { pagination: paginationConfig(e.target.value, source.pagination) })}><option value="NONE">Không phân trang</option><option value="PAGE">Theo số trang</option><option value="OFFSET">Theo offset</option><option value="CURSOR">Theo cursor</option></select></Field>
          {source.pagination?.type !== 'NONE' && <Field label="Số dòng mỗi trang"><input type="number" min="1" className="input-field" value={source.pagination?.pageSize || 100} onChange={e => onUpdate(index, { pagination: { ...source.pagination, pageSize: Number(e.target.value) } })} /></Field>}
          {source.pagination?.type === 'PAGE' && <><Field label="Tên tham số số trang"><input className="input-field" value={source.pagination?.pageParam || 'page'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, pageParam: e.target.value } })} placeholder="page" /><Hint>JSONPlaceholder sử dụng _page</Hint></Field><Field label="Tên tham số kích thước trang"><input className="input-field" value={source.pagination?.sizeParam || 'size'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, sizeParam: e.target.value } })} placeholder="size" /><Hint>JSONPlaceholder sử dụng _limit</Hint></Field><Field label="Trang bắt đầu"><input type="number" min="0" className="input-field" value={source.pagination?.startPage ?? 1} onChange={e => onUpdate(index, { pagination: { ...source.pagination, startPage: Number(e.target.value) } })} /></Field></>}
          {source.pagination?.type === 'OFFSET' && <><Field label="Tên tham số offset"><input className="input-field" value={source.pagination?.offsetParam || 'offset'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, offsetParam: e.target.value } })} /></Field><Field label="Tên tham số giới hạn"><input className="input-field" value={source.pagination?.limitParam || 'limit'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, limitParam: e.target.value } })} /></Field></>}
          {source.pagination?.type === 'CURSOR' && <><Field label="Tên tham số cursor"><input className="input-field" value={source.pagination?.cursorParam || 'cursor'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, cursorParam: e.target.value } })} /></Field><Field label="Đường dẫn cursor tiếp theo"><input className="input-field" value={source.pagination?.nextCursorPath || 'nextCursor'} onChange={e => onUpdate(index, { pagination: { ...source.pagination, nextCursorPath: e.target.value } })} placeholder="meta.nextCursor" /></Field></>}
        </>}
        {source.type === 'POSTGRESQL' && <Field label="Câu truy vấn SELECT" wide><textarea className="input-field min-h-28 font-mono text-xs" value={source.query || ''} onChange={e => onUpdate(index, { query: e.target.value })} placeholder="SELECT employee_code, full_name FROM employees" /><Hint>Chỉ chấp nhận một câu SELECT, không thêm dấu chấm phẩy cuối câu.</Hint></Field>}
        {source.type === 'CSV' && <Field label="File CSV" wide><div className="flex flex-wrap items-center gap-2"><input type="file" accept=".csv,text/csv" onChange={e => onFiles(current => ({ ...current, [index]: e.target.files[0] }))} /><button onClick={() => onUpload(index)} disabled={busy === `upload-${index}`} className="rounded-lg border px-3 py-2 text-xs font-bold disabled:opacity-50">{busy === `upload-${index}` ? 'Đang tải...' : 'Tải file lên'}</button></div>{source.fileVersionId && <p className="mt-2 text-xs font-semibold text-emerald-600">✓ Đã chọn phiên bản: {source.fileName || source.fileVersionId}</p>}</Field>}
      </div><button onClick={() => onDiscover(index, 0)} disabled={Boolean(busy)} className="mt-4 flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50"><RefreshCw size={16} className={busy === `discover-source-${index}` ? 'animate-spin' : ''}/>{busy === `discover-source-${index}` ? 'Đang kiểm tra nguồn...' : `Kiểm tra nguồn ${index + 1}`}</button>{previews[index] && <div className="mt-4"><DataPreview title={`Dữ liệu mẫu nguồn ${index + 1}: ${source.alias || 'Chưa đặt tên'}`} result={previews[index]} onPage={page => onDiscover(index, page)}/></div>}</div>;
    })}</div>
  </section>;
}

function TransformStep({ definition, discovery, busy, onDiscover, onAddJoin, onUpdateJoin, onRemoveJoin }) {
  return <div className="space-y-4"><section className="rounded-xl border bg-white p-5"><div className="flex items-start justify-between"><div><h2 className="font-bold">Ghép nhiều nguồn</h2><p className="mt-1 text-xs text-gray-500">Ghép nguồn đầu tiên với các nguồn tiếp theo bằng những cột tương ứng.</p></div><button disabled={definition.sources.length < 2} onClick={onAddJoin} className="rounded-lg border px-3 py-2 text-xs font-bold disabled:opacity-40">+ Thêm phép ghép</button></div>
    {!definition.joins.length && <Empty text={definition.sources.length < 2 ? 'Bạn chỉ có một nguồn nên không cần ghép.' : 'Chưa cấu hình phép ghép dữ liệu.'} />}<div className="mt-4 space-y-3">{definition.joins.map((join, index) => <div key={index} className="grid gap-3 rounded-lg border p-4 md:grid-cols-2">
      <Field label="Nguồn cần ghép"><select className="input-field" value={join.rightAlias || ''} onChange={e => onUpdateJoin(index, { rightAlias: e.target.value })}>{definition.sources.slice(1).map(source => <option key={source.alias} value={source.alias}>{source.alias}</option>)}</select></Field>
      <Field label="Cách giữ lại các dòng"><select className="input-field" value={join.type || 'INNER'} onChange={e => onUpdateJoin(index, { type: e.target.value })}><option value="INNER">Chỉ dòng khớp ở cả hai nguồn (INNER)</option><option value="LEFT">Tất cả dòng nguồn chính (LEFT)</option><option value="FULL">Tất cả dòng của cả hai nguồn (FULL)</option></select></Field>
      <Field label="Cột nguồn chính" ><input className="input-field" value={(join.leftKeys || []).join(', ')} onChange={e => onUpdateJoin(index, { leftKeys: splitKeys(e.target.value) })} placeholder="Tên cột nguồn chính..." /></Field><Field label="Cột nguồn được ghép"><input className="input-field" value={(join.rightKeys || []).join(', ')} onChange={e => onUpdateJoin(index, { rightKeys: splitKeys(e.target.value) })} placeholder="Tên cột nguồn muốn ghép..." /></Field>
      <Field label="Quan hệ dữ liệu"><select className="input-field" value={join.cardinality || 'ONE_TO_ONE'} onChange={e => onUpdateJoin(index, { cardinality: e.target.value })}><option value="ONE_TO_ONE">Một - một</option><option value="ONE_TO_MANY">Một - nhiều</option><option value="MANY_TO_ONE">Nhiều - một</option></select></Field><div className="flex items-end justify-end"><button onClick={() => onRemoveJoin(index)} className="flex items-center gap-1 px-2 py-2 text-xs font-bold text-red-500"><Trash2 size={14} />Xóa</button></div>
      <JoinModeHelp type={join.type || 'INNER'} mainAlias={definition.sources[0]?.alias || 'nguồn đầu tiên'} rightAlias={join.rightAlias || 'nguồn cần ghép'}/>
    </div>)}</div></section>
    <section className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50/40 p-5"><div><h3 className="text-sm font-bold text-slate-800">Xem kết quả sau khi ghép và xử lý</h3><p className="mt-1 text-xs text-gray-500">Chạy lại toàn bộ nguồn, Join và Transform; các cột mới sẽ được bổ sung vào bước Dữ liệu đầu ra.</p></div><button onClick={onDiscover} disabled={busy === 'discover'} className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50"><RefreshCw size={16} className={busy === 'discover' ? 'animate-spin' : ''} />{busy === 'discover' ? 'Đang kiểm tra...' : 'Kiểm tra dữ liệu sau khi ghép & xử lý'}</button></section>
  </div>;
}

function DataTransforms({ definition, fields, discovery, onAddTransform, onUpdateTransform, onRemoveTransform }) {
  return <section className="rounded-xl border bg-white p-5">
    <div><h2 className="font-bold">Lọc, chuẩn hóa và tổng hợp</h2><p className="mt-1 text-xs text-gray-500">Các thao tác được áp dụng lần lượt từ trên xuống dưới, trước khi dữ liệu được đưa vào Dataset.</p></div>
    <div className="mt-3 flex flex-wrap gap-2">
      <button onClick={() => onAddTransform('FILTER')} className="flex items-center gap-1 rounded-lg border px-3 py-2 text-xs font-bold"><Filter size={14} />Thêm điều kiện lọc</button>
      <button onClick={() => onAddTransform('CAST')} className="rounded-lg border px-3 py-2 text-xs font-bold">Đổi kiểu dữ liệu</button>
      <button onClick={() => onAddTransform('TRIM')} className="rounded-lg border px-3 py-2 text-xs font-bold">Xóa khoảng trắng</button>
      <button onClick={() => onAddTransform('SUM')} className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700">Tính tổng</button>
      <button onClick={() => onAddTransform('AVERAGE')} className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-bold text-blue-700">Tính trung bình</button>
    </div>
    {!definition.transforms.length && <Empty text="Không có bước xử lý; dữ liệu sẽ được giữ nguyên." />}
    <div className="mt-4 space-y-3">{definition.transforms.map((transform, index) => {
      const aggregate = ['SUM', 'AVERAGE'].includes(transform.type);
      return <div key={index} className={`flex flex-wrap items-end gap-3 rounded-lg border p-3 ${aggregate ? 'border-blue-200 bg-blue-50/20' : ''}`}>
        <span className="mb-2 rounded bg-slate-100 px-2 py-1 text-[10px] font-bold">{transformName(transform.type)}</span>
        <Field label={aggregate ? 'Cột số cần tính' : 'Cột'}><FieldSelect fields={fields} value={transform.field || ''} onChange={value => onUpdateTransform(index, { field: value })} /></Field>
        {transform.type === 'FILTER' && <><Field label="Điều kiện"><select className="input-field" value={transform.operator || 'EQUALS'} onChange={event => onUpdateTransform(index, { operator: event.target.value })}>{operators.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>{!['IS_EMPTY', 'NOT_EMPTY'].includes(transform.operator) && <Field label="Giá trị"><input className="input-field" value={transform.value ?? ''} onChange={event => onUpdateTransform(index, { value: event.target.value })} placeholder="Giá trị so sánh" /></Field>}</>}
        {transform.type === 'CAST' && <Field label="Kiểu mới"><select className="input-field" value={transform.targetType || 'STRING'} onChange={event => onUpdateTransform(index, { targetType: event.target.value })}>{outputTypes.map(type => <option key={type}>{type}</option>)}</select></Field>}
        {aggregate && <>
          <Field label="Nhóm theo cột (tùy chọn)"><FieldSelect fields={fields.filter(field => field !== transform.field)} value={transform.groupBy?.[0] || ''} onChange={value => onUpdateTransform(index, { groupBy: value ? [value] : [] })} /></Field>
          <Field label="Tên cột kết quả"><input className="input-field min-w-44" value={transform.target || ''} onChange={event => onUpdateTransform(index, { target: event.target.value })} placeholder={transform.type === 'SUM' ? 'total_amount' : 'average_amount'} /></Field>
          <p className="mb-2 max-w-sm text-[10px] leading-4 text-blue-700">{transform.groupBy?.length ? 'Mỗi giá trị nhóm tạo một dòng kết quả.' : 'Không chọn nhóm sẽ tạo một dòng tổng hợp với khóa _aggregateKey = ALL.'}</p>
        </>}
        <button onClick={() => onRemoveTransform(index)} className="mb-2 ml-auto text-red-500" title="Xóa xử lý"><Trash2 size={16} /></button>
      </div>;
    })}</div>
    {!discovery && <Hint>Hãy lấy dữ liệu mẫu để danh sách cột được tự động hiển thị.</Hint>}
    {definition.transforms.some(transform => ['SUM', 'AVERAGE'].includes(transform.type)) && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs leading-5 text-amber-800"><b>Lưu ý:</b> phép tổng hợp làm giảm dữ liệu thành một dòng cho mỗi nhóm. Hãy đặt phép tổng hợp sau các bước lọc/chuẩn hóa và bấm “Kiểm tra dữ liệu sau khi ghép & xử lý” để đồng bộ Output Schema.</p>}
  </section>;
}

function OutputStep({ definition, fields, schema, businessKey, discovery, busy, hasOneToMany, onAddTransform, onUpdateTransform, onRemoveTransform, onUseSuggested, onPreview, onInfer, onUpdate, onRemove, onAdd, onToggleKey }) {
  const keys = splitKeys(businessKey);
  const suggestedField = preferredBusinessField(schema, { joins: hasOneToMany ? [{ cardinality: 'ONE_TO_MANY' }] : [] });
  return <div className="space-y-4"><DataTransforms definition={definition} fields={fields} discovery={discovery} onAddTransform={onAddTransform} onUpdateTransform={onUpdateTransform} onRemoveTransform={onRemoveTransform} /><section className="rounded-xl border bg-white p-5"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-bold">Chọn dữ liệu đầu ra</h2><p className="mt-1 text-xs text-gray-500">Đổi tên cột và chọn cột định danh duy nhất cho mỗi dòng.</p></div><div className="flex flex-wrap gap-2"><button disabled={!schema.length || !keys.length || Boolean(busy)} onClick={onPreview} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white disabled:opacity-40">{busy === 'preview-output' ? 'Đang kiểm tra...' : 'Kiểm tra dữ liệu đầu ra'}</button><button disabled={!discovery} onClick={onInfer} className="rounded-lg border px-3 py-2 text-xs font-bold disabled:opacity-40">Tạo lại từ dữ liệu mẫu</button><button onClick={onAdd} className="rounded-lg border px-3 py-2 text-xs font-bold">+ Thêm cột</button></div></div>
    {!schema.length ? <Empty text="Chưa có cột đầu ra. Quay lại bước 1 và lấy dữ liệu mẫu để hệ thống tự tạo." /> : <div className="mt-4 overflow-auto"><table className="min-w-full text-left text-xs"><thead className="bg-slate-50 text-gray-500"><tr><th className="p-3">Cột nguồn</th><th className="p-3">Tên đầu ra</th><th className="p-3">Kiểu</th><th className="p-3 text-center">Bắt buộc</th><th className="p-3 text-center">Khóa duy nhất</th><th /></tr></thead><tbody>{schema.map((field, index) => <tr key={index} className="border-t"><td className="p-2"><input className="input-field min-w-36" value={field.source || ''} onChange={e => onUpdate(index, { source: e.target.value })} /></td><td className="p-2"><input className="input-field min-w-36" value={field.fieldKey || ''} onChange={e => onUpdate(index, { fieldKey: e.target.value })} /></td><td className="p-2"><select className="input-field" value={field.type || 'STRING'} onChange={e => onUpdate(index, { type: e.target.value })}>{outputTypes.map(type => <option key={type}>{type}</option>)}</select></td><td className="p-2 text-center"><input type="checkbox" checked={Boolean(field.required)} onChange={e => onUpdate(index, { required: e.target.checked })} /></td><td className="p-2 text-center"><input type="checkbox" checked={keys.includes(field.fieldKey)} onChange={() => onToggleKey(field.fieldKey)} disabled={!field.fieldKey} /></td><td className="p-2"><button onClick={() => onRemove(index)} className="text-gray-400 hover:text-red-500"><Trash2 size={15} /></button></td></tr>)}</tbody></table></div>}
    <div className={`mt-4 rounded-lg p-3 text-xs ${keys.length ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>{keys.length ? <>Khóa định danh: <b>{keys.join(' + ')}</b></> : 'Vui lòng chọn ít nhất một khóa duy nhất.'}</div>
    {hasOneToMany && suggestedField && !keys.includes(suggestedField.fieldKey) && <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs text-blue-800"><span>Khóa <b>{suggestedField.fieldKey}</b> lấy từ <code>{suggestedField.source}</code> phù hợp hơn cho Join một–nhiều.</span><button onClick={() => onUseSuggested(suggestedField.fieldKey)} className="rounded-lg bg-blue-600 px-3 py-2 font-bold text-white">Dùng khóa gợi ý</button></div>}
    <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs leading-5 text-amber-800"><b>Lưu ý khi ghép một–nhiều:</b> khóa <code>id</code> của nguồn chính sẽ lặp lại theo mỗi dòng nguồn phụ. Hãy chọn khóa của nguồn phụ, ví dụ <code>todos.id</code>, hoặc chọn nhiều khóa kết hợp trước khi kiểm tra dữ liệu đầu ra.</p>
  </section></div>;
}

function ScheduleStep({ pipeline, onChange }) {
  return <section className="rounded-xl border bg-white p-5"><h2 className="font-bold">Khi nào cần lấy dữ liệu?</h2><p className="mt-1 text-xs text-gray-500">Mọi lịch chạy được tính theo giờ Việt Nam (GMT+7).</p><div className="mt-4 grid gap-3 md:grid-cols-3">{[['MANUAL', 'Chạy thủ công', 'Chỉ chạy khi bạn bấm “Chạy ngay”.'], ['ONCE', 'Chạy một lần', 'Tự động chạy tại thời điểm đã chọn theo giờ Việt Nam.'], ['DAILY', 'Chạy hằng ngày', 'Lặp lại mỗi ngày theo giờ Việt Nam đã chọn.']].map(([value, title, detail]) => <button key={value} onClick={() => onChange({ scheduleType: value, timezone: 'Asia/Ho_Chi_Minh' })} className={`rounded-xl border p-4 text-left ${pipeline.scheduleType === value ? 'border-orange-500 bg-orange-50' : 'hover:bg-slate-50'}`}><b className="text-sm">{title}</b><p className="mt-1 text-xs text-gray-500">{detail}</p></button>)}</div><div className="mt-5 grid gap-3 md:grid-cols-2"><div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3"><p className="text-xs font-bold text-emerald-800">Múi giờ cố định</p><p className="mt-1 text-sm font-semibold text-emerald-900">Việt Nam — GMT+7</p><code className="text-xs text-emerald-700">Asia/Ho_Chi_Minh</code></div>{pipeline.scheduleType === 'ONCE' && <Field label="Thời điểm chạy (giờ Việt Nam)"><input type="datetime-local" className="input-field" value={pipeline.scheduledAt || ''} onChange={e => onChange({ scheduledAt: e.target.value })} /></Field>}{pipeline.scheduleType === 'DAILY' && <Field label="Giờ chạy mỗi ngày (giờ Việt Nam)"><input type="time" className="input-field" value={pipeline.dailyTime || ''} onChange={e => onChange({ dailyTime: e.target.value })} /></Field>}</div>{pipeline.scheduleType === 'MANUAL' && <p className="mt-3 text-xs text-gray-500">Chế độ thủ công không dùng giờ lịch; pipeline chỉ chạy khi bạn bấm “Chạy ngay”.</p>}</section>;
}

function AdvancedEditor({ definition, schema, setDefinition, setSchema, onApply }) {
  return <section className="rounded-xl border bg-white p-5"><div className="mb-4"><h2 className="font-bold">Cấu hình JSON nâng cao</h2><p className="mt-1 text-xs text-gray-500">Dành cho người phát triển hoặc các cấu hình chưa có trong trình hướng dẫn.</p></div><div className="grid gap-4 lg:grid-cols-2"><Field label="Sources · Joins · Transforms"><textarea className="h-[430px] w-full rounded-lg border bg-slate-950 p-3 font-mono text-xs text-emerald-200" value={definition} onChange={e => setDefinition(e.target.value)} /></Field><Field label="Output schema"><textarea className="h-[430px] w-full rounded-lg border bg-slate-950 p-3 font-mono text-xs text-amber-100" value={schema} onChange={e => setSchema(e.target.value)} /></Field></div><button onClick={onApply} className="mt-4 rounded-lg bg-slate-900 px-4 py-2 text-sm font-bold text-white">Áp dụng JSON và quay lại hướng dẫn</button></section>;
}

function JoinModeHelp({ type, mainAlias, rightAlias }) {
  const detail = type === 'LEFT'
    ? `Giữ mọi dòng của “${mainAlias}”. Dòng không có dữ liệu tương ứng trong “${rightAlias}” vẫn xuất hiện và các cột ${rightAlias}.* sẽ để trống.`
    : type === 'FULL'
      ? `Giữ mọi dòng của cả “${mainAlias}” và “${rightAlias}”. Dòng chỉ tồn tại ở một phía vẫn xuất hiện; các cột của phía còn lại sẽ để trống.`
      : `Chỉ giữ những dòng có khóa tồn tại đồng thời ở “${mainAlias}” và “${rightAlias}”. Dòng không khớp ở bất kỳ phía nào sẽ bị loại.`;
  return <div className="rounded-lg border border-blue-100 bg-blue-50 p-3 text-xs leading-5 text-blue-800 md:col-span-2"><b>{type}:</b> {detail}<p className="mt-1 text-blue-600">Tất cả cột của nguồn được ghép có tiền tố <b>{rightAlias}.</b>, ví dụ: <b>{rightAlias}.title</b>, để không nhầm với cột nguồn chính.</p></div>;
}

function DataPreview({ title, result, onPage }) {
  const rows = result.sample || [], columns = [...new Set(rows.flatMap(row => Object.keys(row)))].sort((left, right) => Number(right.includes('.')) - Number(left.includes('.')));
  const schemaEntries = Object.entries(result.detectedSchema || {}).sort(([left], [right]) => Number(right.includes('.')) - Number(left.includes('.')));
  const page = result.page || 0, totalPages = result.totalPages ?? (rows.length ? 1 : 0), firstRow = result.count ? page * (result.size || rows.length) + 1 : 0;
  return <section className="rounded-xl border bg-white p-5"><div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="font-bold">{title}</h2><p className="text-xs text-gray-500">{result.count || 0} dòng{rows.length ? ` · đang xem ${firstRow}–${Math.min(firstRow + rows.length - 1, result.count || 0)}` : ''}</p></div><div className="flex flex-wrap gap-2">{schemaEntries.slice(0, 12).map(([key, type]) => <span key={key} className={`rounded-full px-2 py-1 text-[10px] ${key.includes('.') ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-600'}`}>{key}: {type}</span>)}</div></div>{!rows.length ? <Empty text="Nguồn dữ liệu không trả về dòng nào." /> : <div className="mt-4 max-h-96 overflow-auto rounded-lg border"><table className="min-w-full whitespace-nowrap text-left text-xs"><thead className="sticky top-0 bg-slate-50"><tr>{columns.map(column => <th key={column} className={`p-3 ${column.includes('.') ? 'bg-blue-50 text-blue-700' : 'text-gray-500'}`}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={index} className="border-t">{columns.map(column => <td key={column} className={`max-w-64 truncate p-3 text-slate-700 ${column.includes('.') ? 'bg-blue-50/30' : ''}`}>{displayValue(row[column])}</td>)}</tr>)}</tbody></table></div>}{onPage && totalPages > 1 && <div className="mt-4 flex items-center justify-between"><button disabled={!result.hasPrevious} onClick={() => onPage(page - 1)} className="flex items-center gap-1 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40"><ChevronLeft size={14}/>Trang trước</button><span className="text-xs text-gray-500">Trang {page + 1}/{totalPages}</span><button disabled={!result.hasNext} onClick={() => onPage(page + 1)} className="flex items-center gap-1 rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-40">Trang sau<ChevronRight size={14}/></button></div>}</section>;
}

function RunHistory({ runs, busy, onPause, onResume }) {
  return <section className="rounded-xl border bg-white p-5"><h2 className="mb-3 font-bold">Lịch sử chạy</h2>{!runs.length ? <p className="text-xs text-gray-400">Pipeline chưa được chạy lần nào.</p> : <div className="overflow-auto"><table className="min-w-full text-left text-sm"><thead><tr className="text-xs text-gray-500"><th className="p-2">Trạng thái</th><th>Kiểu chạy</th><th>Đầu vào</th><th>Đầu ra</th><th>Thay đổi</th><th>Lần thử</th><th>Lỗi</th><th>Thao tác</th></tr></thead><tbody>{runs.map(run => <tr key={run.id} className="border-t"><td className={`p-2 font-semibold ${run.status === 'PAUSED' ? 'text-amber-600' : ''}`}>{run.status === 'PAUSED' ? 'TẠM NGỪNG' : run.status}</td><td>{run.triggerType}</td><td>{run.inputCount ?? '—'}</td><td>{run.outputCount ?? '—'}</td><td>{run.changedCount ?? '—'}</td><td>{run.attemptCount}</td><td className="max-w-72 text-xs text-red-500">{run.errorMessage || '—'}</td><td>{['QUEUED','RUNNING','RETRY'].includes(run.status) ? <button type="button" onClick={onPause} className="flex items-center gap-1 text-xs font-semibold text-amber-600"><PauseCircle size={13}/>Tạm ngừng</button> : run.status === 'PAUSED' ? <button type="button" disabled={Boolean(busy)} onClick={()=>onResume(run.id)} className="flex items-center gap-1 text-xs font-semibold text-emerald-600 disabled:opacity-40"><RotateCw size={13}/>Tiếp tục</button> : '—'}</td></tr>)}</tbody></table></div>}</section>;
}

function Field({ label, children, wide = false }) { return <label className={`block text-xs font-bold text-gray-600 ${wide ? 'md:col-span-2' : ''}`}><span className="mb-1 block">{label}</span>{children}</label>; }
function FieldSelect({ fields, value, onChange }) { return <select className="input-field min-w-44" value={value} onChange={e => onChange(e.target.value)}><option value="">-- Chọn cột --</option>{fields.map(field => <option key={field} value={field}>{field}</option>)}</select>; }
function Hint({ children }) { return <span className="mt-1 block text-[10px] font-normal text-gray-400">{children}</span>; }
function Empty({ text }) { return <div className="mt-4 rounded-lg border border-dashed bg-slate-50 p-5 text-center text-xs text-gray-400">{text}</div>; }
function StatusBadge({ status }) { const color = status === 'PUBLISHED' ? 'bg-emerald-100 text-emerald-700' : status === 'PREVIEWED' ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-600'; return <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${color}`}>{status}</span>; }
function transformName(type) { return ({ FILTER: 'LỌC', CAST: 'ĐỔI KIỂU', TRIM: 'XÓA KHOẢNG TRẮNG', SUM: 'TÍNH TỔNG', AVERAGE: 'TÍNH TRUNG BÌNH' })[type] || type; }
function displayValue(value) { if (value == null) return '—'; if (typeof value === 'object') return JSON.stringify(value); return String(value); }
function coerceValue(value, type) { if (type === 'BOOLEAN') return String(value).toLowerCase() === 'true'; if (type === 'NUMBER' || type === 'INTEGER' || type === 'DECIMAL') { const number = Number(value); return Number.isNaN(number) ? value : number; } return value; }
function friendlySchedule(pipeline) { if (pipeline.scheduleType === 'DAILY') return `Hằng ngày lúc ${pipeline.dailyTime || '—'} (${pipeline.timezone})`; if (pipeline.scheduleType === 'ONCE') return `Một lần lúc ${pipeline.scheduledAt || '—'} (${pipeline.timezone})`; return 'Chạy thủ công'; }
function paginationConfig(type, current = {}) {
  if (type === 'PAGE') return { type, pageSize: current.pageSize || 100, pageParam: current.pageParam || 'page', sizeParam: current.sizeParam || 'size', startPage: current.startPage ?? 1 };
  if (type === 'OFFSET') return { type, pageSize: current.pageSize || 100, offsetParam: current.offsetParam || 'offset', limitParam: current.limitParam || 'limit' };
  if (type === 'CURSOR') return { type, pageSize: current.pageSize || 100, cursorParam: current.cursorParam || 'cursor', nextCursorPath: current.nextCursorPath || 'nextCursor' };
  return { type: 'NONE' };
}
