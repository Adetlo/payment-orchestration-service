import { useEffect, useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";

const NAV = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/payments/new", label: "Create payment", end: false },
  { to: "/payments", label: "Payments", end: true },
  { to: "/reconciliation", label: "Reconciliation", end: false },
  { to: "/health", label: "System health", end: false },
];

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);
  const s = now.toISOString();
  return (
    <span data-testid="clock" aria-live="off">
      {s.slice(0, 10)} {s.slice(11, 19)} UTC
    </span>
  );
}

export function Layout() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand" aria-label="Payment Orchestration Lab home">
          <span className="brand-mark" aria-hidden="true">
            PO
          </span>
          Payment Orchestration Lab
        </Link>
        <div className="header-meta">
          <span>demo · synthetic data</span>
          <Clock />
        </div>
      </header>
      <nav className="app-sidebar" aria-label="Primary">
        <ul className="nav-list">
          {NAV.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className="app-main" id="main">
        <Outlet />
      </main>
      <footer className="app-footer">
        Synthetic data only. Independent educational portfolio project.
      </footer>
    </div>
  );
}
