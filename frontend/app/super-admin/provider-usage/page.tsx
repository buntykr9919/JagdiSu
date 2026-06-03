import ProviderStatusWidget from "../../components/ProviderStatusWidget";

export default function ProviderUsageAdminPage() {
  return (
    <main className="ops-page">
      <header><span>Super Admin</span><h1>Provider Usage</h1></header>
      <ProviderStatusWidget />
    </main>
  );
}
