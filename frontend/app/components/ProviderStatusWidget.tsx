const providers = [
  { name: "OpenRouter", status: "Healthy", latency: "820 ms" },
  { name: "OpenAI", status: "Standby", latency: "910 ms" },
  { name: "Gemini", status: "Standby", latency: "760 ms" },
  { name: "DeepSeek", status: "Cooling", latency: "n/a" }
];

export default function ProviderStatusWidget() {
  return (
    <section className="ops-card">
      <h2>Provider Status</h2>
      <div className="ops-list">
        {providers.map((provider) => (
          <div key={provider.name}>
            <span>{provider.name}</span>
            <strong>{provider.status} - {provider.latency}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}
