import { useState, useCallback } from 'react';
import { apiError, apiFetch } from '../api';

/**
 * Custom hook for Workflow Designer state & API calls.
 * Used by both "create new" and "edit existing" flows.
 */
export default function useWorkflowDesigner() {
  const [workflow, setWorkflow] = useState(null);
  const [steps, setSteps] = useState([]);
  const [loading, setLoading] = useState(false);
  const [connections, setConnections] = useState([]);
  const [validationErrors, setValidationErrors] = useState([]);

  // ── Create Workflow ──
  const createWorkflow = async (data) => {
    const res = await apiFetch('/api/workflows', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      throw new Error(await apiError(res, 'Không thể tạo workflow'));
    }
    return await res.json();
  };

  // ── Load Workflow Detail ──
  const loadWorkflow = useCallback(async (id) => {
    setLoading(true);
    try {
      const [wfRes, stepsRes, connectionsRes] = await Promise.all([
        apiFetch(`/api/workflows/${id}`),
        apiFetch(`/api/workflows/${id}/steps`),
        apiFetch(`/api/workflows/${id}/connections`)
      ]);
      if (!wfRes.ok || !stepsRes.ok) throw new Error('Failed to load workflow');
      const wfData = await wfRes.json();
      const stepsData = await stepsRes.json();
      const connectionsData = connectionsRes.ok ? await connectionsRes.json() : [];
      setWorkflow(wfData);
      setSteps(stepsData);
      setConnections(connectionsData);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  // ── Add Step ──
  const addStep = async (workflowId, stepData) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/steps`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(stepData),
      toast: false
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to add step');
    }
    const newStep = await res.json();
    setSteps(prev => [...prev, newStep]);
    return newStep;
  };

  // ── Delete Step ──
  const deleteStep = async (workflowId, stepId, reconnect = null) => {
    const query = reconnect
      ? `?incomingConnectionId=${encodeURIComponent(reconnect.incomingConnectionId)}&outgoingConnectionId=${encodeURIComponent(reconnect.outgoingConnectionId)}`
      : '';
    const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}${query}`, {
      method: 'DELETE'
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể xóa step');
    }
    const responseText = await res.text();
    let result;
    if (responseText.trim()) {
      try {
        result = JSON.parse(responseText);
      } catch {
        throw new Error('Backend trả về dữ liệu không hợp lệ sau khi xóa step');
      }
    } else {
      // Backward compatibility with the previous DELETE endpoint (204 No Content).
      result = {
        deletedStepId: stepId,
        deletedConnectionIds: connections
          .filter(connection => connection.fromStepId === stepId || connection.toStepId === stepId)
          .map(connection => connection.id),
        replacementConnection: null
      };
    }
    setSteps(prev => prev.filter(s => s.id !== stepId));
    setConnections(prev => {
      const deletedIds = new Set(result.deletedConnectionIds || []);
      const remaining = prev.filter(connection => !deletedIds.has(connection.id));
      if (!result.replacementConnection || remaining.some(connection => connection.id === result.replacementConnection.id)) return remaining;
      return [...remaining, result.replacementConnection];
    });
    return result;
  };

  const updateStep = async (workflowId, stepId, data) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
      successToast: false
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể cập nhật step');
    }
    const updated = await res.json();
    setSteps(prev => prev.map(step => step.id === stepId ? updated : step));
    return updated;
  };

  // ── Validate ──
  const validateWorkflow = async (workflowId) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/validate`, { method: 'POST' });
    if (!res.ok) throw new Error('Failed to validate');
    const errors = await res.json();
    setValidationErrors(errors);
    return errors;
  };

  const createConnection = async (workflowId, data) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/connections`, { method: 'POST', body: JSON.stringify(data), toast: false });
    if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Không thể tạo connection'); }
    const created = await res.json(); setConnections(v => [...v, created]); return created;
  };

  const updateConnection = async (workflowId, connectionId, data) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/connections/${connectionId}`, { method: 'PUT', body: JSON.stringify(data) });
    if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Không thể cập nhật connection'); }
    const updated = await res.json();
    setConnections(current => current.map(connection => connection.id === connectionId ? updated : connection));
    return updated;
  };

  const deleteConnection = async (workflowId, connectionId) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/connections/${connectionId}`, { method: 'DELETE' });
    if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Không thể xóa connection'); }
    setConnections(current => current.filter(connection => connection.id !== connectionId));
  };

  const saveLayout = async (workflowId, positions) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/steps/layout`, {
      method: 'PUT', body: JSON.stringify({ positions }), successToast: false
    });
    if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Không thể lưu bố cục canvas'); }
    const updated = await res.json();
    const byId = new Map(updated.map(step => [step.id, step]));
    setSteps(current => current.map(step => byId.has(step.id) ? { ...step, ...byId.get(step.id) } : step));
    return updated;
  };

  const publishWorkflow = async (workflowId) => {
    const res = await apiFetch(`/api/workflows/${workflowId}/publish`, { method: 'POST' });
    if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Không thể publish'); }
    const data = await res.json(); setWorkflow(data); return data;
  };

  const applyWorkflowResponse = useCallback((data) => setWorkflow(data), []);

  return {
    workflow,
    steps,
    connections,
    loading,
    validationErrors,
    createWorkflow,
    loadWorkflow,
    addStep,
    updateStep,
    deleteStep,
    saveLayout,
    validateWorkflow
    ,createConnection,
    updateConnection,
    deleteConnection,
    publishWorkflow,
    applyWorkflowResponse
  };
}
