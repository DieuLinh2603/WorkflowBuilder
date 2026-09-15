import { test, expect } from '../fixtures/workflow.fixture.js';
import { addStepViaAPI, createConnectionViaAPI } from '../helpers/api.helpers.js';
import { openStepPanel } from '../helpers/panel.helpers.js';

test.describe('Scenario Group 9: Delete Step Modal', () => {

  test('SC-DEL-01: Xóa step độc lập không có connection', async ({ designerPage, adminAuth, workflowId }) => {
    // Add standalone step
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Step Rời');
    await designerPage.reload();

    // Open step panel
    await openStepPanel(designerPage, 'Step Rời');

    // Click trash button in panel header
    const trashBtn = designerPage.locator('aside button[title*="Xóa"]').first();
    await trashBtn.click();

    // DeleteStepModal appears
    await expect(designerPage.getByRole('heading', { name: /xác nhận xóa step/i })).toBeVisible();

    // Confirm delete
    await designerPage.getByRole('button', { name: /xóa step/i }).last().click();

    // Step removed from sidebar
    await expect(designerPage.locator('.w-\\[250px\\]').getByText('Step Rời')).not.toBeVisible();
  });

  test('SC-DEL-02: Xóa step có connection — chọn tự động kết nối lại (reconnect)', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Setup Start -> Approval -> End
    const appr = await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt Reconnect', { x: 450, y: 200 });
    const end = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'End Reconnect', { x: 700, y: 200 });

    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, appr.id, 'DEFAULT');
    await createConnectionViaAPI(adminAuth.token, workflowId, appr.id, end.id, 'APPROVE');

    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt Reconnect');

    // Click Delete step
    const trashBtn = designerPage.locator('aside button[title*="Xóa"]').first();
    await trashBtn.click();

    // Verify modal has reconnect options
    await expect(designerPage.getByText(/Tự động kết nối lại/i)).toBeVisible();

    // Select reconnect option
    const reconnectRadio = designerPage.locator('label').filter({ hasText: /Tự động kết nối lại/i });
    await reconnectRadio.click();

    // Confirm
    await designerPage.getByRole('button', { name: /xóa step/i }).last().click();

    // Appr step is removed
    await expect(designerPage.locator('.w-\\[250px\\]').getByText('Duyệt Reconnect')).not.toBeVisible();
  });

  test('SC-DEL-03: Xóa step — không kết nối lại', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    const appr = await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt Ngắt', { x: 450, y: 200 });
    const end = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'End Ngắt', { x: 700, y: 200 });

    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, appr.id, 'DEFAULT');
    await createConnectionViaAPI(adminAuth.token, workflowId, appr.id, end.id, 'APPROVE');

    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt Ngắt');

    const trashBtn = designerPage.locator('aside button[title*="Xóa"]').first();
    await trashBtn.click();

    // Select "Không kết nối lại"
    const noReconnectRadio = designerPage.locator('label').filter({ hasText: /Không kết nối lại/i });
    await noReconnectRadio.click();

    await designerPage.getByRole('button', { name: /xóa step/i }).last().click();
    await expect(designerPage.locator('.w-\\[250px\\]').getByText('Duyệt Ngắt')).not.toBeVisible();
  });

});
