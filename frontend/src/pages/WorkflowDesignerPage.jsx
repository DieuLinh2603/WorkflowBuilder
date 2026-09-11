import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ReactFlow,
  Background,
  Controls,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType,
  Handle,
  Position
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { ArrowLeft, Plus, Trash2, Zap, CheckCircle2, Eye, UserCheck, Bell, Cpu, CircleStop, Clock3, Users, DatabaseZap, Pencil, X } from 'lucide-react';
import useWorkflowDesigner from '../hooks/useWorkflowDesigner';
import AddStepPopup from '../components/AddStepPopup';
import StartStepPanel from '../components/panels/StartStepPanel';
import BusinessStepPanel from '../components/panels/BusinessStepPanel';
import ReviewStepPanel from '../components/panels/ReviewStepPanel';
import AssignmentStepPanel from '../components/panels/AssignmentStepPanel';
import NotificationStepPanel from '../components/panels/NotificationStepPanel';
import SystemActionStepPanel from '../components/panels/SystemActionStepPanel';
import EndStepPanel from '../components/panels/EndStepPanel';
import ValidationPublishPanel from '../components/panels/ValidationPublishPanel';
import ConnectionConfigModal from '../components/panels/ConnectionConfigModal';
import DeleteStepModal from '../components/panels/DeleteStepModal';
import WorkflowEditorsModal from '../components/WorkflowEditorsModal';
import WorkflowDataBindingModal from '../components/WorkflowDataBindingModal';
import { apiFetch } from '../api';

// ─── Step icon/color config ──────────────────────────
const STEP_CONFIG = {
  START:         { icon: Zap,           bg: 'bg-orange-50',  border: 'border-orange-300', iconColor: 'text-orange-500', label: 'Start' },
  APPROVAL:      { icon: CheckCircle2,  bg: 'bg-orange-50',  border: 'border-orange-300', iconColor: 'text-orange-500', label: 'Approval' },
  REVIEW:        { icon: Eye,           bg: 'bg-blue-50',    border: 'border-blue-300',   iconColor: 'text-blue-500',   label: 'Review' },
  ASSIGNMENT:    { icon: UserCheck,     bg: 'bg-purple-50',  border: 'border-purple-300', iconColor: 'text-purple-500', label: 'Assignment' },
  NOTIFICATION:  { icon: Bell,          bg: 'bg-pink-50',    border: 'border-pink-300',   iconColor: 'text-pink-500',   label: 'Notification' },
  SYSTEM_ACTION: { icon: Cpu,           bg: 'bg-gray-50',    border: 'border-gray-300',   iconColor: 'text-gray-500',   label: 'System Action' },
  END:           { icon: CircleStop,    bg: 'bg-red-50',     border: 'border-red-300',     iconColor: 'text-red-500',    label: 'End' }
};

