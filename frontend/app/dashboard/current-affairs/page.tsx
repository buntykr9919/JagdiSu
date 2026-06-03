const items = [
  "Daily PIB and scheme analysis",
  "Monthly exam magazine pipeline",
  "Editorial summaries with exam relevance"
];

export default function CurrentAffairsDashboardPage() {
  return (
    <main className="ops-page">
      <header><span>Dashboard</span><h1>Current Affairs</h1></header>
      <section className="ops-card">
        <h2>Exam Ready Feed</h2>
        <div className="ops-list">
          {items.map((item) => <div key={item}><span>{item}</span><strong>Ready</strong></div>)}
        </div>
      </section>
    </main>
  );
}
