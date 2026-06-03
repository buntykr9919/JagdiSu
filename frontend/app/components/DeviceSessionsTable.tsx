const sessions = [
  { device: "Chrome on Windows", ip: "127.0.0.1", seen: "Active now", status: "Current" },
  { device: "Android App", ip: "192.168.1.20", seen: "Yesterday", status: "Trusted" }
];

export default function DeviceSessionsTable() {
  return (
    <section className="ops-card">
      <h2>Device Sessions</h2>
      <table className="ops-table">
        <thead>
          <tr><th>Device</th><th>IP</th><th>Last Seen</th><th>Status</th></tr>
        </thead>
        <tbody>
          {sessions.map((session) => (
            <tr key={session.device}>
              <td>{session.device}</td>
              <td>{session.ip}</td>
              <td>{session.seen}</td>
              <td>{session.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