// ─── Custom Node Component ──────────────────────────
function StepNode({ data, selected }) {
  const config = STEP_CONFIG[data.stepType] || STEP_CONFIG.START;
  const Icon = config.icon;

  return (
    <div className={`group relative isolate min-w-[160px] rounded-xl border-2 px-4 py-3 shadow-sm transition-all ${config.bg} ${config.border} ${selected ? 'shadow-md ring-2 ring-orange-300 ring-offset-2' : ''}`}>
      {/* Input handle */}
      {data.stepType !== 'START' && (
        <Handle type="target" position={Position.Left} className="!w-3 !h-3 !bg-gray-300 !border-2 !border-white" />
      )}

      <div className="flex flex-col items-center gap-2">
        <div className={`flex h-10 w-10 items-center justify-center rounded-lg bg-white/80 shadow-sm ${config.iconColor}`}>
          <Icon size={20} />
        </div>
        <span className="text-sm font-semibold text-gray-700 text-center leading-tight">
          {data.label || config.label}
        </span>
      </div>

      {data.stepType === 'APPROVAL' ? <>
        <Handle id="APPROVE" type="source" position={Position.Right} style={{ top: 26 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-emerald-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[15px] whitespace-nowrap rounded-full bg-emerald-50 px-2 py-1 text-[9px] font-bold text-emerald-700">APPROVE</span>
        <button type="button" disabled={data.hasApproveConnection} className="nodrag nopan absolute left-[calc(100%+78px)] top-[14px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-emerald-400 bg-white text-emerald-600 shadow-sm hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-35" onClick={event => { event.preventDefault(); event.stopPropagation(); if (!data.hasApproveConnection) data.onAddClick?.(event, 'APPROVE'); }} title={data.hasApproveConnection ? 'Đã có nhánh Approve' : 'Thêm bước sau khi duyệt'}><Plus size={12} /></button>
        <Handle id="REJECT" type="source" position={Position.Right} style={{ top: 78 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-red-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[67px] whitespace-nowrap rounded-full bg-red-50 px-2 py-1 text-[9px] font-bold text-red-600">REJECT</span>
        <button type="button" disabled={data.hasRejectConnection} className="nodrag nopan absolute left-[calc(100%+70px)] top-[66px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-red-400 bg-white text-red-500 shadow-sm hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-35" onClick={event => { event.preventDefault(); event.stopPropagation(); if (!data.hasRejectConnection) data.onAddClick?.(event, 'REJECT'); }} title={data.hasRejectConnection ? 'Đã có nhánh Reject' : 'Thêm bước sau khi từ chối'}><Plus size={12} /></button>
        {data.hasLegacyDefault && <><Handle id="LEGACY_DEFAULT" type="source" position={Position.Right} style={{ top: 52 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-amber-500" /><span className="pointer-events-none absolute left-[calc(100%+10px)] top-[43px] whitespace-nowrap rounded-full bg-amber-50 px-2 py-1 text-[9px] font-bold text-amber-700">CẦN PHÂN LOẠI</span></>}
      </> : data.stepType === 'REVIEW' ? <>
        <Handle id="REVIEW_PASS" type="source" position={Position.Right} style={{ top: 26 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-blue-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[15px] whitespace-nowrap rounded-full bg-blue-50 px-2 py-1 text-[9px] font-bold text-blue-700">ĐẠT</span>
        <button type="button" disabled={data.hasReviewPassConnection} className="nodrag nopan absolute left-[calc(100%+48px)] top-[14px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-blue-400 bg-white text-blue-600 shadow-sm hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-35" onClick={event => { event.preventDefault(); event.stopPropagation(); if (!data.hasReviewPassConnection) data.onAddClick?.(event, 'REVIEW_PASS'); }} title={data.hasReviewPassConnection ? 'Đã có nhánh Đạt' : 'Thêm bước khi review đạt'}><Plus size={12} /></button>
        <Handle id="REVIEW_FAIL" type="source" position={Position.Right} style={{ top: 78 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-red-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[67px] whitespace-nowrap rounded-full bg-red-50 px-2 py-1 text-[9px] font-bold text-red-600">KHÔNG ĐẠT</span>
        <button type="button" disabled={data.hasReviewFailConnection} className="nodrag nopan absolute left-[calc(100%+84px)] top-[66px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-red-400 bg-white text-red-500 shadow-sm hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-35" onClick={event => { event.preventDefault(); event.stopPropagation(); if (!data.hasReviewFailConnection) data.onAddClick?.(event, 'REVIEW_FAIL'); }} title={data.hasReviewFailConnection ? 'Đã có nhánh Không đạt' : 'Thêm bước xử lý khi review không đạt'}><Plus size={12} /></button>
        {data.hasLegacyDefault && <><Handle id="LEGACY_REVIEW_DEFAULT" type="source" position={Position.Right} style={{ top: 52 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-amber-500" /><span className="pointer-events-none absolute left-[calc(100%+10px)] top-[43px] whitespace-nowrap rounded-full bg-amber-50 px-2 py-1 text-[9px] font-bold text-amber-700">CẦN PHÂN LOẠI</span></>}
      </> : (data.stepType === 'ASSIGNMENT' || data.stepType === 'SYSTEM_ACTION') ? <>
        <Handle id={data.stepType === 'ASSIGNMENT' ? 'ASSIGNMENT_DONE' : 'SYSTEM_SUCCESS'} type="source" position={Position.Right} style={{ top: 26 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-emerald-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[15px] whitespace-nowrap rounded-full bg-emerald-50 px-2 py-1 text-[9px] font-bold text-emerald-700">THÀNH CÔNG</span>
        <button type="button" disabled={data.hasSuccessConnection} className="nodrag nopan absolute left-[calc(100%+86px)] top-[14px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-emerald-400 bg-white text-emerald-600 disabled:opacity-35" onClick={event => { event.stopPropagation(); if (!data.hasSuccessConnection) data.onAddClick?.(event, data.stepType === 'ASSIGNMENT' ? 'ASSIGNMENT_DONE' : 'SYSTEM_SUCCESS'); }}><Plus size={12}/></button>
        <Handle id={data.stepType === 'ASSIGNMENT' ? 'ASSIGNMENT_FAIL' : 'SYSTEM_FAIL'} type="source" position={Position.Right} style={{ top: 78 }} className="!h-3.5 !w-3.5 !border-2 !border-white !bg-red-500" />
        <span className="pointer-events-none absolute left-[calc(100%+10px)] top-[67px] whitespace-nowrap rounded-full bg-red-50 px-2 py-1 text-[9px] font-bold text-red-600">THẤT BẠI</span>
        <button type="button" disabled={data.hasFailureConnection} className="nodrag nopan absolute left-[calc(100%+72px)] top-[66px] z-[60] flex h-6 w-6 items-center justify-center rounded-full border-2 border-red-400 bg-white text-red-500 disabled:opacity-35" onClick={event => { event.stopPropagation(); if (!data.hasFailureConnection) data.onAddClick?.(event, data.stepType === 'ASSIGNMENT' ? 'ASSIGNMENT_FAIL' : 'SYSTEM_FAIL'); }}><Plus size={12}/></button>
      </> : data.stepType !== 'END' && <Handle id="DEFAULT" type="source" position={Position.Right} className="!w-3 !h-3 !bg-orange-400 !border-2 !border-white" />}

      {/* Plus button */}
      {data.stepType !== 'END' && data.stepType !== 'APPROVAL' && data.stepType !== 'REVIEW' && data.stepType !== 'ASSIGNMENT' && data.stepType !== 'SYSTEM_ACTION' && (
        <button
          type="button"
          className="nodrag nopan absolute -right-4 top-1/2 z-[60] flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full border-2 border-orange-400 bg-white text-orange-500 shadow-md transition-colors hover:bg-orange-50"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            data.onAddClick?.(e, 'DEFAULT');
          }}
          title="Thêm step tiếp theo"
        >
          <Plus size={14} />
        </button>
      )}
      {data.canDelete && <button type="button"
        className="nodrag nopan absolute -right-3 -top-3 z-[70] flex h-7 w-7 items-center justify-center rounded-full border border-red-200 bg-white text-red-400 opacity-0 shadow-md transition hover:border-red-400 hover:bg-red-50 hover:text-red-600 group-hover:opacity-100"
        onPointerDown={event => { event.preventDefault(); event.stopPropagation(); }}
        onClick={event => { event.preventDefault(); event.stopPropagation(); data.onDeleteClick?.(); }}
        title="Xóa step"><Trash2 size={13} /></button>}
    </div>
  );
}

const nodeTypes = { stepNode: StepNode };

// ─── Main Page Component ──────────────────────────
export default function WorkflowDesignerPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { workflow, steps, connections, loading, validationErrors, loadWorkflow, addStep, updateStep, updateWorkflowMetadata, deleteStep, saveLayout, validateWorkflow, createConnection, updateConnection, deleteConnection, publishWorkflow, applyWorkflowResponse } = useWorkflowDesigner();

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [showAddPopup, setShowAddPopup] = useState(false);
  const [addingStep, setAddingStep] = useState(false);
  const [designerError, setDesignerError] = useState('');
  const [popupPosition, setPopupPosition] = useState({ x: 0, y: 0 });
  const [sourceNodeId, setSourceNodeId] = useState(null);
  const [sourceConnectionType, setSourceConnectionType] = useState('DEFAULT');
  const [validating, setValidating] = useState(false);
  const [showValidationResult, setShowValidationResult] = useState(false);
  const [showDataBinding, setShowDataBinding] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishedFromPanel, setPublishedFromPanel] = useState(false);
  const [validationFailure, setValidationFailure] = useState('');
  const [pendingConnection, setPendingConnection] = useState(null);
  const [editingStepId, setEditingStepId] = useState(null);
  const [editingLabel, setEditingLabel] = useState('');
  const [configuredStepId, setConfiguredStepId] = useState(null);
  const [deleteStepId, setDeleteStepId] = useState(null);
  const [editingConnection, setEditingConnection] = useState(null);
  const [layoutSaving, setLayoutSaving] = useState(false);
  const [layoutMessage, setLayoutMessage] = useState('');
  const [showEditors, setShowEditors] = useState(false);
  const [metadataForm, setMetadataForm] = useState(null);
  const [metadataSaving, setMetadataSaving] = useState(false);
  const [metadataError, setMetadataError] = useState('');
  const layoutRequestCount = useRef(0);
  const workflowRef = useRef(null);

  useEffect(() => {
    workflowRef.current = workflow;
  }, [workflow]);

  const discardUnchangedDraft = useCallback(async () => {
    if (workflowRef.current?.status !== 'DRAFT') return;
    const draftId = workflowRef.current.id;
    workflowRef.current = null;
    await apiFetch(`/api/workflows/${draftId}/unchanged-draft`, {
      method: 'DELETE',
      toast: false,
      keepalive: true
    }).catch(() => {});
  }, []);

  useEffect(() => () => {
    if (workflowRef.current?.status === 'DRAFT') discardUnchangedDraft();
  }, [discardUnchangedDraft]);

  const selectedStep = configuredStepId ? steps.find(step => step.id === configuredStepId) : null;
  const stepPendingDelete = deleteStepId ? steps.find(step => step.id === deleteStepId) : null;

  const openMetadataEditor = () => {
    setMetadataError('');
    setMetadataForm({ name: workflow?.name || '', description: workflow?.description || '' });
  };
  const saveMetadata = async event => {
    event.preventDefault();
    const name = metadataForm?.name?.trim() || '';
    if (!name) return setMetadataError('Tên workflow là bắt buộc.');
    setMetadataSaving(true); setMetadataError('');
    try {
      await updateWorkflowMetadata(id, { name, description: metadataForm.description?.trim() || null });
      setMetadataForm(null);
    } catch (reason) { setMetadataError(reason.message); }
    finally { setMetadataSaving(false); }
  };

  const openStepConfig = useCallback((stepId) => {
    setShowValidationResult(false);
    setConfiguredStepId(stepId);
    setNodes(current => current.map(node => ({ ...node, selected: node.id === stepId })));
  }, [setNodes]);

  const closeStepConfig = useCallback(() => {
    setConfiguredStepId(null);
    setNodes(current => current.map(node => ({ ...node, selected: false })));
  }, [setNodes]);

  // Load workflow on mount
  useEffect(() => {
    if (id) loadWorkflow(id);
  }, [id, loadWorkflow]);

  // Check if START step already exists
  const hasStartStep = useMemo(() => steps.some(s => s.type === 'START'), [steps]);

  // Convert backend steps → React Flow nodes
  useEffect(() => {
    if (steps.length === 0) return;

    const newNodes = steps.map((step) => ({
      id: step.id,
      type: 'stepNode',
      zIndex: 2,
      position: { x: step.positionX, y: step.positionY },
      data: {
        label: step.label,
        stepType: step.type,
        onAddClick: (e, connectionType = 'DEFAULT') => {
          const rect = e.currentTarget.getBoundingClientRect();
          setPopupPosition({ x: rect.right + 10, y: rect.top - 12, anchorX: rect.left });
          setDesignerError('');
          setSourceNodeId(step.id);
          setSourceConnectionType(connectionType);
          setShowAddPopup(true);
        },
        canDelete: workflow?.status === 'DRAFT' && step.type !== 'START',
        onDeleteClick: () => setDeleteStepId(step.id),
        hasApproveConnection: connections.some(connection => connection.fromStepId === step.id && connection.type === 'APPROVE'),
        hasRejectConnection: connections.some(connection => connection.fromStepId === step.id && connection.type === 'REJECT'),
        hasReviewPassConnection: connections.some(connection => connection.fromStepId === step.id && connection.type === 'REVIEW_PASS'),
        hasReviewFailConnection: connections.some(connection => connection.fromStepId === step.id && connection.type === 'REVIEW_FAIL'),
        hasSuccessConnection: connections.some(connection => connection.fromStepId === step.id && ['ASSIGNMENT_DONE', 'SYSTEM_SUCCESS'].includes(connection.type)),
        hasFailureConnection: connections.some(connection => connection.fromStepId === step.id && ['ASSIGNMENT_FAIL', 'SYSTEM_FAIL'].includes(connection.type)),
        hasLegacyDefault: connections.some(connection => connection.fromStepId === step.id && connection.type === 'DEFAULT')
      }
    }));

    setNodes(current => {
      const currentById = new Map(current.map(node => [node.id, node]));
      return newNodes.map(node => {
        const existing = currentById.get(node.id);
        return existing ? { ...node, position: existing.position, selected: existing.selected } : node;
      });
    });
  }, [steps, connections, setNodes, workflow?.status]);

  useEffect(() => {
    const stepById = new Map(steps.map(step => [step.id, step]));
    const failureConnections = new Set(['REJECT', 'REVIEW_FAIL', 'ASSIGNMENT_FAIL', 'SYSTEM_FAIL']);
    const successConnections = new Set(['APPROVE', 'REVIEW_PASS', 'ASSIGNMENT_DONE', 'SYSTEM_SUCCESS']);
    setEdges(connections.map(connection => {
      const sourceType = stepById.get(connection.fromStepId)?.type;
      const resultLegacy = ['APPROVAL', 'REVIEW', 'ASSIGNMENT', 'SYSTEM_ACTION'].includes(sourceType) && connection.type === 'DEFAULT';
      const color = failureConnections.has(connection.type) ? '#ef4444'
        : successConnections.has(connection.type) ? (connection.type === 'REVIEW_PASS' ? '#3b82f6' : '#10b981')
          : resultLegacy ? '#f59e0b' : '#f97316';
      const label = resultLegacy ? 'CẦN PHÂN LOẠI' : connection.type === 'REVIEW_PASS' ? 'ĐẠT'
        : connection.type === 'REVIEW_FAIL' ? 'KHÔNG ĐẠT'
          : connection.type === 'IF' ? `IF ${connection.clauses?.[0]?.expression ? 'ƒ(x)' : connection.clauses?.[0]?.fieldKey || ''}` : connection.type;
      const displayLabel = ['ASSIGNMENT_DONE', 'SYSTEM_SUCCESS'].includes(connection.type) ? 'THÀNH CÔNG'
        : ['ASSIGNMENT_FAIL', 'SYSTEM_FAIL'].includes(connection.type) ? 'THẤT BẠI' : label;
      return { id: connection.id, source: connection.fromStepId, target: connection.toStepId,
        sourceHandle: resultLegacy ? (sourceType === 'REVIEW' ? 'LEGACY_REVIEW_DEFAULT' : 'LEGACY_DEFAULT')
          : (failureConnections.has(connection.type) || successConnections.has(connection.type)) ? connection.type : undefined,
        label: displayLabel, type: 'smoothstep', zIndex: 0, interactionWidth: 24,
        markerEnd: { type: MarkerType.ArrowClosed, color },
        style: { stroke: color, strokeWidth: (failureConnections.has(connection.type) || successConnections.has(connection.type)) ? 2.5 : 2, strokeDasharray: resultLegacy ? '6 5' : undefined },
        labelStyle: { fill: color, fontWeight: 700, fontSize: resultLegacy ? 9 : 10 },
        labelBgStyle: { fill: '#ffffff', fillOpacity: 0.95 }, labelBgPadding: [5, 3], labelBgBorderRadius: 5 };
    }));
  }, [connections, steps, setEdges]);

  // Handle adding a new step from popup
  const handleAddStep = async (stepType) => {
    if (!id || addingStep) return;

    // Compute position: offset from source node
    const sourceNode = nodes.find(n => n.id === sourceNodeId);
    const posX = sourceNode ? sourceNode.position.x + 250 : 500;
    const branchOffset = ['APPROVE', 'REVIEW_PASS', 'ASSIGNMENT_DONE', 'SYSTEM_SUCCESS'].includes(sourceConnectionType) ? -130
      : ['REJECT', 'REVIEW_FAIL', 'ASSIGNMENT_FAIL', 'SYSTEM_FAIL'].includes(sourceConnectionType) ? 130 : 0;
    const posY = sourceNode ? sourceNode.position.y + branchOffset : 200;

    setAddingStep(true);
    setDesignerError('');
    try {
      const newStep = await addStep(id, {
        type: stepType,
        positionX: posX,
        positionY: posY
      });

      // Auto-add edge from source to new node
      if (sourceNodeId && newStep) {
        try {
          await createConnection(id, { fromStepId: sourceNodeId, toStepId: newStep.id, type: sourceConnectionType, logicalOperator: 'AND', priority: 100, clauses: [] });
        } catch (connectionError) {
          setDesignerError(`Đã thêm node “${newStep.label}” nhưng chưa thể tự nối nhánh: ${connectionError.message}. Bạn có thể kéo connection thủ công từ node nguồn.`);
        }
      }
      setShowAddPopup(false);
      setSourceNodeId(null);
      setSourceConnectionType('DEFAULT');
    } catch (err) {
      setDesignerError(`Không thể thêm node: ${err.message || 'Backend không trả về thông tin lỗi.'}`);
    } finally {
      setAddingStep(false);
    }
  };

  const onConnect = useCallback((params) => setPendingConnection(params), []);

  const saveConnection = async (data) => {
    await createConnection(id, data);
    setPendingConnection(null);
  };

  const saveEditedConnection = async (data) => {
    await updateConnection(id, editingConnection.id, data);
    setEditingConnection(null);
  };

  const removeEditedConnection = async () => {
    await deleteConnection(id, editingConnection.id);
    setEditingConnection(null);
  };

  const persistLayout = async (positions, successMessage) => {
    layoutRequestCount.current += 1;
    setLayoutSaving(true); setLayoutMessage('Đang lưu bố cục...');
    try {
      await saveLayout(id, positions);
      setLayoutMessage(successMessage);
    } catch (error) {
      setLayoutMessage(`Không thể lưu bố cục: ${error.message}`);
      throw error;
    } finally {
      layoutRequestCount.current -= 1;
      if (layoutRequestCount.current === 0) setLayoutSaving(false);
    }
  };

  const handleNodeDragStop = (_, node) => {
    if (workflow?.status !== 'DRAFT') return;
    persistLayout([{ stepId: node.id, positionX: Math.round(node.position.x), positionY: Math.round(node.position.y) }], 'Đã tự lưu vị trí').catch(() => {});
  };

  const saveEntireLayout = async () => {
    if (!nodes.length || workflow?.status !== 'DRAFT') return;
    await persistLayout(nodes.map(node => ({ stepId: node.id, positionX: Math.round(node.position.x), positionY: Math.round(node.position.y) })), 'Đã lưu toàn bộ sơ đồ').catch(() => {});
  };

  const beginRename = (step) => {
    setEditingStepId(step.id);
    setEditingLabel(step.label || '');
  };

  const finishRename = async (step, value = editingLabel) => {
    const label = value.trim();
    setEditingStepId(null);
    if (!label || label === step.label) return;
    try { await updateStep(id, step.id, { label }); }
    catch (error) { alert(error.message); }
  };

  const confirmDeleteStep = async (reconnect) => {
    if (!stepPendingDelete) return;
    await deleteStep(id, stepPendingDelete.id, reconnect);
    if (configuredStepId === stepPendingDelete.id) closeStepConfig();
    setDeleteStepId(null);
  };

  const handleValidate = async () => {
    if (!id) return;
    closeStepConfig();
    setPublishedFromPanel(false);
    setValidationFailure('');
    setShowValidationResult(true);
    setValidating(true);
    try {
      await validateWorkflow(id);
    } catch (error) {
      setValidationFailure(error.message || 'Không thể kết nối tới dịch vụ validation');
    } finally {
      setValidating(false);
    }
  };

  const displayedValidationErrors = validationFailure ? [validationFailure] : validationErrors;
  const isPublishable = workflow?.canPublish && displayedValidationErrors.length === 0 && showValidationResult && !validating && workflow?.status === 'DRAFT';
  const handlePublish = async () => {
    if (!isPublishable) { await handleValidate(); return; }
    setPublishing(true);
    try { await publishWorkflow(id); setPublishedFromPanel(true); }
    catch (error) { alert(error.message || 'Không thể publish workflow'); }
    finally { setPublishing(false); }
  };

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50">
        <div className="text-gray-500 animate-pulse">Đang tải workflow...</div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* ── Header Toolbar ── */}
      <div className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-4 flex-shrink-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={async () => { await discardUnchangedDraft(); navigate('/workflows'); }}
            className="p-1.5 rounded-md hover:bg-gray-100 text-gray-500 transition-colors"
          >
            <ArrowLeft size={20} />
          </button>
          <h1 className="text-base font-bold text-gray-800 truncate max-w-[300px]">
            {workflow?.name || 'Workflow'}
          </h1>
          {workflow?.canEdit && <button type="button" onClick={openMetadataEditor} className="rounded-md p-1.5 text-gray-400 hover:bg-orange-50 hover:text-orange-600" title="Sửa tên và mô tả workflow" aria-label="Sửa tên và mô tả workflow"><Pencil size={15}/></button>}
          <span className="px-2.5 py-0.5 rounded-md text-xs font-semibold bg-gray-200 text-gray-600">
            {workflow?.status || 'Draft'}
          </span>
          <span className="rounded-full bg-sky-50 px-2.5 py-1 text-xs font-semibold text-sky-700">{workflow?.moduleName || workflow?.module}</span>
          <button type="button" onClick={() => navigate(`/workflows/${id}/versions`)} className="flex items-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-500 hover:border-orange-300 hover:text-orange-600" title="Xem lịch sử phiên bản"><Clock3 size={14}/>Phiên bản</button>
        </div>

        <div className="flex items-center gap-2">
          {workflow?.status === 'PUBLISHED' && <button type="button" onClick={() => setShowDataBinding(true)} className="flex items-center gap-1.5 rounded-lg border border-emerald-200 px-3 py-1.5 text-sm font-semibold text-emerald-700 hover:bg-emerald-50"><DatabaseZap size={16}/>Data Binding</button>}
          {layoutMessage && <span className={`mr-2 max-w-[240px] truncate text-xs ${layoutMessage.startsWith('Không thể') ? 'text-red-500' : 'text-gray-500'}`}>{layoutMessage}</span>}
          {workflow?.canManageEditors && <button type="button" onClick={() => setShowEditors(true)} className="flex items-center gap-1.5 rounded-lg border border-blue-200 px-3 py-1.5 text-sm font-semibold text-blue-600 hover:bg-blue-50"><Users size={16}/>Editors ({workflow.editors?.length || 0})</button>}
          <button
            onClick={handleValidate}
            disabled={validating}
            className="px-4 py-1.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-600 hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            {validating ? 'Đang kiểm tra...' : 'Validate'}
          </button>
          {workflow?.canPublish && <button
            onClick={handlePublish}
            disabled={!isPublishable}
            className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
              isPublishable
                ? 'bg-orange-500 text-white hover:bg-orange-600 shadow-sm'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed'
            }`}
          >
            Publish
          </button>}
          <button onClick={saveEntireLayout} disabled={layoutSaving || workflow?.status !== 'DRAFT'} className="px-4 py-1.5 bg-orange-500 hover:bg-orange-600 text-white rounded-lg text-sm font-semibold transition-colors shadow-sm disabled:cursor-not-allowed disabled:opacity-50">
            {layoutSaving ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>

      {/* ── Body: Sidebar + Canvas ── */}
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <div className="w-[250px] bg-white border-r border-gray-200 flex-shrink-0 overflow-auto">
          {(workflow?.typeChecklist?.length > 0 || workflow?.recommendedStepTypes?.length > 0) && <div className="border-b border-orange-100 bg-orange-50/60 p-4"><p className="text-xs font-bold text-orange-700">Gợi ý: {workflow.typeName || workflow.type}</p>{workflow.recommendedStepTypes?.length > 0 && <p className="mt-2 text-[11px] leading-5 text-gray-500">Step nên dùng: {workflow.recommendedStepTypes.join(', ')}</p>}<ul className="mt-2 space-y-1">{workflow.typeChecklist?.map(item => <li key={item} className="flex gap-2 text-[11px] leading-4 text-gray-600"><span className="text-orange-500">✓</span>{item}</li>)}</ul></div>}
          <div className="px-4 py-3 border-b border-gray-100">
            <h3 className="text-[11px] font-bold text-gray-400 tracking-wider uppercase">CÁC BƯỚC ĐÃ THIẾT LẬP</h3>
          </div>
          <div className="p-2 space-y-0.5">
            {steps.map(step => {
              const config = STEP_CONFIG[step.type] || STEP_CONFIG.START;
              const Icon = config.icon;
              return (
                <div
                  key={step.id}
                  className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors group ${selectedStep?.id === step.id ? config.bg : 'hover:bg-orange-50'} ${editingStepId === step.id ? 'cursor-text' : 'cursor-pointer'}`}
                  onClick={() => {
                    openStepConfig(step.id);
                  }}
                  onDoubleClick={(event) => { event.preventDefault(); beginRename(step); }}
                  title="Nhấn đúp để đổi tên step"
                >
                  <div className={`w-5 h-5 rounded flex items-center justify-center flex-shrink-0 ${config.iconColor}`}>
                    <Icon size={14} />
                  </div>
                  {editingStepId === step.id ? <input
                    autoFocus
                    maxLength={120}
                    value={editingLabel}
                    onClick={event => event.stopPropagation()}
                    onChange={event => setEditingLabel(event.target.value)}
                    onBlur={event => finishRename(step, event.currentTarget.value)}
                    onKeyDown={event => {
                      if (event.key === 'Enter') event.currentTarget.blur();
                      if (event.key === 'Escape') {
                        event.currentTarget.value = step.label || '';
                        setEditingLabel(step.label || '');
                        event.currentTarget.blur();
                      }
                    }}
                    className="min-w-0 flex-1 rounded-md border border-orange-300 bg-white px-2 py-1 text-sm outline-none ring-2 ring-orange-100"
                    aria-label={`Đổi tên ${step.label}`}
                  /> : <span className="truncate text-gray-700 group-hover:text-orange-700 font-medium">
                    {step.type === 'START' ? 'Start' : step.type === 'END' ? 'End' : step.type.charAt(0) + step.type.slice(1).toLowerCase()}:{' '}
                    <span className="font-normal text-gray-500">{step.label}</span>
                  </span>}
                </div>
              );
            })}
            {steps.length === 0 && (
              <div className="px-3 py-4 text-xs text-gray-400 text-center">Chưa có bước nào</div>
            )}
          </div>
        </div>

        {/* Canvas */}
        <div className="flex-1 relative">
          {designerError && <div className="absolute left-1/2 top-4 z-[90] flex max-w-xl -translate-x-1/2 items-start gap-3 rounded-xl border border-red-200 bg-white px-4 py-3 text-sm text-red-600 shadow-lg"><span className="flex-1">{designerError}</span><button type="button" onClick={() => setDesignerError('')} className="font-bold text-red-400 hover:text-red-600" aria-label="Đóng thông báo">×</button></div>}
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onEdgeDoubleClick={(_, edge) => {
              if (workflow?.status !== 'DRAFT') return;
              const connection = connections.find(item => item.id === edge.id);
              if (connection) setEditingConnection(connection);
            }}
            onNodeDoubleClick={(_, node) => openStepConfig(node.id)}
            selectNodesOnDrag={false}
            nodesDraggable={workflow?.status === 'DRAFT'}
            nodesConnectable={workflow?.status === 'DRAFT'}
            nodeTypes={nodeTypes}
            fitView
            fitViewOptions={{ padding: 0.4 }}
            defaultEdgeOptions={{
              type: 'smoothstep',
              animated: true,
              zIndex: 0,
              style: { stroke: '#f97316', strokeWidth: 2 }
            }}
          >
            <Background color="#e5e7eb" gap={20} />
            <Controls position="bottom-right" />
          </ReactFlow>

          {/* AddStepPopup */}
          {showAddPopup && (
            <AddStepPopup
              position={popupPosition}
              onSelect={handleAddStep}
              onClose={() => setShowAddPopup(false)}
              hasStartStep={hasStartStep}
              adding={addingStep}
            />
          )}
        </div>

        {/* Right Sidebar - Properties Panel */}
        {selectedStep?.type === 'START' && (
          <StartStepPanel
            workflowId={id}
            step={selectedStep}
            onClose={closeStepConfig}
          />
        )}
        {selectedStep?.type === 'APPROVAL' && <BusinessStepPanel workflowId={id} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} />}
        {selectedStep?.type === 'REVIEW' && <ReviewStepPanel workflowId={id} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} />}
        {selectedStep?.type === 'ASSIGNMENT' && <AssignmentStepPanel workflowId={id} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} />}
        {selectedStep?.type === 'NOTIFICATION' && <NotificationStepPanel workflowId={id} workflowName={workflow?.name} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} onConfigureStart={() => openStepConfig(steps.find(item => item.type === 'START')?.id)} />}
        {selectedStep?.type === 'SYSTEM_ACTION' && <SystemActionStepPanel workflowId={id} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} />}
        {selectedStep?.type === 'END' && <EndStepPanel workflowId={id} step={selectedStep} onClose={closeStepConfig} onDelete={workflow?.status === 'DRAFT' ? () => setDeleteStepId(selectedStep.id) : undefined} />}
        {showValidationResult && <ValidationPublishPanel steps={steps} errors={displayedValidationErrors} validating={validating} publishing={publishing} published={publishedFromPanel || workflow?.status === 'PUBLISHED'} canPublish={!!workflow?.canPublish} onValidate={handleValidate} onPublish={handlePublish} onClose={() => setShowValidationResult(false)} />}
      </div>
      {pendingConnection && <ConnectionConfigModal workflowId={id} connection={pendingConnection}
        sourceStepType={steps.find(step => step.id === pendingConnection.source)?.type}
        targetStepLabel={steps.find(step => step.id === pendingConnection.target)?.label}
        onSave={saveConnection} onClose={() => setPendingConnection(null)} />}
      {editingConnection && <ConnectionConfigModal workflowId={id} editing sourceStepType={steps.find(step => step.id === editingConnection.fromStepId)?.type}
        targetStepLabel={steps.find(step => step.id === editingConnection.toStepId)?.label}
        connection={{ id: editingConnection.id, source: editingConnection.fromStepId, target: editingConnection.toStepId,
          sourceHandle: editingConnection.type, currentType: editingConnection.type,
          logicalOperator: editingConnection.logicalOperator, priority: editingConnection.priority, clauses: editingConnection.clauses || [] }}
        onSave={saveEditedConnection} onDelete={removeEditedConnection} onClose={() => setEditingConnection(null)} />}
      {stepPendingDelete && <DeleteStepModal step={stepPendingDelete} steps={steps} connections={connections} onConfirm={confirmDeleteStep} onClose={() => setDeleteStepId(null)} />}
      {showEditors && workflow && <WorkflowEditorsModal workflow={workflow} onChanged={applyWorkflowResponse} onClose={() => setShowEditors(false)}/>} 
      {showDataBinding && workflow && <WorkflowDataBindingModal workflow={workflow} onClose={() => setShowDataBinding(false)}/>} 
      {metadataForm && <WorkflowMetadataModal form={metadataForm} setForm={setMetadataForm} saving={metadataSaving} error={metadataError} onSubmit={saveMetadata} onClose={() => !metadataSaving && setMetadataForm(null)}/>}
    </div>
  );
}

