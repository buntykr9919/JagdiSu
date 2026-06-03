import DeviceSessionsTable from "../../components/DeviceSessionsTable";

export default function SessionsSettingsPage() {
  return (
    <main className="ops-page">
      <header><span>Settings</span><h1>Sessions</h1></header>
      <DeviceSessionsTable />
    </main>
  );
}
