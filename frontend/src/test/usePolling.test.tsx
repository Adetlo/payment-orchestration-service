import { act, render, screen, waitFor } from "@testing-library/react";
import { StrictMode, useCallback } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { usePolling } from "../hooks/usePolling";

function Probe({ fn }: { fn: () => Promise<number> }) {
  const stable = useCallback(() => fn(), [fn]);
  const { data, error } = usePolling(stable, 3000);
  return (
    <div>
      <span data-testid="value">{data ?? "none"}</span>
      <span data-testid="error">{error?.message ?? ""}</span>
    </div>
  );
}

describe("usePolling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    Object.defineProperty(document, "hidden", { configurable: true, value: false });
  });

  it("fetches immediately, re-fetches on the interval and pauses while the tab is hidden", async () => {
    let calls = 0;
    const fn = vi.fn(() => Promise.resolve(++calls));
    render(<Probe fn={fn} />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(fn).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("value")).toHaveTextContent("1");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(fn).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("value")).toHaveTextContent("2");

    Object.defineProperty(document, "hidden", { configurable: true, value: true });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(9000);
    });
    expect(fn).toHaveBeenCalledTimes(2); // paused

    Object.defineProperty(document, "hidden", { configurable: true, value: false });
    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
      await Promise.resolve();
    });
    expect(fn).toHaveBeenCalledTimes(3);
    expect(screen.getByTestId("value")).toHaveTextContent("3");
  });

  it("surfaces errors without discarding the last good value", async () => {
    let n = 0;
    const fn = vi.fn(() => (n++ === 0 ? Promise.resolve(42) : Promise.reject(new Error("boom"))));
    render(<Probe fn={fn} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId("value")).toHaveTextContent("42");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(screen.getByTestId("error")).toHaveTextContent("boom");
    expect(screen.getByTestId("value")).toHaveTextContent("42");
  });
});

describe("usePolling under React.StrictMode", () => {
  it("still loads data when the effect is mounted twice (single-shot mode)", async () => {
    const fn = vi.fn(() => Promise.resolve(7));
    function Once() {
      const stable = useCallback(() => fn(), []);
      const { data } = usePolling(stable, 0);
      return <span data-testid="once">{data ?? "none"}</span>;
    }
    render(
      <StrictMode>
        <Once />
      </StrictMode>,
    );
    await waitFor(() => expect(screen.getByTestId("once")).toHaveTextContent("7"));
    expect(fn).toHaveBeenCalled();
  });
});
