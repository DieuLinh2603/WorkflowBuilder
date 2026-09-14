import { Search, Plus, Edit2, Eye, Lock } from 'lucide-react';
import { useState } from 'react';
import UserFormPanel from '../components/UserFormPanel';
import UserDetailPanel from '../components/UserDetailPanel';
import useUserManagement from '../hooks/useUserManagement';
import Pagination from '../components/Pagination';

export default function UsersPage() {
  const {
    users,
    loading,
    totalElements,
    totalPages,
    page,
    setPage,
    keyword,
    setKeyword,
    jobTitle,
    setJobTitle,
    role,
    setRole,
    fetchUsers,
    createUser,
    updateUser,
    deactivateUser,
    fetchUserById,
    fetchDropdownUsers
  } = useUserManagement();

  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [panelMode, setPanelMode] = useState('create');
  const [selectedUser, setSelectedUser] = useState(null);
  const [viewedUser, setViewedUser] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const handleOpenAdd = () => {
    setPanelMode('create');
    setSelectedUser(null);
    setIsPanelOpen(true);
  };

  const handleOpenEdit = (user) => {
    setPanelMode('edit');
    setSelectedUser(user);
    setIsPanelOpen(true);
  };

  const handleOpenDetail = async (user) => {
    setViewedUser({ ...user });
    setDetailError('');
    setDetailLoading(true);
    try {
      const detail = await fetchUserById(user.id);
      setViewedUser({ ...user, ...detail });
    } catch (error) {
      setDetailError(error.message || 'Không thể tải thông tin User.');
    } finally {
      setDetailLoading(false);
    }
  };

  const editFromDetail = () => {
    const user = viewedUser;
    setViewedUser(null);
    handleOpenEdit(user);
  };

  const handleSave = async (formData) => {
    if (panelMode === 'create') await createUser(formData);
    else await updateUser(selectedUser.id, formData);
    setIsPanelOpen(false);
  };

  const handleDeactivate = async (id) => {
    if (window.confirm("Bạn có chắc chắn muốn vô hiệu hóa tài khoản này?")) {
      try {
        await deactivateUser(id);
      } catch (err) {
        alert(err.message);
      }
    }
  };

  const roleColors = {
    'ADMIN': 'bg-red-100 text-red-700',
    'WORKFLOW_OWNER': 'bg-orange-100 text-orange-700',
    'EDITOR': 'bg-blue-100 text-blue-700',
    'VIEWER': 'bg-gray-100 text-gray-700'
  };

  const roleLabels = {
    'ADMIN': 'Admin',
    'WORKFLOW_OWNER': 'Owner',
    'EDITOR': 'Editor',
    'VIEWER': 'Viewer'
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 relative h-full flex flex-col overflow-hidden">
      {/* Header Area */}
      <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-800">Quản lý Users</h1>
          <p className="text-sm text-gray-500">Quản lý tài khoản và phân quyền trong hệ thống</p>
        </div>
        
      </div>

      {/* Toolbar Area */}
      <div className="px-6 py-4 flex items-center justify-between bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input 
                type="text" 
                placeholder="Tìm user..." 
                className="pl-9 pr-4 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-orange-500 w-64 bg-white" 
                value={keyword}
                onChange={(e) => { setPage(0); setKeyword(e.target.value); }}
                onBlur={fetchUsers}
                onKeyDown={(e) => e.key === 'Enter' && fetchUsers()}
            />
          </div>
          
          <select 
              className="border border-gray-300 rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px] outline-none focus:border-orange-500"
              value={jobTitle}
              onChange={(e) => { setPage(0); setJobTitle(e.target.value); }}
          >
            <option value="">Chức danh: Tất cả</option>
            <option value="Manager">Manager</option>
            <option value="Director">Director</option>
            <option value="Finance Lead">Finance Lead</option>
            <option value="Nhân viên">Nhân viên</option>
          </select>

          <select 
              className="border border-gray-300 rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px] outline-none focus:border-orange-500"
              value={role}
              onChange={(e) => { setPage(0); setRole(e.target.value); }}
          >
            <option value="">Role hệ thống: Tất cả</option>
            <option value="ADMIN">Admin</option>
            <option value="WORKFLOW_OWNER">Workflow Owner</option>
            <option value="EDITOR">Editor</option>
            <option value="VIEWER">Viewer</option>
          </select>
        </div>

        <button 
          className="bg-orange-500 hover:bg-orange-600 text-white font-semibold flex items-center gap-2 py-2 px-4 rounded-lg text-sm transition-colors shadow-sm"
          onClick={handleOpenAdd}
        >
          <Plus size={16} /> Thêm User
        </button>
      </div>

      {/* Table Area */}
      <div className="px-6 pb-4 flex-1 overflow-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="text-xs font-semibold text-gray-500 uppercase border-b border-gray-200 bg-white sticky top-0 z-10">
              <th className="py-3 px-4">TÊN HIỂN THỊ</th>
              <th className="py-3 px-4">EMAIL</th>
              <th className="py-3 px-4">CHỨC DANH</th>
              <th className="py-3 px-4">QUẢN LÝ TRỰC TIẾP</th>
              <th className="py-3 px-4">ROLE HỆ THỐNG</th>
              <th className="py-3 px-4">NGÀY TẠO</th>
              <th className="py-3 px-4">HÀNH ĐỘNG</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
                <tr><td colSpan="7" className="text-center py-8 text-gray-500">Đang tải dữ liệu...</td></tr>
            ) : users.length === 0 ? (
                <tr><td colSpan="7" className="text-center py-8 text-gray-500">Không tìm thấy dữ liệu</td></tr>
            ) : users.map((user) => (
              <tr key={user.id} className="hover:bg-gray-50 text-sm transition-colors">
                <td className="py-3 px-4 flex items-center gap-3 font-medium text-gray-800">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white ${user.avatarColor || 'bg-gray-500'}`}>
                    {user.avatarInitials}
                  </div>
                  {user.displayName}
                </td>
                <td className="py-3 px-4 text-gray-600">{user.email}</td>
                <td className="py-3 px-4 text-gray-600">{user.jobTitle}</td>
                <td className="py-3 px-4 text-gray-600">{user.managerName || '—'}</td>
                <td className="py-3 px-4 flex flex-wrap gap-1">
                  {user.systemRoles && user.systemRoles.length > 0 ? user.systemRoles.map(r => (
                      <span key={r} className={`px-2.5 py-1 rounded-md text-xs font-semibold ${roleColors[r] || 'bg-gray-100 text-gray-700'}`}>
                        {roleLabels[r] || r}
                      </span>
                  )) : (
                      <span className="px-2.5 py-1 rounded-md text-xs font-semibold bg-gray-100 text-gray-600">
                        Chưa gán role
                      </span>
                  )}
                </td>
                <td className="py-3 px-4 text-gray-600">
                   {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : '—'}
                </td>
                <td className="py-3 px-4">
                  <div className="flex items-center gap-3 text-gray-400">
                    <button className="hover:text-blue-500 transition-colors" onClick={() => handleOpenDetail(user)} title="Xem thông tin"><Eye size={16} /></button>
                    <button className="hover:text-orange-500 transition-colors" onClick={() => handleOpenEdit(user)} title="Sửa"><Edit2 size={16} /></button>
                    <button className="hover:text-red-500 transition-colors" onClick={() => handleDeactivate(user.id)} title="Vô hiệu hóa"><Lock size={16} /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={page} totalItems={totalElements} pageSize={5} onPageChange={setPage} itemLabel="users" />

      <UserFormPanel 
          isOpen={isPanelOpen} 
          onClose={() => setIsPanelOpen(false)} 
          mode={panelMode} 
          initialData={selectedUser}
          onSave={handleSave}
          fetchDropdownUsers={fetchDropdownUsers}
      />
      <UserDetailPanel
          user={viewedUser}
          loading={detailLoading}
          error={detailError}
          onClose={() => setViewedUser(null)}
          onEdit={editFromDetail}
      />
    </div>
  );
}
