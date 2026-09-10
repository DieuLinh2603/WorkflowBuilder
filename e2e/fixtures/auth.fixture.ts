import { test as base, Page } from '@playwright/test';
import { loginViaAPI, LoginResponse } from '../helpers/api.helpers.js';

export interface AuthFixture {
  adminAuth: LoginResponse;
  editorAuth: LoginResponse;
  setupAuth: (page: Page, authData?: LoginResponse) => Promise<void>;
}

export const test = base.extend<AuthFixture>({
  adminAuth: async ({}, use) => {
    const auth = await loginViaAPI('admin@company.com', 'admin123');
    await use(auth);
  },

  editorAuth: async ({}, use) => {
    const auth = await loginViaAPI('editor@company.com', 'editor123');
    await use(auth);
  },

  setupAuth: async ({ adminAuth }, use) => {
    const setup = async (page: Page, authData = adminAuth) => {
      await page.addInitScript(({ token, user }) => {
        localStorage.setItem('wf_token', token);
        localStorage.setItem('wf_user', JSON.stringify(user));
      }, {
        token: authData.token,
        user: {
          id: authData.userId,
          email: authData.email,
          displayName: authData.displayName,
          systemRoles: authData.systemRoles || [],
        },
      });
    };
    await use(setup);
  },
});

export { expect } from '@playwright/test';
