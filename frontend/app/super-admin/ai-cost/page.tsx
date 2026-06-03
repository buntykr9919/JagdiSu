import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function AiCostAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>AI Cost</h1></header>
      <UsageMetricsCard title="Cost Controls" metrics={[
        { label: "30d Cost", value: "$18.42" },
        { label: "Daily Cap", value: "$25" },
        { label: "Budget State", value: "Healthy", tone: "ok" }
      ]} />
    </main>
  );
}
