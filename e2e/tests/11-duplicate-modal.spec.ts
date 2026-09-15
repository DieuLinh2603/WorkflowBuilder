import { test, expect } from '../fixtures/workflow.fixture.js';

test.describe('Scenario Group 11: Duplicate Workflow Modal', () => {

  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      try {
        localStorage.removeItem('wf_banner_duplicate_scope');
      } catch (_) {}
    });
  });

  test('SC-DUP-01: Mở modal nhân bản và kiểm tra DismissibleBanner phạm vi nhân bản', async ({ page, setupAuth, workflow }) => {
    await setupAuth(page);
    await page.goto('/workflows');
    await page.waitForLoadState('domcontentloaded');

    // Find row of our workflow and click duplicate button
    const row = page.locator('tr').filter({ hasText: workflow.name });
    await expect(row).toBeVisible({ timeout: 10_000 });

    const duplicateBtn = row.locator('button[title="Duplicate"]').first();
    await duplicateBtn.click();

    // Modal opens
    await expect(page.getByRole('heading', { name: /nhân bản workflow/i })).toBeVisible();

    // DismissibleBanner visible
    const banner = page.getByText(/Bản sao được tạo ở trạng thái DRAFT, phiên bản v1.0./i);
    await expect(banner).toBeVisible();
    await expect(page.getByText(/Instance, lịch sử xử lý và Data Binding không được sao chép/i)).toBeVisible();
  });

  test('SC-DUP-02: Dismiss DismissibleBanner trong Duplicate modal và verify persistence', async ({ page, setupAuth, workflow }) => {
    await setupAuth(page);
    await page.goto('/workflows');
    await page.waitForLoadState('domcontentloaded');

    const row = page.locator('tr').filter({ hasText: workflow.name });
    await row.locator('button[title="Duplicate"]').first().click();

    const banner = page.getByText(/Bản sao được tạo ở trạng thái DRAFT, phiên bản v1.0./i);
    await expect(banner).toBeVisible();

    // Click ✕ to dismiss
    const dismissBtn = page.locator('.fixed button[title="Không nhắc lại"]').first();
    await dismissBtn.click();
    await expect(banner).not.toBeVisible();

    // Verify localStorage
    const stored = await page.evaluate(() => localStorage.getItem('wf_banner_duplicate_scope'));
    expect(stored).toBe('true');

    // Close and reopen modal
    await page.getByRole('button', { name: 'Hủy' }).click();
    await row.locator('button[title="Duplicate"]').first().click();

    // Banner stays hidden
    await expect(banner).not.toBeVisible();
  });

  test('SC-DUP-03: Nhân bản workflow thành công', async ({ page, setupAuth, workflow }) => {
    await setupAuth(page);
    await page.goto('/workflows');
    await page.waitForLoadState('domcontentloaded');

    const row = page.locator('tr').filter({ hasText: workflow.name });
    await row.locator('button[title="Duplicate"]').first().click();

    const copyName = `[Copy] ${workflow.name}`;
    const nameInput = page.getByPlaceholder(/VD: Quy trình tuyển dụng/i);
    await nameInput.fill(copyName);

    // Submit
    const submitBtn = page.getByRole('button', { name: /Nhân bản & chỉnh sửa/i });
    await submitBtn.click();

    // Should navigate to the designer of the new copy
    await page.waitForURL(/\/workflows\/[^/]+\/design/, { timeout: 15_000 });
    await expect(page.locator('.react-flow, .w-\\[250px\\]').first()).toBeVisible();
  });

});
