import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function SecuritySettingsPage() {
  return (
    <main className="ops-page">
      <header><span>Settings</span><h1>Security</h1></header>
      <div className="ops-grid">
        <UsageMetricsCard title="Security Controls" metrics={[
          { label: "JWT", value: "Active", tone: "ok" },
          { label: "Refresh Rotation", value: "Active", tone: "ok" },
          { label: "2FA", value: "Ready", tone: "warn" }
        ]} />
        <section className="ops-card">
          <h2>Password Reset</h2>
          <label>Email<input className="input" defaultValue="student@jagdisu.local" /></label>
          <button className="button primary">Send Reset Token</button>
        </section>
      </div>
    </main>
  );
}
