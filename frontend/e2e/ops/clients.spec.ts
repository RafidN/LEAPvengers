import { expect, test } from '@playwright/test';
import { mockApi, seedAuthSession, USERS } from '../helpers/mock-api';

test.describe('ops :: clients page', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
    await seedAuthSession(page, USERS.david);
  });

  test('shows disputes and opens order details', async ({ page }) => {
    await page.goto('/clients');

    await expect(page.getByRole('heading', { name: 'Open disputes' })).toBeVisible();
    await expect(page.getByText('The fill price looks wrong.')).toBeVisible();

    await expect(page.getByRole('heading', { name: 'Client Lookup' })).toBeVisible();
    await page.getByRole('button', { name: /Olivia Carter/i }).click();

    await expect(page.getByText('Selected client trades')).toBeVisible();
    await page.getByRole('button', { name: /#5001/i }).click();
    await expect(page.getByRole('heading', { name: 'Order #5001' })).toBeVisible();
  });
});
