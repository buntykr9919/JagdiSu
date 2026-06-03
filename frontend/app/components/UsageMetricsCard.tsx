type Metric = { label: string; value: string; tone?: "ok" | "warn" | "bad" };

export default function UsageMetricsCard({ title, metrics }: { title: string; metrics: Metric[] }) {
  return (
    <section className="ops-card">
      <h2>{title}</h2>
      <div className="ops-metrics">
        {metrics.map((metric) => (
          <div key={metric.label}>
            <span>{metric.label}</span>
            <strong className={metric.tone ? `tone-${metric.tone}` : ""}>{metric.value}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}
