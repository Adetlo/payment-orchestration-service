import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <div className="stack">
      <h1>Page not found</h1>
      <p className="muted">
        <Link to="/">Back to the dashboard</Link>
      </p>
    </div>
  );
}
