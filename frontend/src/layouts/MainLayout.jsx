import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { Share2, LayoutDashboard, GitBranch, Layers, Users, Settings, LogOut, ChevronDown, FilePlus2, ClipboardCheck, KeyRound, DatabaseZap, Plug, FileText } from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import NotificationCenter from '../components/NotificationCenter';
import ChangePasswordModal from '../components/ChangePasswordModal';

const ROLE_LABELS = {
  ADMIN: 'Admin',
  WORKFLOW_OWNER: 'Workflow Owner',
  EDITOR: 'Editor',
  VIEWER: 'Viewer',
};

const ROLE_BADGE_COLORS = {
  ADMIN: 'bg-red-100 text-red-700',
  WORKFLOW_OWNER: 'bg-orange-100 text-orange-700',
  EDITOR: 'bg-blue-100 text-blue-700',
  VIEWER: 'bg-gray-200 text-gray-700',
};

export default function MainLayout() {
  const { user, logout, hasRole } = useAuth();
  const primaryRole = ['ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER'].find(hasRole) || 'VIEWER';
  const navigate = useNavigate();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);

  // Sidebar items based on role
  const getNavItems = () => {
    const base = [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
      { path: '/catalog', label: 'Tạo yêu cầu', icon: FilePlus2 },
      { path: '/tasks', label: 'Nhiệm vụ', icon: ClipboardCheck },
    ];

    if (hasRole('ADMIN')) {
      return [
        ...base,
        { path: '/workflows', label: 'Workflows', icon: GitBranch },
        { path: '/forms', label: 'Forms', icon: FileText },
        { path: '/pipelines', label: 'Data Pipelines', icon: DatabaseZap },
        { path: '/connectors', label: 'Connectors', icon: Plug },
        { path: '/tickets', label: 'Tickets', icon: Layers },
        { path: '/users', label: 'Users', icon: Users },
      ];
    }

    if (hasRole('WORKFLOW_OWNER')) {
      return [
        ...base,
        { path: '/workflows', label: 'Workflows', icon: GitBranch },
        { path: '/pipelines', label: 'Data Pipelines', icon: DatabaseZap },
        { path: '/tickets', label: 'Tickets', icon: Layers },
      ];
    }

    if (hasRole('EDITOR')) {
      return [
        ...base,
        { path: '/workflows', label: 'Workflows', icon: GitBranch },
        { path: '/tickets', label: 'Tickets', icon: Layers },
      ];
    }

    // VIEWER
    return [
      ...base,
      { path: '/workflows', label: 'Workflows được giao', icon: GitBranch },
      { path: '/tickets', label: 'Tickets', icon: Layers },
    ];
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-grayLight">
      {/* Sidebar */}
      <div className="w-60 bg-navyDark text-gray-300 flex flex-col flex-shrink-0">
        <div className="px-5 py-5 flex items-center gap-3 text-white border-b border-white/10">
          <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
            <Share2 size={16} />
          </div>
          <span className="font-bold text-base">WorkflowBuilder</span>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {getNavItems().map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 ${
                  isActive
                    ? 'bg-primary text-white shadow-md shadow-primary/30'
                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`
              }
            >
              <item.icon size={18} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-white/10">
          {hasRole('ADMIN') && (
            <NavLink to="/settings" className="flex items-center gap-3 px-3 py-2.5 text-sm text-gray-400 hover:text-white transition-colors rounded-lg hover:bg-white/5">
              <Settings size={18} />
              <span>Cài đặt hệ thống</span>
            </NavLink>
          )}
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 px-3 py-2.5 text-sm text-gray-400 hover:text-red-400 transition-colors rounded-lg hover:bg-white/5 w-full text-left mt-1"
          >
            <LogOut size={18} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Header */}
        <header className="h-14 bg-white border-b border-grayBorder flex items-center justify-between px-6 flex-shrink-0">
          <div />
          <div className="flex items-center gap-4">
            <div className={`px-3 py-1 rounded-full text-xs font-semibold ${ROLE_BADGE_COLORS[primaryRole]}`}>
              Vai trò: {(user.systemRoles || ['VIEWER']).map(r => ROLE_LABELS[r] || r).join(', ')}
            </div>

            <NotificationCenter />
            <div className="group relative flex items-center gap-2 cursor-pointer pb-2 pt-2">
              <div className="w-8 h-8 rounded-full bg-primary/10 text-primary flex items-center justify-center text-sm font-bold">
                {user.displayName.charAt(0)}
              </div>
              <span className="text-sm font-medium text-gray-700">{user.displayName}</span>
              <ChevronDown size={14} className="text-gray-400" />
              
              <div className="absolute top-full right-0 mt-0 w-48 bg-white rounded-lg shadow-lg border border-grayBorder py-1 hidden group-hover:block z-50">
                <button
                  onClick={() => setPasswordModalOpen(true)}
                  className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-orange-50 hover:text-orange-600 flex items-center gap-2 transition-colors"
                >
                  <KeyRound size={16} />
                  <span>Đổi mật khẩu</span>
                </button>
                <button
                  onClick={handleLogout}
                  className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-red-50 hover:text-red-600 flex items-center gap-2 transition-colors"
                >
                  <LogOut size={16} />
                  <span>Đăng xuất</span>
                </button>
              </div>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-auto p-6 bg-grayLight">
          <Outlet />
        </main>
      </div>
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} onChanged={handleLogout}/>
    </div>
  );
}
