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
| `src/app/app.routes.ts` | All routes: `/sign-in`, `/register`, `/forgot-password`, `/dashboard` |
| `src/app/app.config.ts` | App-wide providers (router, HTTP + auth interceptor, charts) |
| `src/app/pages/authentication/` | Sign-in, register, forgot password. Styles shared in `auth.css` |
| `src/app/pages/landing/` | Dashboard (currently mock data) |
| `src/app/pages/chart/` | Portfolio line chart used by the dashboard |
| `src/app/services/` | One service per backend area: `auth`, `history`, `instrument-search` |
| `src/app/interceptors/auth.interceptor.ts` | Adds `Authorization: Bearer <token>` to every request |
| `src/styles.css` | Global styles, fonts and the theme tokens the Spartan components use |
| `libs/ui/` | Spartan UI components (`navigation-menu`, `table`), added with `npx ng g @spartan-ng/cli:ui` |

## Conventions

- Only `AuthService` touches `localStorage`. Everything else calls `authService.getToken()` etc.
- Don't set auth headers by hand; the interceptor does it.
- New page: create `src/app/pages/<name>/`, then add it to `app.routes.ts`.
