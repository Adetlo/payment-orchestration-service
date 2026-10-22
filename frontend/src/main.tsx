import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { ROUTER_FUTURE } from "./router";
import "./styles/app.css";

const container = document.getElementById("root");
if (!container) {
  throw new Error("Root element #root not found");
}

ReactDOM.createRoot(container).render(
  <React.StrictMode>
    <BrowserRouter future={ROUTER_FUTURE}>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
