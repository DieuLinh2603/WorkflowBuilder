import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addFieldViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 1: Start Step Configuration', () => {

  test('SC-START-01: Hiển thị panel khi mở Start Node', async ({ designerPage }) => {
    // 1. Open Start Step panel
    await openStepPanel(designerPage, 'Start');

    // 2. Verify panel UI elements & updated microcopy
    await expect(designerPage.getByRole('heading', { name: /cấu hình: start step/i })).toBeVisible();
    await expect(designerPage.getByText('Bắt đầu & Thu thập dữ liệu')).toBeVisible();
    await expect(designerPage.getByPlaceholder(/VD: Vui lòng đính kèm báo giá trước khi gửi/i)).toBeVisible();

    // Verify default options
    const scopeSelect = designerPage.locator('select').first();
    await expect(scopeSelect).toHaveValue('ALL_EMPLOYEES');

    // Toggle withdrawal is present and on by default
    const withdrawalToggle = designerPage.locator('text=Cho phép người gửi thu hồi');
    await expect(withdrawalToggle).toBeVisible();

    // Request form section visible with intact business rule note
    await expect(designerPage.getByText(/REQUEST FORM/i)).toBeVisible();
    await expect(designerPage.getByText(/Đây là dữ liệu gốc của request/i)).toBeVisible();
  });

  test('SC-START-02: Lưu hướng dẫn cho người tạo request', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    const instructionArea = designerPage.getByPlaceholder(/VD: Vui lòng đính kèm báo giá trước khi gửi/i);
    const testInstruction = `Hướng dẫn test tự động ${Date.now()}`;
    await instructionArea.fill(testInstruction);

    // Click Save
    await savePanel(designerPage);

    // Verify success message / persistence
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-START-03: Giới hạn người tạo request — validation khi chưa chọn đối tượng', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    // Select SPECIFIC_GROUP_ROLE
    const scopeSelect = designerPage.locator('select').first();
    await scopeSelect.selectOption('SPECIFIC_GROUP_ROLE');

    // Try saving without selecting any user/group/role
    const saveBtn = designerPage.getByRole('button', { name: /lưu cấu hình/i });
    await saveBtn.click();

    // Expect validation error
    await expect(
      designerPage.getByText('Hãy chọn ít nhất một user, nhóm hoặc vai trò được phép tạo request.')
    ).toBeVisible();
  });

  test('SC-START-04: Thêm User vào danh sách được phép tạo request', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    const scopeSelect = designerPage.locator('select').first();
    await scopeSelect.selectOption('SPECIFIC_GROUP_ROLE');

    // Search user dropdown
    const userSearchInput = designerPage.getByPlaceholder(/Tìm kiếm user.../i);
    await userSearchInput.click();
    await userSearchInput.fill('Admin');

    // Select first option from dropdown
    const dropdownOption = designerPage.locator('button').filter({ hasText: /Admin/i }).first();
    await expect(dropdownOption).toBeVisible();
    await dropdownOption.click();

    // User should appear as selected chip
    await expect(designerPage.locator('.bg-orange-50').filter({ hasText: /Admin/i }).first()).toBeVisible();

    // Save
    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-START-05: Thêm field dữ liệu tùy chỉnh (Custom Input Field)', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    // Click "+ Thêm field"
    await designerPage.getByRole('button', { name: /\+ Thêm field/i }).click();

    // AddFieldModal opens
    await expect(designerPage.getByRole('heading', { name: /thêm field/i })).toBeVisible();

    // Fill form
    await designerPage.getByPlaceholder(/Tên hiển thị \(VD: Số tiền\)/i).fill('Số tiền đề xuất');
    await designerPage.locator('select').filter({ hasText: /Văn bản|Số/i }).first().selectOption('NUMBER');
    
    // Check required toggle
    const reqCheckbox = designerPage.locator('input[type="checkbox"]').first();
    await reqCheckbox.check();

    // Save field
    await designerPage.getByRole('button', { name: /^Thêm$/i }).click();

    // Field should appear in request form
    await expect(designerPage.getByText('Số tiền đề xuất')).toBeVisible();
    await expect(designerPage.getByText(/Bắt buộc/i).first()).toBeVisible();
  });

  test('SC-START-06: Xóa field dữ liệu', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Setup: add a field via API first
    await addFieldViaAPI(adminAuth.token, workflowId, startStep.id, {
      label: 'Field cần xóa',
      type: 'TEXT',
      required: false,
    });

    await designerPage.reload();
    await openStepPanel(designerPage, 'Start');

    // Locate field row and delete button
    const fieldRow = designerPage.locator('div').filter({ hasText: 'Field cần xóa' }).first();
    await expect(fieldRow).toBeVisible();

    // Intercept confirm dialog
    designerPage.once('dialog', async (dialog) => {
      await dialog.accept();
    });

    const deleteBtn = fieldRow.locator('button').filter({ has: designerPage.locator('svg') }).first();
    await deleteBtn.click();

    // Field should disappear
    await expect(designerPage.getByText('Field cần xóa')).not.toBeVisible();
  });

  test('SC-START-07: Bật chế độ BATCH và chọn recipient field', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Add email field first
    await addFieldViaAPI(adminAuth.token, workflowId, startStep.id, {
      label: 'Email người nhận',
      type: 'TEXT',
      required: true,
    });

    await designerPage.reload();
    await openStepPanel(designerPage, 'Start');

    // Switch mode to BATCH
    const batchRadio = designerPage.locator('input[value="BATCH"]');
    await batchRadio.check();

    // Select recipient field
    const recipientSelect = designerPage.locator('select').filter({ hasText: /Email người nhận/i });
    if (await recipientSelect.isVisible()) {
      await recipientSelect.selectOption({ label: 'Email người nhận' });
    }

    // Enter max batch rows
    const maxBatchInput = designerPage.locator('input[type="number"]').first();
    if (await maxBatchInput.isVisible()) {
      await maxBatchInput.fill('100');
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-START-08: Tắt toggle cho phép thu hồi', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    // Toggle withdrawal button
    const toggleBtn = designerPage.locator('button[role="switch"]').filter({ hasText: /Cho phép người gửi thu hồi/i }).first();
    if (await toggleBtn.isVisible()) {
      await toggleBtn.click();
    } else {
      // If styled as checkbox
      const checkbox = designerPage.locator('input[type="checkbox"]').first();
      await checkbox.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

});
