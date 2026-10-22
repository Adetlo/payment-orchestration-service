import type { Currency } from "../api/types";
import { validateAmount } from "./money";

export const ACCOUNT_PATTERN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{8,30}$/;
export const REFERENCE_PATTERN = /^[A-Za-z0-9 ./-]{1,35}$/;
export const IDEMPOTENCY_KEY_MAX = 128;

export interface CreatePaymentFormValues {
  payerAccount: string;
  payeeAccount: string;
  amount: string;
  currency: Currency;
  reference: string;
  idempotencyKey: string;
  demoFailure: string;
}

export type FormErrors = Partial<Record<keyof CreatePaymentFormValues, string>>;

export function validateCreatePayment(values: CreatePaymentFormValues): FormErrors {
  const errors: FormErrors = {};
  const payer = values.payerAccount.trim();
  const payee = values.payeeAccount.trim();

  if (!payer) errors.payerAccount = "Payer account is required";
  else if (!ACCOUNT_PATTERN.test(payer))
    errors.payerAccount = "Use a synthetic IBAN-like value, e.g. GB12PAYLAB00000001";

  if (!payee) errors.payeeAccount = "Payee account is required";
  else if (!ACCOUNT_PATTERN.test(payee))
    errors.payeeAccount = "Use a synthetic IBAN-like value, e.g. GB12PAYLAB00000002";
  else if (payer && payer === payee) errors.payeeAccount = "Payee must differ from payer";

  const amountError = validateAmount(values.amount, values.currency);
  if (amountError) errors.amount = amountError;

  const reference = values.reference.trim();
  if (!reference) errors.reference = "Reference is required";
  else if (!REFERENCE_PATTERN.test(reference))
    errors.reference = "1–35 characters: letters, digits, space, dot, slash or hyphen";

  if (values.idempotencyKey.length > IDEMPOTENCY_KEY_MAX)
    errors.idempotencyKey = `Idempotency key must be at most ${String(IDEMPOTENCY_KEY_MAX)} characters`;

  return errors;
}
