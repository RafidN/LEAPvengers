/**
 * Number formats for text built in TypeScript. Templates use the matching pipes:
 * money is `| currency` and quantities are `| number: '1.0-2'`. The whole site shows at most 2 decimals.
 */

/** $1,234.50 */
export const formatMoney = (value: number) =>
  value.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 1,234 or 0.05: no padding zeros, at most 2 decimals. */
export const formatQuantity = (value: number) =>
  value.toLocaleString('en-US', { maximumFractionDigits: 2 });
