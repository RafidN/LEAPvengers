import { expect, test } from '@playwright/test';
import { mockApi, seedAuthSession, USERS } from '../helpers/mock-api';

test.describe('analyst :: insights page', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
    await seedAuthSession(page, USERS.priya);
  });

  test('loads client insights page', async ({ page }) => {
    await page.goto('/insights');

    await expect(page.getByRole('heading', { name: 'Client Insights' })).toBeVisible();
  });

  test('loads trading activity report page', async ({ page }) => {
    await page.goto('/reports');

    await expect(page.getByRole('heading', { name: 'Trading Activity' })).toBeVisible();
  });
});
