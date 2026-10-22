import { defineConfig, devices } from "@playwright/test";

/**
 * Screenshot capture project. It does not seed data: scripts/capture-screenshots.sh starts the
 * stack, seeds it through the API and then runs `npm run screenshots` here.
 */
export const BASE_URL = process.env.BASE_URL ?? "http://localhost:3000";
export const API_URL = process.env.API_URL ?? "http://localhost:8080";
export const PROMETHEUS_URL = process.env.PROMETHEUS_URL ?? "http://localhost:9090";

export default defineConfig({
  testDir: ".",
  testMatch: /screenshots\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 60_000,
  expect: { timeout: 20_000 },
  reporter: [["list"]],
  outputDir: "test-results",
  use: {
    baseURL: BASE_URL,
    headless: true,
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: "light",
    locale: "en-GB",
    timezoneId: "UTC",
    ignoreHTTPSErrors: true,
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "screenshots",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1440, height: 900 },
        deviceScaleFactor: 1,
      },
    },
  ],
});
