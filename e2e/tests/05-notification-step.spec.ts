import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 5: Notification Step Configuration', () => {

  test('SC-NOTIF-01: Thêm Notification Step, kiểm tra subtitle rút gọn và CollapsibleNote', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Gửi thông báo');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Gửi thông báo');

    // Subtitle must be shortened to "Gửi thông báo tự động"
    await expect(designerPage.getByText('Gửi thông báo tự động')).toBeVisible();

    // Section headers
    await expect(designerPage.getByText(/Kênh gửi thông báo/i)).toBeVisible();
    await expect(designerPage.getByText(/Gửi khi nào \(Trigger\)/i)).toBeVisible();

    // Collapsible note exists and is collapsed by default
    const collapsibleHeader = designerPage.locator('button').filter({ hasText: /Yêu cầu cấu hình kỹ thuật theo từng kênh/i });
    await expect(collapsibleHeader).toBeVisible();
    await expect(collapsibleHeader).toHaveAttribute('aria-expanded', 'false');
  });

  test('SC-NOTIF-02: Cấu hình kênh IN_APP + lưu thông báo', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo In-app');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo In-app');

    // Title template
    const titleInput = designerPage.locator('input').filter({ hasText: '' }).nth(0);
    // Find input by value or label
    const titleField = designerPage.locator('input[value*="Thông báo"], input:near(:text("Tiêu đề"))').first();
    if (await titleField.isVisible()) {
      await titleField.fill('Thông báo yêu cầu {{requestCode}}');
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-NOTIF-03: Bật kênh EMAIL', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo Email');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo Email');

    // Click Email Notification card
    const emailCard = designerPage.locator('button, div').filter({ hasText: /Email Notification/i }).first();
    await emailCard.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-NOTIF-04: Bật kênh WEBHOOK và nhập URL đích', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo Webhook');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo Webhook');

    // Click Webhook Notification card
    const webhookCard = designerPage.locator('button, div').filter({ hasText: /Webhook Notification/i }).first();
    await webhookCard.click();

    // URL input should appear
    const urlInput = designerPage.getByPlaceholder(/https:\/\/api.example.com\/webhook/i);
    await expect(urlInput).toBeVisible();
    await urlInput.fill('https://hooks.slack.com/services/T00/B00/XXXX');

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-NOTIF-05: Mở và đóng CollapsibleNote yêu cầu kỹ thuật', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo Notes');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo Notes');

    const collapsibleBtn = designerPage.locator('button').filter({ hasText: /Yêu cầu cấu hình kỹ thuật theo từng kênh/i });
    await expect(collapsibleBtn).toHaveAttribute('aria-expanded', 'false');

    // Click to expand
    await collapsibleBtn.click();
    await expect(collapsibleBtn).toHaveAttribute('aria-expanded', 'true');
    await expect(designerPage.getByText(/Email cần SMTP đã cấu hình/i)).toBeVisible();

    // Click to collapse
    await collapsibleBtn.click();
    await expect(collapsibleBtn).toHaveAttribute('aria-expanded', 'false');
    await expect(designerPage.getByText(/Email cần SMTP đã cấu hình/i)).not.toBeVisible();
  });

  test('SC-NOTIF-06: Chọn Content Template preset', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo Template');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo Template');

    // Click template "Cần xử lý"
    const templateCard = designerPage.locator('button, div').filter({ hasText: /Cần xử lý/i }).first();
    if (await templateCard.isVisible()) {
      await templateCard.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-NOTIF-07: Thêm người nhận User cụ thể', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'Thông báo Recipient');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Thông báo Recipient');

    // Search user
    const searchInput = designerPage.getByPlaceholder(/Tìm người nhận/i);
    if (await searchInput.isVisible()) {
      await searchInput.click();
      await searchInput.fill('Admin');
      const item = designerPage.locator('button').filter({ hasText: /Admin/i }).first();
      await item.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

});
