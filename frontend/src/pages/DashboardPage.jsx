import { useAuth } from "../context/AuthContext";
import AdminDashboard from "../components/dashboards/AdminDashboard";
import OwnerDashboard from "../components/dashboards/OwnerDashboard";
import EditorDashboard from "../components/dashboards/EditorDashboard";
import ViewerDashboard from "../components/dashboards/ViewerDashboard";

export default function DashboardPage() {
  const { user } = useAuth();

  if (user.systemRoles?.includes("ADMIN")) return <AdminDashboard />;
  if (user.systemRoles?.includes("WORKFLOW_OWNER")) return <OwnerDashboard />;
  if (user.systemRoles?.includes("EDITOR")) return <EditorDashboard />;
  return <ViewerDashboard />;
}
