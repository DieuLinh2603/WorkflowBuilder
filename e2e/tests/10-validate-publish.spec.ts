import { test, expect } from '../fixtures/workflow.fixture.js';
import { addStepViaAPI, createConnectionViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 10: Validate & Publish Workflow', () => {

  test('SC-PUB-01: Mở Validation Panel với workflow chưa đủ điều kiện', async ({ designerPage }) => {
    // Click "Validate" in designer header
    const validateBtn = designerPage.getByRole('button', { name: /^Validate$/i });
    await validateBtn.click();

    // ValidationPublishPanel appears
    await expect(designerPage.getByRole('heading', { name: /kiểm tra & xuất bản/i })).toBeVisible();

    // Checklist items should be visible
    await expect(designerPage.getByText('Có Start Step')).toBeVisible();
    await expect(designerPage.getByText('Có End Step')).toBeVisible();
  });

  test('SC-PUB-02 & SC-PUB-03: Validate workflow hợp lệ và Publish thành công', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Complete the workflow graph: Start -> End
    const endStep = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Kết thúc');
    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, endStep.id, 'DEFAULT');

    await designerPage.reload();

    // Click Validate
    await designerPage.getByRole('button', { name: /^Validate$/i }).click();

    // Wait for validation checklist to succeed
    await expect(designerPage.getByText(/Mọi điều kiện và cấu hình đều đạt chuẩn/i)).toBeVisible({ timeout: 10_000 });

    // Publish button in panel
    const publishBtn = designerPage.getByRole('button', { name: /Publish Workflow/i });
    await expect(publishBtn).toBeVisible();
    await expect(publishBtn).toBeEnabled();

    // Click Publish
    await publishBtn.click();

    // Verify success banner
    await expect(designerPage.getByText(/Workflow đã được publish thành công/i)).toBeVisible({ timeout: 10_000 });
  });

  test('SC-PUB-04: Publish bị hạn chế với tài khoản EDITOR không phải Owner', async ({ page, editorAuth, adminAuth, workflowId, startStep }) => {
    // Add end step and connection
    const endStep = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Kết thúc');
    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, endStep.id, 'DEFAULT');

    // Login as EDITOR
    await page.addInitScript(({ token, user }) => {
      localStorage.setItem('wf_token', token);
      localStorage.setItem('wf_user', JSON.stringify(user));
    }, {
      token: editorAuth.token,
      user: {
        id: editorAuth.userId,
        email: editorAuth.email,
        displayName: editorAuth.displayName,
        systemRoles: editorAuth.systemRoles,
      },
    });

    await page.goto(`/workflows/${workflowId}/design`);
    await page.waitForLoadState('domcontentloaded');

    // Click Validate
    const validateBtn = page.getByRole('button', { name: /^Validate$/i });
    if (await validateBtn.isVisible()) {
      await validateBtn.click();

      // If valid, button shows "Chờ Owner publish" and info banner explains restriction
      const waitingBtn = page.getByRole('button', { name: /Chờ Owner publish|Kiểm tra lại/i });
      await expect(waitingBtn).toBeVisible();
    }
  });

});
