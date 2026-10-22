import { useState } from "react";

export function JsonToggle({ value, label = "Raw JSON" }: { value: unknown; label?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        className="btn small"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        data-testid="json-toggle"
      >
        {open ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
      </button>
      {open && (
        <pre className="json" data-testid="json-raw">
          {JSON.stringify(value, null, 2)}
        </pre>
      )}
    </>
  );
}
