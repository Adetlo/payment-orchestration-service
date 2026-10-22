/** Deterministic UTC formatting (no locale) so screenshots and tests are stable. */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const s = date.toISOString();
  return `${s.slice(0, 10)} ${s.slice(11, 19)} UTC`;
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const s = date.toISOString();
  return `${s.slice(11, 23)}`;
}

export function shortId(id: string | null | undefined, length = 8): string {
  if (!id) return "—";
  return id.length > length ? `${id.slice(0, length)}…` : id;
}

export function maskAccount(account: string | null | undefined): string {
  if (!account) return "—";
  if (account.length <= 6) return account;
  return `${account.slice(0, 2)}**…${account.slice(-4)}`;
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

export function stringifyDetails(details: unknown): string {
  if (details === null || details === undefined) return "";
  if (typeof details === "string") return details;
  try {
    return JSON.stringify(details);
  } catch {
    return "[unserialisable details]";
  }
}

/** datetime-local input value -> ISO instant, or undefined when empty/invalid. */
export function localInputToIso(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  return date.toISOString();
}
