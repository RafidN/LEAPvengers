import { expect, test } from '@playwright/test';
import { mockApi, seedAuthSession, USERS } from '../helpers/mock-api';

test.describe('trader :: dashboard page', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
    await seedAuthSession(page, USERS.joanna);
  });

  test('renders cards and toggles total visibility', async ({ page }) => {
    await page.goto('/dashboard');

    await expect(page.getByText('Total value')).toBeVisible();
    await expect(page.getByText('Cash balance')).toBeVisible();

    const totalToggle = page.getByRole('button', { name: /total value/i }).first();
    const ariaLabelBefore = await totalToggle.getAttribute('aria-label');
    await totalToggle.click();
    if (ariaLabelBefore) {
      await expect(totalToggle).not.toHaveAttribute('aria-label', ariaLabelBefore);
    }
  });

  test('markets page filters instruments from search input', async ({ page }) => {
    await page.goto('/markets');

    const search = page.getByRole('textbox', { name: 'Search instruments' });
    await expect(search).toBeVisible();

    await search.fill('BTC');

    await expect(page.getByText('BTC-USD')).toBeVisible();
    await expect(search).toHaveValue('BTC');
  });

  test('trade page allows side and quantity entry', async ({ page }) => {
    await page.goto('/trade');

    await expect(page.getByRole('heading', { name: 'Trade' })).toBeVisible();

    await page.getByRole('button', { name: 'Sell' }).click();
    const quantity = page.getByRole('spinbutton', { name: 'Quantity' });
    await quantity.fill('2');

    await expect(quantity).toHaveValue('2');
    await expect(page.getByText('Estimated proceeds')).toBeVisible();
  });

  test('mode change in settings is reflected on trade page', async ({ page }) => {
    await page.goto('/trade');
    await expect(page.getByRole('heading', { name: 'Trade' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Advanced view' })).toBeVisible();

    await page.goto('/settings');
    const easyButton = page.getByRole('button', { name: 'Easy' });
    const advancedButton = page.getByRole('button', { name: 'Advanced' });

    await advancedButton.click();
    await expect(advancedButton).toHaveAttribute('aria-pressed', 'true');
    await expect(easyButton).toHaveAttribute('aria-pressed', 'false');

    await page.goto('/trade');
    await expect(page.getByRole('button', { name: 'Simple view' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Advanced view' })).toHaveCount(0);

    await page.goto('/settings');
    await easyButton.click();
    await expect(easyButton).toHaveAttribute('aria-pressed', 'true');
    await expect(advancedButton).toHaveAttribute('aria-pressed', 'false');

    await page.goto('/trade');
    await expect(page.getByRole('heading', { name: 'Trade' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Advanced view' })).toBeVisible();
  });

  test('theme change in settings updates app theme class', async ({ page }) => {
    await page.goto('/settings');

    const themeSelect = page.locator('#settings-theme');
    await expect(themeSelect).toBeVisible();

    await themeSelect.selectOption('dark');
    const darkApplied = await page.evaluate(() => document.documentElement.classList.contains('dark'));
    expect(darkApplied).toBe(true);

    await themeSelect.selectOption('pastel');
    const pastelApplied = await page.evaluate(() => document.documentElement.classList.contains('pastel'));
    expect(pastelApplied).toBe(true);

    await themeSelect.selectOption('light');
    const lightClearedThemeClasses = await page.evaluate(() => {
      const classes = document.documentElement.classList;
      return !classes.contains('dark')
        && !classes.contains('pastel')
        && !classes.contains('solarized')
        && !classes.contains('earthy')
        && !classes.contains('bold')
        && !classes.contains('vintage');
    });
    expect(lightClearedThemeClasses).toBe(true);
  });
});
