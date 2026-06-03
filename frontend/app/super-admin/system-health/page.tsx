import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function SystemHealthAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>System Health</h1></header>
      <UsageMetricsCard title="Runtime" metrics={[
        { label: "API", value: "UP", tone: "ok" },
        { label: "Redis", value: "UP", tone: "ok" },
        { label: "Search", value: "Fallback", tone: "warn" }
      ]} />
    </main>
  );
}
