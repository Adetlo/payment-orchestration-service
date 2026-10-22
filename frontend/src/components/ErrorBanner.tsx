import { ApiError } from "../api/client";

export function ErrorBanner({ error, testId }: { error: Error | null; testId?: string }) {
  if (!error) return null;
  if (error instanceof ApiError) {
    return (
      <div className="banner tone-red" role="alert" data-testid={testId ?? "error-banner"}>
        <strong>
          {error.status > 0 ? `${String(error.status)} ` : ""}
          {error.title}
        </strong>
        {error.detail && <div>{error.detail}</div>}
        {error.type && error.type !== "about:blank" && (
          <div className="mono small">{error.type}</div>
        )}
      </div>
    );
  }
  return (
    <div className="banner tone-red" role="alert" data-testid={testId ?? "error-banner"}>
      <strong>Request failed</strong>
      <div>{error.message}</div>
    </div>
  );
}
