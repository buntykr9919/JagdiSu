const plan = [
  { day: "Today", topic: "Modern History: Congress Sessions" },
  { day: "Tomorrow", topic: "Polity: Emergency Provisions" },
  { day: "Friday", topic: "Maths: Profit and Loss" }
];

export default function RevisionPlanPage() {
  return (
    <main className="ops-page">
      <header><span>Dashboard</span><h1>Revision Plan</h1></header>
      <section className="ops-card">
        <h2>Upcoming Revisions</h2>
        <div className="ops-list">
          {plan.map((item) => <div key={item.topic}><strong>{item.day}</strong><span>{item.topic}</span></div>)}
        </div>
      </section>
    </main>
  );
}
