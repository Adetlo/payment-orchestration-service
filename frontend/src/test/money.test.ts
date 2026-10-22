import { describe, expect, it } from "vitest";
import { formatAmount, formatMoney, validateAmount } from "../lib/money";

describe("money formatting (string based, no float math)", () => {
  it("keeps the decimal string exactly as given and groups thousands", () => {
    expect(formatAmount("125.50")).toBe("125.50");
    expect(formatAmount("1000000.00")).toBe("1,000,000.00");
    expect(formatAmount("1234567.89")).toBe("1,234,567.89");
    expect(formatAmount("0.10")).toBe("0.10");
    expect(formatAmount("5000")).toBe("5,000");
    expect(formatAmount("-999.99")).toBe("-999.99");
  });

  it("prefixes the ISO currency code and tolerates missing values", () => {
    expect(formatMoney("125.50", "GBP")).toBe("GBP 125.50");
    expect(formatMoney("5000", "JPY")).toBe("JPY 5,000");
    expect(formatMoney(null, "EUR")).toBe("EUR —");
    expect(formatMoney("12.3", null)).toBe("12.3");
  });

  it("passes through values it cannot parse rather than coercing them", () => {
    expect(formatAmount("abc")).toBe("abc");
    expect(formatAmount("1e3")).toBe("1e3");
  });
});

describe("validateAmount", () => {
  it("enforces scale per currency", () => {
    expect(validateAmount("125.50", "GBP")).toBeNull();
    expect(validateAmount("125.5", "GBP")).toMatch(/exactly 2 decimal/);
    expect(validateAmount("5000", "JPY")).toBeNull();
    expect(validateAmount("5000.00", "JPY")).toMatch(/no decimal/);
  });

  it("rejects zero, negatives, garbage and amounts above the demo limit", () => {
    expect(validateAmount("0.00", "GBP")).toMatch(/greater than zero/);
    expect(validateAmount("-1.00", "GBP")).toMatch(/positive decimal/);
    expect(validateAmount("", "GBP")).toMatch(/required/);
    expect(validateAmount("1000000.00", "GBP")).toBeNull();
    expect(validateAmount("1000000.01", "GBP")).toMatch(/exceeds/);
    expect(validateAmount("12345678.00", "GBP")).toMatch(/exceeds/);
  });
});
