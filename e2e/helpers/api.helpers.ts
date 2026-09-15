/**
 * REST API Helper functions for setting up test data and authentication.
 * Using direct API calls for test fixtures ensures speed, consistency, and reduced flakiness.
 */

export const BACKEND_URL = 'http://localhost:8081';

export interface LoginResponse {
  token: string;
  userId: string;
  email: string;
  displayName: string;
  systemRoles: string[];
}

export interface WorkflowResponse {
  id: string;
  name: string;
  description?: string;
  version: string;
  status: 'DRAFT' | 'PUBLISHED' | 'SUSPENDED' | 'ARCHIVED';
  steps?: StepResponse[];
  [key: string]: any;
}

export interface StepResponse {
  id: string;
  workflowId: string;
  type: 'START' | 'APPROVAL' | 'REVIEW' | 'ASSIGNMENT' | 'NOTIFICATION' | 'SYSTEM_ACTION' | 'END';
  label: string;
  positionX: number;
  positionY: number;
  [key: string]: any;
}

/**
 * Log in via backend REST API and return auth credentials & JWT token.
 */
export async function loginViaAPI(
  email = 'admin@company.com',
  password = 'admin123'
): Promise<LoginResponse> {
  const res = await fetch(`${BACKEND_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '');
    throw new Error(`API login failed with status ${res.status}: ${errorBody}`);
  }

  return res.json();
}

/**
 * Create a new draft workflow via REST API.
 */
export async function createWorkflow(
  token: string,
  name: string,
  description = 'Playwright Test Workflow'
): Promise<WorkflowResponse> {
  const res = await fetch(`${BACKEND_URL}/api/workflows`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      name,
      description,
      workflowType: 'Khác',
      module: 'IT',
    }),
  });

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '');
    throw new Error(`Failed to create workflow via API: ${errorBody}`);
  }

  return res.json();
}

/**
 * Delete a workflow by ID via REST API.
 */
export async function deleteWorkflow(token: string, workflowId: string): Promise<void> {
  const res = await fetch(`${BACKEND_URL}/api/workflows/${workflowId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });

  // 200/204 or 404 (already deleted) are both acceptable in cleanup
  if (!res.ok && res.status !== 404) {
    const errorBody = await res.text().catch(() => '');
    console.warn(`Warning: failed to delete workflow ${workflowId}: ${errorBody}`);
  }
}

/**
 * Fetch all steps of a workflow.
 */
export async function getWorkflowSteps(token: string, workflowId: string): Promise<StepResponse[]> {
  const res = await fetch(`${BACKEND_URL}/api/workflows/${workflowId}/steps`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to fetch workflow steps for ${workflowId}`);
  return res.json();
}

/**
 * Add a new step to a workflow via REST API.
 */
export async function addStepViaAPI(
  token: string,
  workflowId: string,
  type: StepResponse['type'],
  label: string,
  position = { x: 500, y: 200 }
): Promise<StepResponse> {
  const res = await fetch(`${BACKEND_URL}/api/workflows/${workflowId}/steps`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      type,
      label,
      positionX: position.x,
      positionY: position.y,
    }),
  });

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '');
    throw new Error(`Failed to add step ${type}: ${errorBody}`);
  }

  return res.json();
}

/**
 * Create a connection between two steps.
 */
export async function createConnectionViaAPI(
  token: string,
  workflowId: string,
  fromStepId: string,
  toStepId: string,
  type: 'DEFAULT' | 'APPROVE' | 'REJECT' | 'REVIEW_PASS' | 'REVIEW_FAIL' | 'IF' | 'ELSE' = 'DEFAULT'
): Promise<any> {
  const res = await fetch(`${BACKEND_URL}/api/workflows/${workflowId}/connections`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      fromStepId,
      toStepId,
      type,
      logicalOperator: 'AND',
      clauses: [],
    }),
  });

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '');
    throw new Error(`Failed to create connection: ${errorBody}`);
  }

  return res.json();
}

/**
 * Add a custom input field to a step.
 */
export async function addFieldViaAPI(
  token: string,
  workflowId: string,
  stepId: string,
  field: {
    label: string;
    fieldKey?: string;
    type: 'TEXT' | 'NUMBER' | 'DATE' | 'CHECKBOX' | 'SELECT' | 'FILE';
    required?: boolean;
    options?: string[];
  }
): Promise<any> {
  const res = await fetch(`${BACKEND_URL}/api/workflows/${workflowId}/steps/${stepId}/fields`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(field),
  });

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '');
    throw new Error(`Failed to add field: ${errorBody}`);
  }

  return res.json();
}

/**
 * Fetch active users from backend.
 */
export async function getActiveUsers(token: string): Promise<any[]> {
  const res = await fetch(`${BACKEND_URL}/api/users/active`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  return res.json();
}

/**
 * Fetch user groups from backend.
 */
export async function getGroups(token: string): Promise<any[]> {
  const res = await fetch(`${BACKEND_URL}/api/groups`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  return res.json();
}
