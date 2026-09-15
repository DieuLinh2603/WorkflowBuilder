import { Page } from '@playwright/test';
import { test as authTest, expect } from './auth.fixture.js';
import { createWorkflow, deleteWorkflow, getWorkflowSteps, WorkflowResponse, StepResponse } from '../helpers/api.helpers.js';

export interface WorkflowFixture {
  workflow: WorkflowResponse;
  workflowId: string;
  steps: StepResponse[];
  startStep: StepResponse;
  designerPage: Page;
}

export const test = authTest.extend<WorkflowFixture>({
  workflow: async ({ adminAuth }, use) => {
    const wfName = `[PW-Test] Workflow ${Date.now()}`;
    const wf = await createWorkflow(adminAuth.token, wfName);
    try {
      await use(wf);
    } finally {
      await deleteWorkflow(adminAuth.token, wf.id);
    }
  },

  workflowId: async ({ workflow }, use) => {
    await use(workflow.id);
  },

  steps: async ({ adminAuth, workflowId }, use) => {
    const steps = await getWorkflowSteps(adminAuth.token, workflowId);
    await use(steps);
  },

  startStep: async ({ steps }, use) => {
    const start = steps.find(s => s.type === 'START');
    if (!start) throw new Error('Workflow created without START step');
    await use(start);
  },

  designerPage: async ({ page, setupAuth, workflowId }, use) => {
    await setupAuth(page);
    await page.goto(`/workflows/${workflowId}/design`);
    await page.waitForLoadState('domcontentloaded');
    // Ensure canvas and sidebar are loaded
    await expect(page.locator('.react-flow, .w-\\[250px\\]').first()).toBeVisible({ timeout: 15_000 });
    await use(page);
  },
});

export { expect };
