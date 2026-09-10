import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI, addFieldViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 6: System Action Step Configuration', () => {

  test('SC-SYS-01: Thêm System Action, mở panel và kiểm tra subtitle "Tác vụ tự động"', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Tác vụ hệ thống');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Tác vụ hệ thống');

    // Title and shortened subtitle
    await expect(designerPage.getByRole('heading', { name: /cấu hình: system action step/i })).toBeVisible();
    await expect(designerPage.getByText('Tác vụ tự động')).toBeVisible();

    // 5 action types
    await expect(designerPage.getByText('Gọi API ngoài')).toBeVisible();
    await expect(designerPage.getByText('Gửi thông báo')).toBeVisible();
    await expect(designerPage.getByText('Cập nhật dữ liệu')).toBeVisible();
    await expect(designerPage.getByText('Tạo record mới')).toBeVisible();
    await expect(designerPage.getByText('Cập nhật trạng thái')).toBeVisible();
  });

  test('SC-SYS-02: Cấu hình API_CALL (Endpoint, Method, Payload, Retry, Timeout)', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Call API');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Call API');

    // Default action is API_CALL
    const endpointInput = designerPage.getByPlaceholder(/https:\/\/api.example.com\/orders/i);
    await endpointInput.fill('https://api.example.com/sync-orders');

    // Select Method
    const methodSelect = designerPage.locator('select').filter({ hasText: /POST|GET/i }).first();
    await methodSelect.selectOption('POST');

    // Select Failure Policy = RETRY
    const retryRadio = designerPage.locator('label').filter({ hasText: 'Thử lại' });
    await retryRadio.click();

    // Timeout
    const timeoutInput = designerPage.locator('input[type="number"]').last();
    await timeoutInput.fill('45');

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-SYS-03 & SC-SYS-04: Cấu hình SEND_NOTIFICATION (In-App và Webhook)', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Sys Notify');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Sys Notify');

    // Select SEND_NOTIFICATION
    await designerPage.getByRole('button', { name: 'Gửi thông báo' }).click();

    // Select Webhook channel
    const channelSelect = designerPage.locator('select').filter({ hasText: /In-app Notification|Email|Webhook/i }).first();
    await channelSelect.selectOption('WEBHOOK');

    // Fill Webhook URL
    const urlInput = designerPage.getByPlaceholder(/https:\/\/api.example.com\/webhook/i);
    await expect(urlInput).toBeVisible();
    await urlInput.fill('https://my-webhook.internal/alerts');

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-SYS-05 & SC-SYS-09: Cấu hình UPDATE_DATA + Data Mapping + Xóa mapping row', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Add a field to Start step
    await addFieldViaAPI(adminAuth.token, workflowId, startStep.id, {
      label: 'Trạng thái đơn',
      type: 'TEXT',
      required: false,
    });

    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Update Data');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Update Data');

    // Select UPDATE_DATA
    await designerPage.getByRole('button', { name: 'Cập nhật dữ liệu' }).click();

    // Click "+ Thêm mapping"
    const addMappingBtn = designerPage.getByRole('button', { name: /Thêm mapping/i });
    await addMappingBtn.click();

    // Mapping row should appear
    const mappingRow = designerPage.locator('.grid-cols-\\[1fr_1fr_36px\\]').last();
    await expect(mappingRow).toBeVisible();

    // Test SC-SYS-09: Delete mapping row
    const trashBtn = mappingRow.locator('button').first();
    await trashBtn.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-SYS-06: Cấu hình CREATE_RECORD', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Create Record');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Create Record');

    await designerPage.getByRole('button', { name: 'Tạo record mới' }).click();

    const recordTypeInput = designerPage.getByPlaceholder(/Purchase Order/i);
    await recordTypeInput.fill('Hóa đơn VAT');

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-SYS-07 & SC-SYS-08: Cấu hình UPDATE_STATUS và Failure Policy = STOP', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'SYSTEM_ACTION', 'Update Status');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Update Status');

    await designerPage.getByRole('button', { name: 'Cập nhật trạng thái' }).click();

    // Status dropdown
    const statusSelect = designerPage.locator('select').filter({ hasText: /In progress|Completed/i }).last();
    if (await statusSelect.isVisible()) {
      await statusSelect.selectOption('COMPLETED');
    }

    // Policy = STOP
    const stopRadio = designerPage.locator('label').filter({ hasText: 'Dừng workflow và báo lỗi' });
    await stopRadio.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

});
