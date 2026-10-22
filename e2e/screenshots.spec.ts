import { expect, test, type Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { API_URL, PROMETHEUS_URL } from "./playwright.config";

/*
 * One test per documentation image. Every test waits for the matching data-testid to hold real
 * data and fails loudly if the UI is empty: a blank page must never be written to docs/images.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
const IMAGES_DIR = resolve(HERE, "../docs/images");
const TARGET_REFERENCE = "INV-DEMO-0003";

interface PaymentSummary {
  paymentId: string;
  status: string;
  reference: string;
  retryCount: number;
  createdAt: string;
}

interface PaymentsPage {
  items: PaymentSummary[];
  totalElements: number;
}

function imagePath(name: string): string {
  mkdirSync(IMAGES_DIR, { recursive: true });
  return resolve(IMAGES_DIR, name);
}

/** Hide elements that change every second so repeated captures stay comparable. */
async function freezeDynamicChrome(page: Page): Promise<void> {
  await page.addStyleTag({
    content: `
      [data-testid="clock"] { visibility: hidden !important; }
      *, *::before, *::after { transition: none !important; animation: none !important; caret-color: transparent !important; }
    `,
  });
}

async function openPage(page: Page, path: string): Promise<void> {
  await page.emulateMedia({ reducedMotion: "reduce", colorScheme: "light" });
  await page.goto(path, { waitUntil: "domcontentloaded" });
  await expect(page.locator(".app-shell")).toBeVisible();
  await freezeDynamicChrome(page);
}

async function parseInteger(page: Page, testId: string): Promise<number> {
  const text = (await page.getByTestId(testId).innerText()).replace(
    /[^0-9]/g,
    "",
  );
  return text.length > 0 ? Number(text) : Number.NaN;
}

async function fetchJson<T>(page: Page, url: string): Promise<T> {
  const response = await page.request.get(url, { failOnStatusCode: true });
  return (await response.json()) as T;
}

async function findTargetPayment(page: Page): Promise<PaymentSummary> {
  // Page through the newest payments (up to 2,000) so the seeded payment is found even when a load
  // test has added thousands of newer records.
  const candidates: PaymentSummary[] = [];
  for (let pageNo = 0; pageNo < 40 && candidates.length === 0; pageNo += 1) {
    const body = await fetchJson<PaymentsPage>(
      page,
      `${API_URL}/api/v1/payments?size=50&page=${pageNo}`,
    );
    candidates.push(
      ...body.items.filter((p) => p.reference === TARGET_REFERENCE),
    );
    if (body.items.length < 50) break;
  }
  expect(
    candidates.length,
    `expected at least one payment with reference ${TARGET_REFERENCE}; the seed script must run first`,
  ).toBeGreaterThan(0);
  const withRetries = candidates.find((p) => p.retryCount > 0);
  const completed = candidates.find((p) => p.status === "COMPLETED");
  const chosen = withRetries ?? completed ?? candidates[0];
  if (!chosen) throw new Error("unreachable: no candidate payment");
  return chosen;
}

async function waitForPaymentDetail(
  page: Page,
  payment: PaymentSummary,
): Promise<void> {
  await openPage(page, `/payments/${payment.paymentId}`);
  const detail = page.getByTestId("payment-detail");
  await expect(detail).toHaveAttribute("data-loaded", "true");
  await expect(detail).toContainText(payment.paymentId);
  await expect(detail).toContainText(TARGET_REFERENCE);
  await expect(page.getByTestId("payment-retries")).toContainText(
    "Retry count",
  );
  // A processed payment has at least Received -> Validated -> terminal: three events minimum.
  await expect
    .poll(async () => page.getByTestId("timeline-event").count(), {
      message: "timeline should show at least 3 events",
    })
    .toBeGreaterThanOrEqual(3);
  await expect
    .poll(async () => page.getByTestId("audit-row").count(), {
      message: "audit table should have at least one row",
    })
    .toBeGreaterThanOrEqual(1);
}

test.describe("documentation screenshots", () => {
  test("01-dashboard", async ({ page }) => {
    await openPage(page, "/");
    await expect(page.getByTestId("dashboard-stats")).toHaveAttribute(
      "data-loaded",
      "true",
    );
    await expect
      .poll(() => parseInteger(page, "stat-total-value"), {
        message: "dashboard total payments must be > 0 (seed the stack first)",
      })
      .toBeGreaterThan(0);
    await expect
      .poll(() => parseInteger(page, "stat-successful-value"))
      .toBeGreaterThan(0);
    await expect(
      page.getByTestId("stat-health").getByTestId("health-pill"),
    ).toHaveText(/UP|DEGRADED|DOWN/);
    await expect
      .poll(
        async () =>
          page
            .getByTestId("dashboard-recent")
            .getByTestId("payment-row")
            .count(),
        {
          message: "recent payments table must contain rows",
        },
      )
      .toBeGreaterThanOrEqual(1);
    await page.screenshot({
      path: imagePath("01-dashboard.png"),
      fullPage: false,
    });
  });

  test("02-create-payment", async ({ page }) => {
    await openPage(page, "/payments/new");
    const form = page.getByTestId("create-payment-form");
    await expect(form).toBeVisible();
    await form.getByLabel("Payer account").fill("GB12PAYLAB00000001");
    await form.getByLabel("Payee account").fill("GB12PAYLAB00000002");
    await form.getByLabel("Amount").fill("125.50");
    await form.getByLabel("Currency").selectOption("GBP");
    await form.getByLabel("Reference").fill("INV-DEMO-SCREENSHOT");
    await form.getByLabel(/Idempotency-Key/).fill("demo-screenshot-001");
    const demoSelect = page.getByTestId("demo-failure-select");
    await expect(demoSelect).toBeVisible();
    await demoSelect.selectOption("settlement-timeout");
    await expect(demoSelect).toHaveValue("settlement-timeout");
    await expect(page.getByTestId("create-payment-result")).toBeVisible();
    // Captured before submit: the form is the subject, not the response.
    await page.screenshot({
      path: imagePath("02-create-payment.png"),
      fullPage: false,
    });
  });

  test("03-payment-detail", async ({ page }) => {
    const payment = await findTargetPayment(page);
    await waitForPaymentDetail(page, payment);
    await page.screenshot({
      path: imagePath("03-payment-detail.png"),
      fullPage: false,
    });
  });

  test("04-payment-events", async ({ page }) => {
    const payment = await findTargetPayment(page);
    await waitForPaymentDetail(page, payment);
    const wrapper = page.getByTestId("payment-events");
    await wrapper.scrollIntoViewIfNeeded();
    await expect(wrapper.getByTestId("payment-timeline")).toBeVisible();
    await expect(wrapper.getByTestId("payment-audit")).toBeVisible();
    // Scroll the events section to the top of the viewport and capture a consistent 1440x900 frame
    // showing the start of the timeline next to the audit trail.
    await wrapper.evaluate((el) => el.scrollIntoView({ block: "start" }));
    await page.evaluate(() => window.scrollBy(0, -16));
    await page.waitForTimeout(300);
    await page.screenshot({ path: imagePath("04-payment-events.png") });
  });

  test("05-reconciliation", async ({ page }) => {
    await openPage(page, "/reconciliation");
    const summary = page.getByTestId("reconciliation-summary");
    await expect(
      summary,
      "a reconciliation run must exist before capturing",
    ).toHaveAttribute("data-loaded", "true");
    await expect
      .poll(() => parseInteger(page, "summary-checked"), {
        message: "last run must have checked at least one record",
      })
      .toBeGreaterThan(0);
    await expect(page.getByTestId("classification-bar")).toBeVisible();
    await expect
      .poll(async () =>
        page.getByTestId("reconciliation-runs").getByTestId("run-row").count(),
      )
      .toBeGreaterThanOrEqual(1);
    await expect
      .poll(
        async () =>
          page
            .getByTestId("reconciliation-items")
            .getByTestId("item-row")
            .count(),
        {
          message: "selected run must list its items",
        },
      )
      .toBeGreaterThanOrEqual(1);
    await expect(page.getByTestId("run-reconciliation")).toBeEnabled();
    await page.screenshot({
      path: imagePath("05-reconciliation.png"),
      fullPage: false,
    });
  });

  test("06-system-health", async ({ page }) => {
    await openPage(page, "/health");
    const grid = page.getByTestId("system-health");
    await expect(grid).toHaveAttribute("data-loaded", "true");
    for (const component of ["api", "postgresql", "kafka", "redis"]) {
      const card = page.getByTestId(`health-${component}`);
      await expect(card).toBeVisible();
      await expect(card.getByTestId("health-pill")).toHaveText(
        /^(UP|DEGRADED|DOWN)$/,
      );
    }
    await expect(
      page.getByTestId("health-api").getByTestId("health-pill"),
    ).toHaveText("UP");
    await page.screenshot({
      path: imagePath("06-system-health.png"),
      fullPage: false,
    });
  });

  test("07-swagger-api", async ({ page }) => {
    await page.goto(`${API_URL}/swagger-ui/index.html`, {
      waitUntil: "domcontentloaded",
    });
    await expect(page.locator(".swagger-ui").first()).toBeVisible();
    await expect(page.locator(".information-container")).toBeVisible();
    const paymentsTag = page
      .locator(".opblock-tag", { hasText: /payments/i })
      .first();
    await expect(
      paymentsTag,
      "Swagger UI must list a Payments tag",
    ).toBeVisible();
    const section = page
      .locator(".opblock-tag-section", { has: paymentsTag })
      .first();
    if (!(await section.evaluate((el) => el.classList.contains("is-open")))) {
      await paymentsTag.click();
    }
    await expect(section).toHaveClass(/is-open/);
    await expect(section.locator(".opblock").first()).toBeVisible();
    await page.addStyleTag({
      content: "*, *::before, *::after { transition: none !important; }",
    });
    await page.screenshot({
      path: imagePath("07-swagger-api.png"),
      fullPage: false,
    });
  });

  test("08-prometheus-metrics", async ({ page }) => {
    const graphUrl = `${PROMETHEUS_URL}/graph?g0.expr=payment_received_total&g0.tab=1&g0.range_input=15m`;
    let rendered = false;
    try {
      await page.goto(graphUrl, { waitUntil: "domcontentloaded" });
      await expect(
        page.getByText("payment_received_total").first(),
      ).toBeVisible({ timeout: 20_000 });
      // Prefer waiting for a result row so the capture shows a value, not an empty table.
      await expect
        .poll(
          async () => page.locator("text=/payment_received_total\\{/").count(),
          {
            timeout: 20_000,
          },
        )
        .toBeGreaterThanOrEqual(1);
      rendered = true;
    } catch (error) {
      test.info().annotations.push({
        type: "fallback",
        description: `graph page unavailable (${String(error)}); capturing /targets instead`,
      });
    }
    if (!rendered) {
      await page.goto(`${PROMETHEUS_URL}/targets`, {
        waitUntil: "domcontentloaded",
      });
      await expect(
        page.getByText(/actuator\/prometheus/).first(),
      ).toBeVisible();
      await expect(page.getByText(/\bUP\b/).first()).toBeVisible();
    }
    await page.screenshot({
      path: imagePath("08-prometheus-metrics.png"),
      fullPage: false,
    });
  });
});
