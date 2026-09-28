import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Public pages are prerendered at build time
  { path: 'sign-in', renderMode: RenderMode.Prerender },
  { path: 'register', renderMode: RenderMode.Prerender },
  { path: 'forgot-password', renderMode: RenderMode.Prerender },

  // Signed-in pages need the token from localStorage, which only exists in the browser
  { path: '**', renderMode: RenderMode.Client }
];
