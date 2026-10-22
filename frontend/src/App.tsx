import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { CreatePaymentPage } from "./pages/CreatePaymentPage";
import { DashboardPage } from "./pages/DashboardPage";
import { HealthPage } from "./pages/HealthPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PaymentDetailPage } from "./pages/PaymentDetailPage";
import { PaymentsPage } from "./pages/PaymentsPage";
import { ReconciliationPage } from "./pages/ReconciliationPage";

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="payments/new" element={<CreatePaymentPage />} />
        <Route path="payments" element={<PaymentsPage />} />
        <Route path="payments/:id" element={<PaymentDetailPage />} />
        <Route path="reconciliation" element={<ReconciliationPage />} />
        <Route path="health" element={<HealthPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
