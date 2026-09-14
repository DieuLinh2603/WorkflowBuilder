import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import LoginPage from './pages/LoginPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import DashboardPage from './pages/DashboardPage';
import UsersPage from './pages/UsersPage';
import InstancesPage from './pages/InstancesPage';
import WorkflowsPage from './pages/WorkflowsPage';
import WorkflowDesignerPage from './pages/WorkflowDesignerPage';
import WorkflowVersionHistoryPage from './pages/WorkflowVersionHistoryPage';
import CatalogPage from './pages/CatalogPage';
import RequestFormPage from './pages/RequestFormPage';
import MyWorkPage from './pages/MyWorkPage';
import TaskDetailPage from './pages/TaskDetailPage';
import SettingsPage from './pages/SettingsPage';
import ConnectorsPage from './pages/ConnectorsPage';
import PipelinesPage from './pages/PipelinesPage';
import PipelineDesignerPage from './pages/PipelineDesignerPage';
import FormsPage from './pages/FormsPage';
import ProtectedRoute from './components/ProtectedRoute';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/workflows/:id/design"
        element={
          <ProtectedRoute allowedRoles={['ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER']}>
            <WorkflowDesignerPage />
          </ProtectedRoute>
        }
      />
      <Route path="/tasks/:taskId" element={<ProtectedRoute><TaskDetailPage /></ProtectedRoute>} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route
          path="users"
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <UsersPage />
            </ProtectedRoute>
          }
        />
        <Route path="instances" element={<InstancesPage />} />
        <Route path="instances/:instanceId" element={<InstancesPage />} />
        <Route path="tasks" element={<MyWorkPage />} />
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="catalog/:workflowId" element={<RequestFormPage />} />
        <Route path="settings" element={<ProtectedRoute allowedRoles={['ADMIN']}><SettingsPage /></ProtectedRoute>} />
        <Route path="forms" element={<ProtectedRoute allowedRoles={['ADMIN']}><FormsPage /></ProtectedRoute>} />
        <Route path="connectors" element={<ProtectedRoute allowedRoles={['ADMIN']}><ConnectorsPage /></ProtectedRoute>} />
        <Route path="pipelines" element={<ProtectedRoute allowedRoles={['ADMIN','WORKFLOW_OWNER']}><PipelinesPage /></ProtectedRoute>} />
        <Route path="pipelines/:id" element={<ProtectedRoute allowedRoles={['ADMIN','WORKFLOW_OWNER']}><PipelineDesignerPage /></ProtectedRoute>} />
        <Route path="tickets" element={<InstancesPage />} />
        <Route path="tickets/:instanceId" element={<InstancesPage />} />
        <Route
          path="workflows"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER']}>
              <WorkflowsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="workflows/:id/versions"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER']}>
              <WorkflowVersionHistoryPage />
            </ProtectedRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