function WorkflowMetadataModal({ form, setForm, saving, error, onSubmit, onClose }) {
  return <div className="fixed inset-0 z-[180] flex items-center justify-center bg-slate-900/40 p-4" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <form onSubmit={onSubmit} className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
      <header className="flex items-start justify-between border-b px-6 py-5"><div><h2 className="text-lg font-bold text-slate-800">Thông tin workflow</h2><p className="mt-1 text-xs text-slate-500">Tên và mô tả sẽ được lưu cho phiên bản Draft hiện tại.</p></div><button type="button" disabled={saving} onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" aria-label="Đóng"><X size={18}/></button></header>
      <div className="space-y-4 p-6">
        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}
        <label className="block text-sm font-semibold text-slate-700">Tên workflow <span className="text-red-500">*</span><input autoFocus required maxLength={255} value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} className="input-field mt-1.5" placeholder="Nhập tên workflow"/></label>
        <label className="block text-sm font-semibold text-slate-700">Mô tả<textarea rows={5} maxLength={2000} value={form.description} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} className="input-field mt-1.5 resize-none" placeholder="Mô tả mục đích và phạm vi của workflow"/><span className="mt-1 block text-right text-[10px] font-normal text-slate-400">{form.description.length}/2000</span></label>
      </div>
      <footer className="flex justify-end gap-2 border-t bg-slate-50 px-6 py-4"><button type="button" disabled={saving} onClick={onClose} className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold text-slate-600 disabled:opacity-50">Hủy</button><button disabled={saving || !form.name.trim()} className="btn-primary px-5 py-2 disabled:opacity-50">{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button></footer>
    </form>
  </div>;
}
