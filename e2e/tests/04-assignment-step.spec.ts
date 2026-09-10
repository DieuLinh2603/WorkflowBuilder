import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 4: Assignment Step Configuration', () => {

  test.beforeEach(async ({ designerPage }) => {
    await designerPage.evaluate(() => {
      localStorage.removeItem('wf_banner_group_info');
    });
  });

  test('SC-ASGN-01: Thêm Assignment Step và kiểm tra không còn subtitle lặp tiêu đề', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'ASSIGNMENT', 'Phân công xử lý');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Phân công xử lý');

    // Verify Title is present
    await expect(designerPage.getByRole('heading', { name: /cấu hình: assignment step/i })).toBeVisible();

    // Verify redundant subtitle has been removed
    await expect(designerPage.getByText('Thiết lập thông tin cho bước giao việc trong quy trình')).not.toBeVisible();

    // Verify User / Group tabs
    await expect(designerPage.getByRole('button', { name: 'User cụ thể' })).toBeVisible();
    await expect(designerPage.getByRole('button', { name: 'Nhóm người thực hiện' })).toBeVisible();
  });

  test('SC-ASGN-02: Giao việc cho User cụ thể', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'ASSIGNMENT', 'Giao user');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Giao user');

    // Default tab is User cụ thể
    const searchInput = designerPage.getByPlaceholder(/Tìm người thực hiện.../i);
    await searchInput.click();
    await searchInput.fill('Admin');

    const item = designerPage.locator('button').filter({ hasText: /Admin/i }).first();
    await expect(item).toBeVisible();
    await item.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-ASGN-03: Giao việc cho Nhóm và verify DismissibleBanner hiển thị', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'ASSIGNMENT', 'Giao nhóm');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Giao nhóm');

    // Click tab Nhóm người thực hiện
    await designerPage.getByRole('button', { name: 'Nhóm người thực hiện' }).click();

    // Banner Group info must be visible
    const banner = designerPage.getByText(/Group là danh sách User do Admin quản lý tại/i);
    await expect(banner).toBeVisible();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-ASGN-04: Dismiss Group info banner và verify persistence', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'ASSIGNMENT', 'Giao nhóm banner');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Giao nhóm banner');

    await designerPage.getByRole('button', { name: 'Nhóm người thực hiện' }).click();
    const banner = designerPage.getByText(/Group là danh sách User do Admin quản lý tại/i);
    await expect(banner).toBeVisible();

    // Click dismiss button ✕
    const dismissBtn = designerPage.locator('button[title="Không nhắc lại"]').first();
    await dismissBtn.click();
    await expect(banner).not.toBeVisible();

    // Verify localStorage
    const stored = await designerPage.evaluate(() => localStorage.getItem('wf_banner_group_info'));
    expect(stored).toBe('true');

    // Switch tab and switch back, banner stays dismissed
    await designerPage.getByRole('button', { name: 'User cụ thể' }).click();
    await designerPage.getByRole('button', { name: 'Nhóm người thực hiện' }).click();
    await expect(banner).not.toBeVisible();
  });

  test('SC-ASGN-05: Thêm Custom Input Field trong Assignment Step', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'ASSIGNMENT', 'Giao có form');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Giao có form');

    // Click "+ Thêm field"
    await designerPage.getByRole('button', { name: /\+ Thêm field/i }).click();
    await designerPage.getByPlaceholder(/Tên hiển thị/i).fill('Kết quả xử lý');
    await designerPage.getByRole('button', { name: /^Thêm$/i }).click();

    await expect(designerPage.getByText('Kết quả xử lý')).toBeVisible();
  });

});
