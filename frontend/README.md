# Frontend — Payment Orchestration Lab demo UI

Vite + React 18 + TypeScript (strict) single-page app that makes the orchestration service's
behaviour visible: dashboard, create payment, payments list/detail (timeline + audit),
reconciliation and system health. Hand-written CSS, no UI framework. Synthetic data only.

## Run locally

```bash
npm ci
npm run dev          # http://localhost:3000, proxies /api and /actuator to http://localhost:8080
```

Set `VITE_PROXY_TARGET` to point the dev proxy elsewhere, or `VITE_API_BASE` to change the API
prefix baked into the build (default `/api/v1`).

## Quality gates

```bash
npm run lint         # eslint: typescript-eslint (type-checked), react-hooks, jsx-a11y
npm run typecheck    # tsc --noEmit (app + node configs)
npm run format:check # prettier
npm test -- --run    # vitest + Testing Library (jsdom), fetch is mocked
npm run build        # tsc + vite build -> dist/
```

## Docker

```bash
docker build -t payment-orchestration-frontend:local .
docker run --rm -p 3000:8080 payment-orchestration-frontend:local
```

The image is `nginxinc/nginx-unprivileged` on port 8080. `nginx.conf` serves the SPA with history
fallback, proxies `/api/` and `/actuator/` to the compose service `backend:8080`, adds security
headers and a conservative CSP, enables gzip and answers `/healthz`.

## Layout

```
src/api         typed fetch client (problem+json -> ApiError), endpoint functions, wire types
src/hooks       usePolling (3 s, pauses when the tab is hidden)
src/lib         money (string based, no float math), formatting, status tones, validation
src/components  layout, pills, tables, timeline, classification bar
src/pages       one component per route
src/test        vitest suites and fixtures
```

Key elements carry `data-testid` attributes (`dashboard-stats`, `create-payment-form`,
`payment-timeline`, `reconciliation-summary`, `system-health`, ...) so the Playwright project in
`../e2e` can wait for real data before capturing screenshots.
