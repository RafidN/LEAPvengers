import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { Theme } from '../services/theme.service';
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

const LIGHT_PALETTE: ChartPalette = {
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
};

const DARK_PALETTE: ChartPalette = {
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
};

const BLUE_ORANGE_LIGHT: ChartPalette = {
  ...LIGHT_PALETTE,
  series1: '#2563eb',
  series1Hover: '#1d4ed8',
  series2: '#0f766e',
  series2Hover: '#115e59',
  up: '#1d4ed8',
  down: '#b45309',
  upFill: 'rgba(29, 78, 216, 0.12)',
  downFill: 'rgba(180, 83, 9, 0.12)',
  upText: '#1d4ed8',
  downText: '#b45309'
};

const BLUE_ORANGE_DARK: ChartPalette = {
  ...DARK_PALETTE,
  series1: '#4aa8ff',
  series1Hover: '#58a6ff',
  series2: '#4cc9a0',
  series2Hover: '#79e2bd',
  up: '#58a6ff',
  down: '#ffb347',
  upFill: 'rgba(88, 166, 255, 0.15)',
  downFill: 'rgba(255, 179, 71, 0.15)',
  upText: '#58a6ff',
  downText: '#ffb347'
};

export const CHART_PALETTES: Record<Theme, ChartPalette> = {
  light: LIGHT_PALETTE,
  dark: DARK_PALETTE,
  pastel: BLUE_ORANGE_LIGHT,
  solarized: {
    ...BLUE_ORANGE_DARK,
    ink: '#d7e3e8',
    inkMuted: '#9fb8c2',
    grid: 'rgba(215, 227, 232, 0.12)',
    surface: '#052733',
    border: '#245064'
  },
  earthy: {
    ...BLUE_ORANGE_LIGHT,
    series1: '#0f766e',
    series1Hover: '#115e59',
    series2: '#2563eb',
    series2Hover: '#1d4ed8',
    up: '#0f766e',
    upText: '#0f766e'
  },
  bold: {
    ...BLUE_ORANGE_DARK,
    series1: '#38bdf8',
    series1Hover: '#7dd3fc',
    series2: '#a78bfa',
    series2Hover: '#c4b5fd',
    up: '#38bdf8',
    upText: '#38bdf8',
    surface: '#131d31',
    border: '#30456d'
  },
  vintage: {
    ...BLUE_ORANGE_LIGHT,
    series1: '#334155',
    series1Hover: '#475569',
    series2: '#0f766e',
    series2Hover: '#115e59',
    up: '#334155',
    upText: '#334155'
  }
};

export const chartPalette = (theme: Theme): ChartPalette => CHART_PALETTES[theme];

/**
 * Chart.js registration, provided by each chart component rather than the app, so Chart.js loads with the
 * pages that draw charts instead of before sign-in.
 */
export const CHART_PROVIDERS = [provideCharts(withDefaultRegisterables())];
