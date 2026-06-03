import InvoiceTable from "../../components/InvoiceTable";
import SubscriptionCard from "../../components/SubscriptionCard";

export default function SubscriptionSettingsPage() {
  return (
    <main className="ops-page">
      <header><span>Settings</span><h1>Subscription</h1></header>
      <div className="ops-grid">
        <SubscriptionCard />
        <InvoiceTable />
      </div>
    </main>
  );
}
