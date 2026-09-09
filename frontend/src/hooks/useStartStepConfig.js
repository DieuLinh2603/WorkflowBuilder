import { useState, useCallback } from 'react';
import { apiError, apiFetch } from '../api';

export default function useStartStepConfig(workflowId, stepId) {
  const [config, setConfig] = useState({
    instructionForCreator: '',
    requesterScope: 'ALL_EMPLOYEES',
    allowedUserIds: [],
    allowedGroupIds: [],
    allowedRoles: [],
    fields: [],
    allowRequesterWithdrawal: true,
    submissionMode: 'SINGLE',
    recordRecipientFieldKey: '',
    maxBatchRows: 500
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchConfig = useCallback(async () => {
    if (!workflowId || !stepId) return;
    setLoading(true);
    try {
      const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}/start-config`);
      if (!res.ok) throw new Error('Lỗi tải cấu hình');
      const data = await res.json();
      setConfig(prev => ({
        ...prev,
        instructionForCreator: data.instructionForCreator || '',
        requesterScope: data.requesterScope || 'ALL_EMPLOYEES',
        allowedUserIds: data.allowedUserIds || [],
        allowedGroupIds: data.allowedGroupIds || [],
        allowedRoles: data.allowedRoles || [],
        fields: data.fields || [],
        allowRequesterWithdrawal: data.allowRequesterWithdrawal !== false,
        submissionMode: data.submissionMode || 'SINGLE',
        recordRecipientFieldKey: data.recordRecipientFieldKey || '',
        maxBatchRows: data.maxBatchRows || 500
      }));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [workflowId, stepId]);

  const updateConfig = async (instruction, scope, userIds, groupIds, roles, allowRequesterWithdrawal = true,
    submissionMode = 'SINGLE', recordRecipientFieldKey = '', maxBatchRows = 500) => {
    try {
      const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}/start-config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          instructionForCreator: instruction,
          requesterScope: scope,
          allowedUserIds: userIds || [],
          allowedGroupIds: groupIds || [],
          allowedRoles: roles || [],
          allowRequesterWithdrawal,
          submissionMode,
          recordRecipientFieldKey: recordRecipientFieldKey || null,
          maxBatchRows
        })
      });
      if (!res.ok) throw new Error(await apiError(res, 'Không thể lưu cấu hình Start Step'));
      setConfig(prev => ({
        ...prev,
        instructionForCreator: instruction,
        requesterScope: scope,
        allowedUserIds: userIds || [],
        allowedGroupIds: groupIds || [],
        allowedRoles: roles || [],
        allowRequesterWithdrawal,
        submissionMode,
        recordRecipientFieldKey,
        maxBatchRows
      }));
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    }
  };

  const addField = async (fieldData) => {
    try {
      const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}/fields`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fieldData)
      });
      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || 'Lỗi thêm field');
      }
      const newField = await res.json();
      setConfig(prev => ({
        ...prev,
        fields: [...prev.fields, newField]
      }));
      return true;
    } catch (err) {
      console.error(err);
      alert(err.message);
      return false;
    }
  };

  const updateField = async (fieldId, fieldData) => {
    try {
      const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}/fields/${fieldId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fieldData)
      });
      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || 'Lỗi cập nhật field');
      }
      const updatedField = await res.json();
      setConfig(prev => ({
        ...prev,
        fields: prev.fields.map(f => f.id === fieldId ? updatedField : f)
      }));
      return true;
    } catch (err) {
      console.error(err);
      alert(err.message);
      return false;
    }
  };

  const deleteField = async (fieldId) => {
    try {
      const res = await apiFetch(`/api/workflows/${workflowId}/steps/${stepId}/fields/${fieldId}`, {
        method: 'DELETE'
      });
      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || 'Lỗi xóa field');
      }
      setConfig(prev => ({
        ...prev,
        fields: prev.fields.filter(f => f.id !== fieldId)
      }));
      return true;
    } catch (err) {
      console.error(err);
      alert(err.message);
      return false;
    }
  };

  return {
    config,
    loading,
    error,
    fetchConfig,
    updateConfig,
    addField,
    updateField,
    deleteField
  };
}
