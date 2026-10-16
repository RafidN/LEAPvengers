import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
/**
 * Chart colours per theme. Chart.js draws on a canvas, so it can't read the CSS tokens in styles.css;
 * these mirror them. The dark series colours were checked against the dark card surface (#1c2024).
 */
export interface ChartPalette {
  /** The main line or bar series. */
  series1: string;
  series1Hover: string;
  /** A second, unrelated single-series chart on the same page. */
  series2: string;
  series2Hover: string;
  /** Price lines that ended the period up or down, and their fills. */
  up: string;
  down: string;
  upFill: string;
  downFill: string;
  /** Gain and loss text, matching the --profit and --loss tokens in styles.css. */
  upText: string;
  downText: string;
  ink: string;
  inkMuted: string;
  grid: string;
  surface: string;
  border: string;
}

export const CHART_PALETTES: Record<'light' | 'dark', ChartPalette> = {
  light: {
    series1: '#0f6a46',
    series1Hover: '#37b87b',
    series2: '#13315c',
    series2Hover: '#2b5f9e',
    up: '#0f6a46',
    down: '#8f2d2d',
    upFill: 'rgba(15, 106, 70, 0.12)',
    downFill: 'rgba(143, 45, 45, 0.12)',
    upText: '#0f6a46',
    downText: '#8f2d2d',
    ink: '#1f2328',
    inkMuted: '#6f7881',
    grid: 'rgba(31, 35, 40, 0.08)',
    surface: '#ffffff',
    border: '#d9d2c7'
  },
  dark: {
    series1: '#2f9e6a',
    series1Hover: '#3fb67f',
    series2: '#4d8fe6',
    series2Hover: '#6ea8fe',
    up: '#2f9e6a',
    down: '#d95c5c',
    upFill: 'rgba(47, 158, 106, 0.15)',
    downFill: 'rgba(217, 92, 92, 0.15)',
    upText: '#4cc38a',
    downText: '#f07a7a',
    ink: '#e7e5e0',
    inkMuted: '#9aa3ab',
    grid: 'rgba(255, 255, 255, 0.08)',
    surface: '#1c2024',
    border: '#30363c'
  }
};

/**
 * Chart.js registration, provided by each chart component rather than the app, so Chart.js loads with the
 * pages that draw charts instead of before sign-in.
 */
export const CHART_PROVIDERS = [provideCharts(withDefaultRegisterables())];
