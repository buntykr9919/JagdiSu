const topics = ["Polity: Fundamental Rights", "Economy: Inflation", "Maths: Time and Work"];

export default function WeakTopicsWidget() {
  return (
    <section className="ops-card">
      <h2>Weak Topics</h2>
      <div className="ops-list">
        {topics.map((topic, index) => (
          <div key={topic}><strong>{index + 1}</strong><span>{topic}</span></div>
        ))}
      </div>
    </section>
  );
}
