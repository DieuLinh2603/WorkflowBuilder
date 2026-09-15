import { Page, expect } from '@playwright/test';
import { doubleClickCanvasNode } from './canvas.helpers.js';

/**
 * Open configuration panel for a step by clicking it in the left sidebar
 * (or double-clicking on the canvas if sidebar item is not immediately found).
 */
export async function openStepPanel(page: Page, labelOrType: string) {
  // Sidebar container is the 250px left column containing "CÁC BƯỚC ĐÃ THIẾT LẬP"
  const sidebar = page.locator('.w-\\[250px\\]');
  const sidebarItem = sidebar.locator('div[title*="đổi tên"]').filter({ hasText: new RegExp(labelOrType, 'i') }).first();

  if (await sidebarItem.isVisible({ timeout: 3000 }).catch(() => false)) {
    await sidebarItem.click();
  } else {
    const textItem = sidebar.getByText(new RegExp(labelOrType, 'i')).first();
    if (await textItem.isVisible({ timeout: 2000 }).catch(() => false)) {
      await textItem.click();
    } else {
      // Fallback: double click on the canvas node
      await doubleClickCanvasNode(page, labelOrType);
    }
  }

  // Verify panel opens on the right side
  const panel = page.locator('aside, .w-\\[540px\\], .w-\\[560px\\]').first();
  await expect(panel).toBeVisible({ timeout: 8_000 });
  return panel;
}

/**
 * Click "Lưu cấu hình" in the open step panel and wait for response/success toast.
 */
export async function savePanel(page: Page) {
  const saveBtn = page.getByRole('button', { name: /lưu cấu hình/i }).first();
  await expect(saveBtn).toBeVisible({ timeout: 5000 });
  await expect(saveBtn).toBeEnabled({ timeout: 5000 });

  const [response] = await Promise.all([
    page.waitForResponse(res => res.url().includes('/config') && ['PUT', 'POST'].includes(res.request().method()), {
      timeout: 10_000,
    }).catch(() => null),
    saveBtn.click(),
  ]);

  // Wait a brief moment for toast or UI update
  await page.waitForTimeout(300);
  return response;
}

/**
 * Close the open step panel.
 */
export async function closePanel(page: Page) {
  const closeBtn = page.locator('aside, .w-\\[540px\\], .w-\\[560px\\]').locator('button').filter({ has: page.locator('svg') }).first();
  if (await closeBtn.isVisible().catch(() => false)) {
    await closeBtn.click();
  }
}
