import DailyGoalWidget from "../../components/DailyGoalWidget";
import StudyStreakWidget from "../../components/StudyStreakWidget";
import WeakTopicsWidget from "../../components/WeakTopicsWidget";
import UsageMetricsCard from "../../components/UsageMetricsCard";

export default function AnalyticsDashboardPage() {
  return (
    <main className="ops-page">
      <header><span>Dashboard</span><h1>Analytics</h1></header>
      <div className="ops-grid">
        <StudyStreakWidget />
        <DailyGoalWidget />
        <UsageMetricsCard title="Learning" metrics={[
          { label: "Weekly Study", value: "7h 20m" },
          { label: "Accuracy", value: "78%" },
          { label: "Tests", value: "12" }
        ]} />
        <WeakTopicsWidget />
      </div>
    </main>
  );
}
