import ProviderStatusWidget from "../../components/ProviderStatusWidget";
import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function AiUsageDashboardPage() {
  return (
    <main className="ops-page">
      <header><span>Dashboard</span><h1>AI Usage</h1></header>
      <div className="ops-grid">
        <UsageMetricsCard title="Usage Budget" metrics={[
          { label: "Tokens Today", value: "42k" },
          { label: "Cost Today", value: "$0.18", tone: "ok" },
          { label: "Failovers", value: "2", tone: "warn" }
        ]} />
        <ProviderStatusWidget />
      </div>
    </main>
  );
}
