# e2e — documentation screenshots

Playwright project that captures the eight images in `../docs/images/` from a running demo stack.
It is deliberately dumb: it does not start or seed anything. `scripts/capture-screenshots.sh`
(owned by the root project) brings the stack up, seeds synthetic data through the API, then calls
`npm run screenshots` here.

## Environment variables

| Variable         | Default                 | Used for                                          |
|------------------|-------------------------|---------------------------------------------------|
| `BASE_URL`       | `http://localhost:3000` | the frontend (nginx container or `vite dev`)      |
| `API_URL`        | `http://localhost:8080` | backend: payment lookup and the Swagger UI capture |
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus graph / targets capture                |

## Run

```bash
npm ci
npm run install-browsers   # once; downloads Chromium for the pinned Playwright version
npm run screenshots        # playwright test --project=screenshots
```

Chromium, 1440x900, device scale factor 1, headless, 1 retry, `list` reporter. Each test waits for
its `data-testid` target to contain real data (total payments > 0, at least three timeline events,
a reconciliation run with records, four health cards with UP/DEGRADED/DOWN pills, ...). If the UI
is empty the test fails and no image is written, so a blank page can never land in the docs.

| Image                      | Source                                                           |
|----------------------------|------------------------------------------------------------------|
| `01-dashboard.png`         | `/`                                                              |
| `02-create-payment.png`    | `/payments/new`, form filled, before submit                      |
| `03-payment-detail.png`    | detail of the `INV-DEMO-0003` payment (retried one if present)   |
| `04-payment-events.png`    | same payment, timeline + audit wrapper (`locator.screenshot`)    |
| `05-reconciliation.png`    | `/reconciliation` after a run exists                             |
| `06-system-health.png`     | `/health`                                                        |
| `07-swagger-api.png`       | `API_URL/swagger-ui/index.html`, Payments tag expanded           |
| `08-prometheus-metrics.png`| `PROMETHEUS_URL/graph?g0.expr=payment_created_total` (or `/targets`) |

The header clock is hidden before each capture; everything else is left as rendered.
