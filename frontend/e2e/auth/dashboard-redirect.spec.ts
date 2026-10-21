import { expect, test } from '@playwright/test';
import { mockApi } from '../helpers/mock-api';

test.describe('auth :: dashboard page', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
  });

  test('redirects anonymous users to sign-in', async ({ page }) => {
    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/sign-in$/);
    await expect(page.getByRole('heading', { name: 'Welcome to S.H.I.E.L.D.' })).toBeVisible();
  });

  test('can sign in through demo persona and reach dashboard', async ({ page }) => {
    await page.goto('/sign-in');

    await page.getByRole('button', { name: /Joanna/i }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Sign out/i })).toBeVisible();
  });
});
