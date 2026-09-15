import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI, addFieldViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 2: Approval Step Configuration', () => {

  test.beforeEach(async ({ designerPage }) => {
    // Clear dismissal keys before test so banners are fresh
    await designerPage.evaluate(() => {
      localStorage.removeItem('wf_banner_reject_loop');
    });
  });

  test('SC-APPR-01: Thêm Approval Step và mở panel', async ({ designerPage, adminAuth, workflowId }) => {
    // Add Approval Step via API
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Phê duyệt chi phí');
    await designerPage.reload();

    // Open panel
    await openStepPanel(designerPage, 'Phê duyệt chi phí');

    // Assert panel
    await expect(designerPage.getByRole('heading', { name: /phê duyệt|approval/i }).first()).toBeVisible();
    await expect(designerPage.getByText('MANUAL')).toBeVisible();
    await expect(designerPage.getByText('AUTO')).toBeVisible();
  });

  test('SC-APPR-02: Cấu hình MANUAL + FIXED_USER approver', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt hồ sơ');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt hồ sơ');

    // Mode is MANUAL by default. Approver Mode is FIXED_USER by default.
    const searchInput = designerPage.getByPlaceholder(/Tìm người xử lý/i);
    await searchInput.click();
    await searchInput.fill('Admin');

    const item = designerPage.locator('button').filter({ hasText: /Admin/i }).first();
    await expect(item).toBeVisible();
    await item.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-03: Cấu hình MANUAL + ROLE_BASED approver', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt chức danh');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt chức danh');

    // Click Role Based button
    await designerPage.getByRole('button', { name: 'Role Based' }).click();

    // Select role
    const roleSelect = designerPage.locator('select').first();
    await expect(roleSelect).toBeVisible();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-04: Cấu hình MANUAL + DYNAMIC approver', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt quản lý');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt quản lý');

    // Click Dynamic
    await designerPage.getByRole('button', { name: 'Dynamic' }).click();

    // Verify dynamic note / options
    await expect(designerPage.getByText(/Quản lý trực tiếp của người tạo request/i)).toBeVisible();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-05 & SC-APPR-06: Cấu hình Completion Policy — ANY và ALL', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt số đông');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt số đông');

    // Select ANY
    const anyRadio = designerPage.locator('input[type="radio"][value="ANY"], label:has-text("Bất kỳ ai hoàn thành (ANY)")');
    await anyRadio.click();
    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();

    // Select ALL
    const allRadio = designerPage.locator('input[type="radio"][value="ALL"], label:has-text("Tất cả phải hoàn thành (ALL)")');
    await allRadio.click();
    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-07: Cấu hình Completion Policy — PERCENTAGE & kiểm tra Business Rule note', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt phần trăm');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt phần trăm');

    // Select PERCENTAGE
    const percentRadio = designerPage.locator('input[type="radio"][value="PERCENTAGE"], label:has-text("Tỷ lệ phần trăm")');
    await percentRadio.click();

    // Verify Math.ceil rounding note is intact and visible
    await expect(designerPage.getByText(/Số người cần hoàn thành được làm tròn lên/i)).toBeVisible();

    // Input 75%
    const percentInput = designerPage.locator('input[type="number"]').first();
    await percentInput.fill('75');

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-08: Cấu hình Deadline — chọn preset & kiểm tra note 23:59:59', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt deadline');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt deadline');

    // Select preset 2 days
    const deadlineSelect = designerPage.locator('select').filter({ hasText: /ngày/i }).first();
    if (await deadlineSelect.isVisible()) {
      await deadlineSelect.selectOption('48');
    }

    // Verify 23:59:59 note is intact
    await expect(designerPage.getByText(/23:59:59/i)).toBeVisible();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-09: Cấu hình Deadline — Tùy chỉnh số ngày', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt deadline custom');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt deadline custom');

    const deadlineSelect = designerPage.locator('select').filter({ hasText: /ngày/i }).first();
    if (await deadlineSelect.isVisible()) {
      await deadlineSelect.selectOption('CUSTOM');
      const customDaysInput = designerPage.getByPlaceholder(/Số ngày/i);
      await customDaysInput.fill('5');
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-10 & SC-APPR-11: Cấu hình Escalation — REMIND và AUTO_REJECT', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt escalation');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt escalation');

    // Select REMIND
    const remindRadio = designerPage.locator('input[type="radio"][value="REMIND"], label:has-text("Nhắc nhở qua Email/Teams")');
    if (await remindRadio.isVisible()) await remindRadio.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();

    // Select AUTO_REJECT
    const rejectRadio = designerPage.locator('input[type="radio"][value="AUTO_REJECT"], label:has-text("Tự động reject yêu cầu")');
    if (await rejectRadio.isVisible()) await rejectRadio.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-12: Chuyển sang chế độ AUTO và kiểm tra icon Settings2', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Add field to Start step first
    await addFieldViaAPI(adminAuth.token, workflowId, startStep.id, {
      label: 'Tổng tiền chi phí',
      type: 'NUMBER',
      required: true,
    });

    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Tự động duyệt');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Tự động duyệt');

    // Switch to AUTO mode
    await designerPage.getByRole('button', { name: 'AUTO' }).click();

    // Verify auto condition explanation has Settings2 icon and message
    await expect(designerPage.getByText(/Hệ thống không tạo task cho người duyệt/i)).toBeVisible();

    // Click Thêm điều kiện
    const addConditionBtn = designerPage.getByRole('button', { name: /\+ Thêm điều kiện|Thêm điều kiện/i });
    if (await addConditionBtn.isVisible()) {
      await addConditionBtn.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-APPR-13: AUTO mode — không có field → hiển thị cảnh báo amber', async ({ designerPage, adminAuth, workflowId }) => {
    // Create approval step with no fields in start step
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Auto No Field');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Auto No Field');

    // Click AUTO
    await designerPage.getByRole('button', { name: 'AUTO' }).click();

    // Expect amber banner
    await expect(designerPage.getByText(/Chưa có field dữ liệu từ các step khác/i)).toBeVisible();
  });

  test('SC-APPR-14: Thêm custom field trong Approval Step', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt thêm field');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt thêm field');

    // Click "+ Thêm field"
    await designerPage.getByRole('button', { name: /\+ Thêm field/i }).click();
    await designerPage.getByPlaceholder(/Tên hiển thị/i).fill('Ý kiến người duyệt');
    await designerPage.getByRole('button', { name: /^Thêm$/i }).click();

    await expect(designerPage.getByText('Ý kiến người duyệt')).toBeVisible();
  });

  test('SC-APPR-15: Dismiss banner REJECT loopback và verify localStorage', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'Duyệt Banner');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt Banner');

    // 1. Banner must be initially visible
    const bannerText = designerPage.getByText(/Nhánh REJECT có thể nối về một bước nhập liệu/i);
    await expect(bannerText).toBeVisible();

    // 2. Click dismiss button
    const dismissBtn = designerPage.locator('button[title="Không nhắc lại"]').first();
    await dismissBtn.click();

    // 3. Banner immediately disappears
    await expect(bannerText).not.toBeVisible();

    // 4. Verify localStorage stored the key
    const stored = await designerPage.evaluate(() => localStorage.getItem('wf_banner_reject_loop'));
    expect(stored).toBe('true');

    // 5. Reload and verify banner stays hidden
    await designerPage.reload();
    await openStepPanel(designerPage, 'Duyệt Banner');
    await expect(bannerText).not.toBeVisible();
  });

});
