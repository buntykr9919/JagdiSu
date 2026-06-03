const invoices = [
  { id: "JDSU-LOCAL-001", plan: "Monthly", amount: "Rs 199", status: "Issued" },
  { id: "JDSU-LOCAL-002", plan: "Yearly", amount: "Rs 1499", status: "Paid" }
];

export default function InvoiceTable() {
  return (
    <section className="ops-card">
      <h2>Invoices</h2>
      <table className="ops-table">
        <thead>
          <tr><th>Invoice</th><th>Plan</th><th>Amount</th><th>Status</th></tr>
        </thead>
        <tbody>
          {invoices.map((invoice) => (
            <tr key={invoice.id}>
              <td>{invoice.id}</td>
              <td>{invoice.plan}</td>
              <td>{invoice.amount}</td>
              <td>{invoice.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
