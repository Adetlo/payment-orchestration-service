/**
 * Money formatting on string amounts. Amounts are never converted to floating point numbers:
 * the backend returns decimal strings with the correct scale, and we only group the integer part.
 */

const AMOUNT_PATTERN = /^(-)?(\d+)(?:\.(\d+))?$/;

export function groupThousands(integerPart: string): string {
  return integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

export function formatAmount(amount: string | null | undefined): string {
  if (amount === null || amount === undefined) return "—";
  const match = AMOUNT_PATTERN.exec(amount.trim());
  if (!match) return amount;
  const sign = match[1] ?? "";
  const integerPart = match[2] ?? "0";
  const fraction = match[3];
  const grouped = groupThousands(integerPart);
  return fraction === undefined ? `${sign}${grouped}` : `${sign}${grouped}.${fraction}`;
}

export function formatMoney(amount: string | null | undefined, currency: string | null): string {
  const formatted = formatAmount(amount);
  return currency ? `${currency} ${formatted}` : formatted;
}

/** Decimal scale enforced per currency (DESIGN.md 4.1). */
export const CURRENCY_SCALE: Record<string, number> = {
  GBP: 2,
  EUR: 2,
  USD: 2,
  SGD: 2,
  JPY: 0,
  IDR: 0,
};

export const MAX_AMOUNT_INTEGER_DIGITS = 7; // 1,000,000.00 is the demo ceiling

/** Validates the textual amount for a currency without doing any float arithmetic. */
export function validateAmount(amount: string, currency: string): string | null {
  const trimmed = amount.trim();
  if (trimmed.length === 0) return "Amount is required";
  const match = /^(\d+)(?:\.(\d+))?$/.exec(trimmed);
  if (!match) return "Amount must be a positive decimal number such as 125.50";
  const integerPart = match[1] ?? "";
  const fraction = match[2] ?? "";
  const scale = CURRENCY_SCALE[currency] ?? 2;
  if (fraction.length !== scale) {
    return scale === 0
      ? `${currency} amounts must have no decimal places`
      : `${currency} amounts must have exactly ${String(scale)} decimal places`;
  }
  if (/^0+$/.test(integerPart) && (fraction.length === 0 || /^0+$/.test(fraction))) {
    return "Amount must be greater than zero";
  }
  const normalisedInt = integerPart.replace(/^0+(?=\d)/, "");
  if (normalisedInt.length > MAX_AMOUNT_INTEGER_DIGITS) {
    return "Amount exceeds the 1,000,000.00 demo limit";
  }
  if (normalisedInt.length === MAX_AMOUNT_INTEGER_DIGITS) {
    // 1,000,000.00 is allowed; anything above it with seven integer digits is not.
    const aboveLimit = normalisedInt !== "1000000" || /[1-9]/.test(fraction);
    if (aboveLimit) return "Amount exceeds the 1,000,000.00 demo limit";
  }
  return null;
}
