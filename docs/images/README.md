# Images

All images in this directory are generated, never hand-edited, from the running demo stack
with deterministic seed data, so they can be regenerated at any commit.

| File | Page / source | Produced by |
|------|---------------|-------------|
| `01-dashboard.png` | UI dashboard `/` after seeding | `scripts/capture-screenshots.sh` |
| `02-create-payment.png` | UI `/payments/new` with the form filled in | `scripts/capture-screenshots.sh` |
| `03-payment-detail.png` | UI `/payments/:id` for the `settlement-timeout` payment (retries visible) | `scripts/capture-screenshots.sh` |
| `04-payment-events.png` | UI `/payments/:id` event timeline and audit trail | `scripts/capture-screenshots.sh` |
| `05-reconciliation.png` | UI `/reconciliation` showing the `with-discrepancies` run | `scripts/capture-screenshots.sh` |
| `06-system-health.png` | UI `/health` component status | `scripts/capture-screenshots.sh` |
| `07-swagger-api.png` | `http://localhost:8080/swagger-ui.html` | `scripts/capture-screenshots.sh` |
| `08-prometheus-metrics.png` | `http://localhost:8080/actuator/prometheus` (or Prometheus graph) | `scripts/capture-screenshots.sh` |
| `social-preview.png` | `docs/social-preview.html` rendered at 1280x640 | Playwright CLI, see below |

## Regenerating the screenshots

```bash
./scripts/capture-screenshots.sh            # docker compose up, wait, seed-demo.sh, Playwright, compose down
./scripts/capture-screenshots.sh --no-start # against a running stack
```

The script seeds the deterministic data set (`scripts/seed-demo.sh`, 12 payments with
idempotency keys `demo-seed-001..012` and a `with-discrepancies` reconciliation run), then runs
the Playwright project in `e2e/` (`npm run screenshots`, chromium, 1440x900, device scale
factor 1), which writes the PNG files here, and prints the list with sizes.

## Social preview

`docs/social-preview.html` is a self-contained 1280x640 page (inline CSS, no external fonts).
Render it with the Playwright CLI (installed by `e2e/`):

```bash
cd e2e && npm ci && npx playwright install chromium && cd ..
npx --prefix e2e playwright screenshot --viewport-size=1280,640 docs/social-preview.html docs/images/social-preview.png
```

Upload `docs/images/social-preview.png` as the repository's social preview image in GitHub
Settings.

## Conventions

* PNG, no transparency, light UI theme, no personal data (the data is synthetic by construction).
* Keep each file under 500 KB; crop to the content if a page is mostly empty.
* When the UI changes, regenerate all eight images in one run so they are consistent.
