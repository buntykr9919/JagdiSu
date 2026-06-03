import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function PaymentMonitoringAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>Payment Monitoring</h1></header>
      <UsageMetricsCard title="Payments" metrics={[
        { label: "Gateway", value: "Razorpay" },
        { label: "Captured", value: "Healthy", tone: "ok" },
        { label: "Webhook", value: "Verified", tone: "ok" }
      ]} />
    </main>
  );
}
