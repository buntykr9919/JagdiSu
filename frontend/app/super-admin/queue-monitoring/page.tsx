import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function QueueMonitoringAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>Queue Monitoring</h1></header>
      <UsageMetricsCard title="Queues" metrics={[
        { label: "Async Jobs", value: "0" },
        { label: "Search Queue", value: "0" },
        { label: "Redis Stream", value: "Active", tone: "ok" }
      ]} />
    </main>
  );
}
