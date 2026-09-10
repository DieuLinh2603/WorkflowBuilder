import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 7: End Step Configuration', () => {

  test('SC-END-01: Mở End Step Panel, kiểm tra không còn subtitle thừa và business rule note vẫn hiển thị', async ({ designerPage, adminAuth, workflowId }) => {
    // Add End step
    await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Kết thúc quy trình');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Kết thúc quy trình');

    // Title exists
    await expect(designerPage.getByRole('heading', { name: /cấu hình: end step/i })).toBeVisible();

    // Redundant subtitle has been removed
    await expect(designerPage.getByText('Thiết lập kết quả khi workflow đi tới điểm kết thúc')).not.toBeVisible();

    // 4 Outcome cards
    await expect(designerPage.getByText('Hoàn tất')).toBeVisible();
    await expect(designerPage.getByText('Đã duyệt')).toBeVisible();
    await expect(designerPage.getByText('Từ chối')).toBeVisible();
    await expect(designerPage.getByText('Đã hủy')).toBeVisible();

    // Business rule note at bottom must remain 100% visible
    await expect(designerPage.getByText(/End Step không có connection đi ra/i)).toBeVisible();
  });

  test('SC-END-02 & SC-END-03: Chọn outcome APPROVED và REJECTED', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'END', 'End Approved');
    await designerPage.reload();
    await openStepPanel(designerPage, 'End Approved');

    // Click "Đã duyệt"
    await designerPage.locator('button').filter({ hasText: 'Đã duyệt' }).click();
    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();

    // Click "Từ chối"
    await designerPage.locator('button').filter({ hasText: 'Từ chối' }).click();
    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-END-04: Bật thông báo cho người tạo và CSV recipient', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'END', 'End Notify');
    await designerPage.reload();
    await openStepPanel(designerPage, 'End Notify');

    // Check / toggle notify options
    const notifyRequester = designerPage.locator('label').filter({ hasText: 'Thông báo kết quả cho người tạo request' });
    if (await notifyRequester.isVisible()) {
      await notifyRequester.click();
    }

    const notifyRecipient = designerPage.locator('label').filter({ hasText: 'Thông báo cho người nhận của dòng CSV' });
    if (await notifyRecipient.isVisible()) {
      await notifyRecipient.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-END-05: Sử dụng template variables trong nội dung thông báo', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'END', 'End Template');
    await designerPage.reload();
    await openStepPanel(designerPage, 'End Template');

    // Fill message with variables
    const messageArea = designerPage.locator('textarea').first();
    await messageArea.fill('Yêu cầu {{requestCode}} của workflow {{workflowName}} đã kết thúc thành công.');

    // Verify hint note about template variables
    await expect(designerPage.getByText(/Có thể dùng biến/i)).toBeVisible();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

});
