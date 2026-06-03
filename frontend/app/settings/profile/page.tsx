import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function ProfileSettingsPage() {
  return (
    <main className="ops-page">
      <header><span>Settings</span><h1>Profile</h1></header>
      <div className="ops-grid">
        <section className="ops-card">
          <h2>Student Profile</h2>
          <label>Name<input className="input" defaultValue="JagdiSu Student" /></label>
          <label>Email<input className="input" defaultValue="student@jagdisu.local" /></label>
          <label>Mobile<input className="input" defaultValue="+91 99999 99999" /></label>
        </section>
        <UsageMetricsCard title="Profile Health" metrics={[
          { label: "Email", value: "Verified", tone: "ok" },
          { label: "Mobile", value: "Verified", tone: "ok" },
          { label: "Completion", value: "92%" }
        ]} />
      </div>
    </main>
  );
}
