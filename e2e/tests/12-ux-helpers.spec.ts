import { test, expect } from '../fixtures/workflow.fixture.js';
import { openStepPanel } from '../helpers/panel.helpers.js';
import { addStepViaAPI } from '../helpers/api.helpers.js';

test.describe('Scenario Group 12: Shared UX Components (UXHelpers)', () => {

  test('SC-UX-01: LabelWithTooltip — kiểm tra tooltip khi hover vào icon info', async ({ designerPage }) => {
    await openStepPanel(designerPage, 'Start');

    // Open AddFieldModal
    await designerPage.getByRole('button', { name: /\+ Thêm field/i }).click();

    // In AddFieldModal or similar, find info icon
    const infoIcon = designerPage.locator('.cursor-help, svg.text-slate-400').first();
    if (await infoIcon.isVisible()) {
      await infoIcon.hover();
      // Tooltip tooltip popover with black background appears
      const tooltip = designerPage.locator('.bg-slate-800').first();
      await expect(tooltip).toBeVisible();
    }
  });

  test('SC-UX-02: CollapsibleNote — kiểm tra hành vi mở, đóng và thuộc tính accessibility', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'NOTIFICATION', 'UX Collapsible Test');
    await designerPage.reload();
    await openStepPanel(designerPage, 'UX Collapsible Test');

    const toggleBtn = designerPage.locator('button').filter({ hasText: /Yêu cầu cấu hình kỹ thuật theo từng kênh/i });
    await expect(toggleBtn).toBeVisible();

    // 1. Initial state: collapsed, aria-expanded=false
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');

    // 2. Click to expand
    await toggleBtn.click();
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'true');
    await expect(designerPage.getByText(/Email cần SMTP đã cấu hình/i)).toBeVisible();

    // 3. Click to collapse again
    await toggleBtn.click();
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false');
    await expect(designerPage.getByText(/Email cần SMTP đã cấu hình/i)).not.toBeVisible();
  });

  test('SC-UX-03: DismissibleBanner — kiểm tra khả năng lưu trữ persistent qua page reload', async ({ designerPage, adminAuth, workflowId }) => {
    await addStepViaAPI(adminAuth.token, workflowId, 'APPROVAL', 'UX Banner Test');
    await designerPage.reload();
    await openStepPanel(designerPage, 'UX Banner Test');

    // Ensure banner visible
    const banner = designerPage.getByText(/Nhánh REJECT có thể nối về một bước nhập liệu/i);
    await expect(banner).toBeVisible();

    // Click dismiss
    await designerPage.locator('button[title="Không nhắc lại"]').first().click();
    await expect(banner).not.toBeVisible();

    // Hard refresh the page
    await designerPage.reload();
    await openStepPanel(designerPage, 'UX Banner Test');

    // Banner must stay hidden
    await expect(banner).not.toBeVisible();
  });

});
