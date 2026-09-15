import { Page, expect } from '@playwright/test';

/**
 * Locate a step node on the ReactFlow canvas by matching its label text or step type.
 */
export function getCanvasNode(page: Page, labelOrType: string) {
  return page.locator('.react-flow__node').filter({ hasText: new RegExp(labelOrType, 'i') });
}

/**
 * Click a node on the canvas.
 */
export async function clickCanvasNode(page: Page, labelOrType: string) {
  const node = getCanvasNode(page, labelOrType).first();
  await expect(node).toBeVisible({ timeout: 10_000 });
  await node.click();
}

/**
 * Double click a node on the canvas (which opens its configuration panel).
 */
export async function doubleClickCanvasNode(page: Page, labelOrType: string) {
  const node = getCanvasNode(page, labelOrType).first();
  await expect(node).toBeVisible({ timeout: 10_000 });
  await node.dblclick();
}

/**
 * Add a step via the UI plus button on a canvas node.
 */
export async function addStepViaUI(
  page: Page,
  fromNodeText: string,
  stepType: 'APPROVAL' | 'REVIEW' | 'ASSIGNMENT' | 'NOTIFICATION' | 'SYSTEM_ACTION' | 'END'
) {
  const node = getCanvasNode(page, fromNodeText).first();
  await expect(node).toBeVisible({ timeout: 10_000 });

  // Hover over node to reveal plus button if needed
  await node.hover();

  // Find the plus button attached to this node
  const plusBtn = node.locator('button[title*="Thêm"], button:has-text("+")').first();
  await plusBtn.click({ force: true });

  // The AddStepPopup modal/popover appears
  const popup = page.locator('.bg-white.rounded-xl.shadow-xl, [data-step-popup]');
  await expect(popup).toBeVisible({ timeout: 5_000 });

  // Map stepType to visible label in AddStepPopup
  const stepLabels: Record<string, RegExp> = {
    APPROVAL: /Phê duyệt|Approval/i,
    REVIEW: /Xem xét|Review/i,
    ASSIGNMENT: /Giao việc|Assignment/i,
    NOTIFICATION: /Thông báo|Notification/i,
    SYSTEM_ACTION: /Hệ thống|System Action/i,
    END: /Kết thúc|End/i,
  };

  const option = popup.locator('button').filter({ hasText: stepLabels[stepType] || stepType }).first();
  await option.click();
}

/**
 * Click an edge / connection on the canvas to open the ConnectionConfigModal.
 */
export async function doubleClickConnection(page: Page, fromNodeText?: string) {
  const edge = page.locator('.react-flow__edge').first();
  await expect(edge).toBeVisible({ timeout: 10_000 });
  await edge.dblclick({ force: true });
}
