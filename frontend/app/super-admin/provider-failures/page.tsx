import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function ProviderFailuresAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>Provider Failures</h1></header>
      <UsageMetricsCard title="Failure Windows" metrics={[
        { label: "OpenRouter", value: "0", tone: "ok" },
        { label: "Gemini", value: "1", tone: "warn" },
        { label: "Claude", value: "0", tone: "ok" }
      ]} />
    </main>
  );
}
