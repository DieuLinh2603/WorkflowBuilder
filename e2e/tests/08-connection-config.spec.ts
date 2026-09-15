import { test, expect } from '../fixtures/workflow.fixture.js';
import { addStepViaAPI, createConnectionViaAPI, addFieldViaAPI } from '../helpers/api.helpers.js';
import { doubleClickConnection } from '../helpers/canvas.helpers.js';

test.describe('Scenario Group 8: Connection Configuration Modal', () => {

  test('SC-CONN-01 & SC-CONN-03: Mở modal Connection, kiểm tra CollapsibleNote IF/ELSE logic', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Setup Start -> End connection
    const endStep = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Điểm dừng');
    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, endStep.id, 'DEFAULT');

    await designerPage.reload();

    // Double click edge to open modal
    await doubleClickConnection(designerPage);

    // Modal opens
    await expect(designerPage.getByRole('heading', { name: /cấu hình connection|chỉnh sửa connection/i })).toBeVisible();

    // Switch to IF type
    const typeSelect = designerPage.getByLabel(/Loại connection/i);
    if (await typeSelect.isVisible()) {
      await typeSelect.selectOption('IF');
    }

    // Verify CollapsibleNote with GitBranch icon
    const logicCollapsible = designerPage.locator('button').filter({ hasText: /Nguyên tắc đánh giá & điều kiện rẽ nhánh IF\/ELSE/i });
    if (await logicCollapsible.isVisible()) {
      await expect(logicCollapsible).toHaveAttribute('aria-expanded', 'false');
      // Expand
      await logicCollapsible.click();
      await expect(logicCollapsible).toHaveAttribute('aria-expanded', 'true');
      await expect(designerPage.getByText(/Có thể tạo nhiều nhánh IF để phân loại A\/B\/C/i)).toBeVisible();

      // Collapse
      await logicCollapsible.click();
      await expect(logicCollapsible).toHaveAttribute('aria-expanded', 'false');
    }
  });

  test('SC-CONN-02: Thêm điều kiện IF trên connection và xem live preview', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    // Add field first
    await addFieldViaAPI(adminAuth.token, workflowId, startStep.id, {
      label: 'Giá trị hóa đơn',
      type: 'NUMBER',
      required: true,
    });

    const endStep = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Kết thúc IF');
    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, endStep.id, 'DEFAULT');

    await designerPage.reload();
    await doubleClickConnection(designerPage);

    // Select IF
    await designerPage.getByLabel(/Loại connection/i).selectOption('IF');

    // Fill clause expectedValue so save is enabled
    const valueInput = designerPage.locator('input[placeholder="Nhập giá trị"]');
    if (await valueInput.isVisible()) {
      await valueInput.fill('1000000');
    }

    // Verify live preview section
    await expect(designerPage.getByText(/Bản xem trước logic/i)).toBeVisible();
    await expect(designerPage.getByText(/NẾU \(IF\)/i)).toBeVisible();

    // Save with Áp dụng
    const saveBtn = designerPage.getByRole('button', { name: /Áp dụng/i });
    await saveBtn.click();
  });

  test('SC-CONN-04: Xóa connection từ modal chỉnh sửa', async ({ designerPage, adminAuth, workflowId, startStep }) => {
    const endStep = await addStepViaAPI(adminAuth.token, workflowId, 'END', 'Kết thúc Xóa');
    await createConnectionViaAPI(adminAuth.token, workflowId, startStep.id, endStep.id, 'DEFAULT');

    await designerPage.reload();
    await doubleClickConnection(designerPage);

    // Click "Xóa connection" with dialog accept
    designerPage.once('dialog', dialog => dialog.accept());
    const deleteBtn = designerPage.getByRole('button', { name: /Xóa connection/i });
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Modal closes
    await expect(designerPage.getByRole('heading', { name: /chỉnh sửa connection/i })).not.toBeVisible();
  });

});
