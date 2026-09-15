import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel, savePanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 3: Review Step Configuration', () => {

  test.beforeEach(async ({ designerPage }) => {
    await designerPage.evaluate(() => {
      localStorage.removeItem('wf_banner_review_canvas');
    });
  });

  test('SC-REV-01: Thêm Review Step và mở panel', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'REVIEW', 'Xem xét hồ sơ');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Xem xét hồ sơ');

    await expect(designerPage.getByRole('heading', { name: /cấu hình: review step/i })).toBeVisible();
    await expect(designerPage.getByText(/Người review/i)).toBeVisible();
    await expect(designerPage.getByText(/Nội dung cần review/i)).toBeVisible();
    await expect(designerPage.getByText(/Kết quả review/i)).toBeVisible();
  });

  test('SC-REV-02: Cấu hình reviewer FIXED_USER và REQUIRE_APPROVAL', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'REVIEW', 'Review pháp lý');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Review pháp lý');

    // Pick user
    const searchInput = designerPage.getByPlaceholder(/Tìm người xử lý/i);
    await searchInput.click();
    await searchInput.fill('Admin');
    const userOption = designerPage.locator('button').filter({ hasText: /Admin/i }).first();
    await userOption.click();

    // Select review content
    const contentSelect = designerPage.locator('select').filter({ hasText: /Nội dung request|Tài liệu/i });
    if (await contentSelect.isVisible()) {
      await contentSelect.selectOption('REQUEST_CONTENT');
    }

    // Select REQUIRE_APPROVAL
    const reqApprovalOption = designerPage.locator('input[type="radio"][value="REQUIRE_APPROVAL"], label:has-text("Yêu cầu review đạt") input[type="radio"]').first();
    const reqApprovalLabel = designerPage.locator('label').filter({ hasText: /Yêu cầu review đạt/i }).first();
    if (await reqApprovalOption.isVisible()) {
      await reqApprovalOption.check();
    } else if (await reqApprovalLabel.isVisible()) {
      await reqApprovalLabel.click();
    }

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-REV-03: Chọn resultMode COMMENT_ONLY', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'REVIEW', 'Review góp ý');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Review góp ý');

    // Select COMMENT_ONLY
    const commentOnlyRadio = designerPage.locator('label').filter({ hasText: /Chỉ ghi nhận review, không chặn luồng/i });
    await commentOnlyRadio.click();

    await savePanel(designerPage);
    await expect(designerPage.getByText(/Đã lưu cấu hình/i)).toBeVisible();
  });

  test('SC-REV-04: Dismiss banner canvas guide và verify persistence', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'REVIEW', 'Review Banner');
    await designerPage.reload();
    await openStepPanel(designerPage, 'Review Banner');

    // Banner is initially visible with icon MousePointerClick
    const banner = designerPage.getByText(/Trên canvas, nối nhánh Đạt tới bước tiếp theo/i);
    await expect(banner).toBeVisible();

    // Click dismiss button
    const dismissBtn = designerPage.locator('button[title="Không nhắc lại"]').first();
    await dismissBtn.click();
    await expect(banner).not.toBeVisible();

    // Verify localStorage
    const stored = await designerPage.evaluate(() => localStorage.getItem('wf_banner_review_canvas'));
    expect(stored).toBe('true');

    // Reload and verify stays hidden
    await designerPage.reload();
    await openStepPanel(designerPage, 'Review Banner');
    await expect(banner).not.toBeVisible();
  });

});
