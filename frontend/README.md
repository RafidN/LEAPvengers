# Frontend

Angular 22 app (standalone components, SSR). API calls to `/api` are proxied to the backend on `localhost:8081` (see `proxy.conf.json`).

```bash
npm install
npm start          # http://localhost:4200
npm test           # Vitest unit tests
npm run build      # production build into dist/
```

## Where things are

| Path | What |
| --- | --- |
| `src/app/app.routes.ts` | All routes. Public: `/sign-in`, `/register`, `/forgot-password`. Signed in: `/dashboard`, `/markets`, `/clients` |
| `src/app/app.routes.server.ts` | Public pages are prerendered; signed-in pages render in the browser (they need the token) |
| `src/app/app.config.ts` | App-wide providers (router, HTTP + auth interceptor, charts) |
| `src/app/guards/auth.guard.ts` | Sends signed-out or expired users to `/sign-in` |
| `src/app/layout/shell/` | Top navigation shared by every signed-in page |
| `src/app/pages/authentication/` | Sign-in, register, forgot password. `auth.css` re-themes them dark green |
| `src/app/pages/dashboard/` | Totals, holdings, recent activity and a live watchlist |
| `src/app/pages/markets/` | Instrument search and price history chart |
| `src/app/pages/clients/` | Client segments (analyst view) |
| `src/app/components/price-chart/` | Reusable price line chart |
| `src/app/services/` | One service per backend area: `auth`, `history`, `instrument-search`, `client-segment` |
| `src/app/interceptors/auth.interceptor.ts` | Adds `Authorization: Bearer <token>` to every request |
| `src/styles.css` | Tailwind, fonts and the theme tokens the Spartan components use |
| `libs/ui/` | Spartan UI components: `alert`, `badge`, `button`, `card`, `input`, `label`, `table` |

## Conventions

- Build UI from Spartan components (`hlmBtn`, `hlmCard`, `hlmInput`, `hlmTable`, ...). Add a new one with
  `npx ng g @spartan-ng/cli:ui <name>`, and don't edit files in `libs/ui/`.
- Style with Tailwind classes in the template; Spartan merges them over its own. To re-theme a whole page,
  override the tokens from `styles.css` on the page's `:host` (see `auth.css`).
- Keep page state that changes in HTTP callbacks in signals. The app has no zone.js, so plain fields
  set inside `subscribe` won't re-render.
- Only `AuthService` touches `localStorage`. Everything else calls `authService.getToken()` etc.
- Don't set auth headers by hand; the interceptor does it.
- New page: create `src/app/pages/<name>/`, then add it to `app.routes.ts`.
