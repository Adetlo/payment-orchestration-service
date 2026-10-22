import type { PaymentStatus } from "../api/types";

export type Tone = "green" | "red" | "amber" | "blue" | "grey";

/** DESIGN.md section 5 colour mapping. Unknown statuses fall back to grey. */
export function paymentStatusTone(status: string): Tone {
  switch (status) {
    case "COMPLETED":
      return "green";
    case "FAILED":
    case "REJECTED":
      return "red";
    case "PROCESSING":
    case "RETRY_PENDING":
      return "amber";
    case "RECEIVED":
    case "VALIDATED":
      return "blue";
    case "CANCELLED":
    default:
      return "grey";
  }
}

export function healthTone(status: string | null | undefined): Tone {
  switch (status) {
    case "UP":
      return "green";
    case "DEGRADED":
      return "amber";
    case "DOWN":
      return "red";
    default:
      return "grey";
  }
}

export function classificationTone(classification: string): Tone {
  switch (classification) {
    case "MATCHED":
      return "green";
    case "AMOUNT_MISMATCH":
    case "STATUS_MISMATCH":
      return "red";
    case "MISSING_INTERNAL":
    case "MISSING_EXTERNAL":
      return "amber";
    default:
      return "grey";
  }
}

export function reconciliationStatusTone(status: string | null | undefined): Tone {
  switch (status) {
    case "COMPLETED":
      return "green";
    case "RUNNING":
      return "amber";
    case "FAILED":
      return "red";
    default:
      return "grey";
  }
}

export const CANCELLABLE_STATUSES: readonly PaymentStatus[] = [
  "RECEIVED",
  "VALIDATED",
  "RETRY_PENDING",
];

export function isTerminal(status: string): boolean {
  return (
    status === "COMPLETED" || status === "REJECTED" || status === "FAILED" || status === "CANCELLED"
  );
}
