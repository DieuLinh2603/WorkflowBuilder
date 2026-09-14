import { useState, useMemo, useEffect } from 'react';
import { Search, Plus, FileText, Edit2, Play, Trash2, Copy, AlertTriangle, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Pagination from '../components/Pagination';

export default function FormsPage() {
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const [forms, setForms] = useState([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const pageSize = 8;

  // Modals state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState(null);
  const [removing, setRemoving] = useState(false);

  // Try to load forms from backend if endpoint is ready, fallback to sample data
  useEffect(() => {
    let isMounted = true;
    const loadForms = async () => {
      setLoading(true);
      try {
        const token = localStorage.getItem('token');
        const res = await fetch('/api/forms', {
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        });
        if (res.ok) {
          const data = await res.json();
          if (isMounted && Array.isArray(data) && data.length > 0) {
            setForms(data);
          }
        }
      } catch (err) {
        // Use initial sample forms
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    loadForms();
    return () => { isMounted = false; };
  }, []);

  const categories = useMemo(() => {
    return [...new Set(forms.map(f => f.category).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi'));
  }, [forms]);

  const filtered = useMemo(() => {
    return forms.filter(item => {
      const matchQuery = `${item.title} ${item.description || ''} ${item.category || ''} ${item.createdByName || ''}`
        .toLocaleLowerCase('vi')
        .includes(query.toLocaleLowerCase('vi'));
      const matchCategory = !categoryFilter || item.category === categoryFilter;
      const matchStatus = !statusFilter || item.status === statusFilter;
      return matchQuery && matchCategory && matchStatus;
    });
  }, [forms, query, categoryFilter, statusFilter]);

  const displayed = filtered.slice(page * pageSize, (page + 1) * pageSize);
  useEffect(() => setPage(0), [query, categoryFilter, statusFilter]);

  const handleCreateForm = (newForm) => {
    const created = {
      ...newForm,
      id: `f-${Date.now()}`,
      fieldCount: 0,
      status: 'DRAFT',
      createdByName: user?.displayName || user?.name || 'Admin',
      updatedAt: new Date().toISOString()
    };
    setForms([created, ...forms]);
    setShowCreateModal(false);
  };

  const handleDuplicate = (form) => {
    const copy = {
      ...form,
      id: `f-${Date.now()}`,
      title: `${form.title} (Bản sao)`,
      status: 'DRAFT',
      updatedAt: new Date().toISOString()
    };
    setForms([copy, ...forms]);
  };

  const confirmRemove = () => {
    if (!pendingRemoval) return;
    setRemoving(true);
    setTimeout(() => {
      setForms(forms.filter(f => f.id !== pendingRemoval.id));
      setPendingRemoval(null);
      setRemoving(false);
    }, 300);
  };

  const statusClass = (status) => {
    switch (status) {
      case 'ACTIVE':
        return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
      case 'DRAFT':
        return 'bg-amber-50 text-amber-700 border border-amber-200';
      case 'ARCHIVED':
        return 'bg-gray-100 text-gray-600 border border-gray-200';
      default:
        return 'bg-blue-50 text-blue-700 border border-blue-200';
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-grayBorder relative h-full flex flex-col overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b border-grayBorder flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-800">Quản lý Forms</h1>
          <p className="text-sm text-gray-500">Thiết kế và quản lý các biểu mẫu độc lập của tổ chức</p>
        </div>
      </div>

      {/* Action & Filter Bar */}
      <div className="px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm biểu mẫu..."
              className="pl-9 pr-4 py-2 border border-grayBorder rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-primary w-64"
            />
          </div>

          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="border border-grayBorder rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px]"
          >
            <option value="">Phân loại: Tất cả</option>
            {categories.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="border border-grayBorder rounded-md px-3 py-2 text-sm text-gray-700 bg-white min-w-[150px]"
          >
            <option value="">Trạng thái: Tất cả</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="DRAFT">Bản nháp</option>
            <option value="ARCHIVED">Lưu trữ</option>
          </select>
        </div>

        {(hasRole('ADMIN') || hasRole('WORKFLOW_OWNER')) && (
          <button
            type="button"
            onClick={() => setShowCreateModal(true)}
            className="btn-primary flex items-center gap-2 py-2"
          >
            <Plus size={16} /> Tạo Form mới
          </button>
        )}
      </div>

      {/* Table */}
      <div className="px-6 pb-4 flex-1 overflow-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="text-xs font-semibold text-gray-500 uppercase border-b border-grayBorder bg-gray-50/50">
              <th className="py-3 px-4">TÊN BIỂU MẪU</th>
              <th className="py-3 px-4">PHÂN LOẠI</th>
              <th className="py-3 px-4">SỐ TRƯỜNG</th>
              <th className="py-3 px-4">TRẠNG THÁI</th>
              <th className="py-3 px-4">NGƯỜI TẠO</th>
              <th className="py-3 px-4">NGÀY CẬP NHẬT</th>
              <th className="py-3 px-4">HÀNH ĐỘNG</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && (
              <tr>
                <td colSpan="7" className="p-8 text-center text-gray-400">
                  Đang tải...
                </td>
              </tr>
            )}
            {!loading && displayed.length === 0 && (
              <tr>
                <td colSpan="7" className="p-8 text-center text-gray-400">
                  Không tìm thấy biểu mẫu nào phù hợp.
                </td>
              </tr>
            )}
            {displayed.map((form) => (
              <tr key={form.id} className="hover:bg-gray-50 text-sm">
                <td className="py-3 px-4 font-medium text-gray-800">
                  <div className="flex items-center gap-2">
                    <FileText size={16} className="text-gray-400 flex-shrink-0" />
                    <div>
                      <div className="font-semibold text-slate-800">{form.title}</div>
                      {form.description && (
                        <div className="text-xs text-gray-500 line-clamp-1 max-w-md">{form.description}</div>
                      )}
                    </div>
                  </div>
                </td>
                <td className="py-3 px-4 text-gray-600">{form.category || '-'}</td>
                <td className="py-3 px-4 text-gray-600">
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                    {form.fieldCount || 0} trường
                  </span>
                </td>
                <td className="py-3 px-4">
                  <span className={`px-2.5 py-1 rounded-md text-xs font-semibold ${statusClass(form.status)}`}>
                    {form.status === 'ACTIVE' ? 'Hoạt động' : form.status === 'DRAFT' ? 'Bản nháp' : 'Lưu trữ'}
                  </span>
                </td>
                <td className="py-3 px-4 text-gray-600">{form.createdByName || 'Admin'}</td>
                <td className="py-3 px-4 text-gray-600">
                  {form.updatedAt ? new Date(form.updatedAt).toLocaleDateString('vi-VN') : '-'}
                </td>
                <td className="py-3 px-4">
                  <div className="flex items-center gap-3 text-gray-400">
                    <button
                      type="button"
                      onClick={() => alert(`Mở trình chỉnh sửa Form: ${form.title}`)}
                      className="hover:text-primary tooltip"
                      title="Chỉnh sửa cấu trúc Form"
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDuplicate(form)}
                      className="hover:text-blue-500 tooltip"
                      title="Nhân bản Form"
                    >
                      <Copy size={16} />
                    </button>
                    <button
                      type="button"
                      onClick={() => alert(`Xem trước Form: ${form.title}`)}
                      className="hover:text-green-500 tooltip"
                      title="Xem trước biểu mẫu"
                    >
                      <Play size={16} />
                    </button>
                    <button
                      type="button"
                      onClick={() => setPendingRemoval(form)}
                      className="hover:text-red-500 tooltip"
                      title="Xóa Form"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <Pagination
        page={page}
        totalItems={filtered.length}
        pageSize={pageSize}
        onPageChange={setPage}
        itemLabel="biểu mẫu"
      />

      {/* Create Form Modal */}
      {showCreateModal && (
        <CreateFormModal
          isOpen={showCreateModal}
          onClose={() => setShowCreateModal(false)}
          onCreate={handleCreateForm}
        />
      )}

      {/* Removal Modal */}
      {pendingRemoval && (
        <FormRemovalModal
          form={pendingRemoval}
          busy={removing}
          onClose={() => !removing && setPendingRemoval(null)}
          onConfirm={confirmRemove}
        />
      )}
    </div>
  );
}

function CreateFormModal({ isOpen, onClose, onCreate }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('Hành chính - Nhân sự');
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Tên biểu mẫu là bắt buộc');
      return;
    }
    onCreate({ title: title.trim(), description: description.trim(), category });
  };

  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/50 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-[520px] overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <FileText size={20} />
            </span>
            <div>
              <h2 className="text-lg font-bold text-slate-800">Tạo Form mới</h2>
              <p className="text-xs text-slate-500">Biểu mẫu độc lập dùng để gắn vào các bước trong quy trình</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            aria-label="Đóng"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="p-6 space-y-4">
            {error && (
              <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700 border border-red-200">
                {error}
              </div>
            )}

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">
                Tên biểu mẫu <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => {
                  setTitle(e.target.value);
                  setError('');
                }}
                placeholder="VD: Phiếu đề xuất mua sắm, Đơn xin nghỉ phép..."
                className="w-full rounded-lg border border-slate-200 px-3.5 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                autoFocus
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">Phân loại</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full rounded-lg border border-slate-200 px-3.5 py-2.5 text-sm bg-white focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="Hành chính - Nhân sự">Hành chính - Nhân sự</option>
                <option value="Kế toán - Tài chính">Kế toán - Tài chính</option>
                <option value="Mua sắm & Tài sản">Mua sắm & Tài sản</option>
                <option value="IT & Kỹ thuật">IT & Kỹ thuật</option>
                <option value="Kinh doanh & Tiếp thị">Kinh doanh & Tiếp thị</option>
                <option value="Khác">Khác</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1">Mô tả mục đích</label>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Mô tả tóm tắt mục đích và nội dung thu thập của biểu mẫu..."
                className="w-full rounded-lg border border-slate-200 px-3.5 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="rounded-lg bg-primary px-5 py-2.5 text-sm font-bold text-white hover:bg-primary/90 shadow-sm"
            >
              Tạo Form
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function FormRemovalModal({ form, busy, onClose, onConfirm }) {
  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-900/50 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-[520px] overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start gap-4 border-b border-slate-100 px-6 py-5">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-500">
            <Trash2 size={21} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-bold text-slate-800">Xóa biểu mẫu?</h2>
            <p className="mt-1 truncate text-sm font-semibold text-slate-500">{form.title}</p>
          </div>
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"
            aria-label="Đóng"
          >
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-5">
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700">
            Biểu mẫu này và toàn bộ định nghĩa các trường liên quan sẽ bị xóa. Thao tác này không thể hoàn tác.
          </div>
        </div>
        <div className="flex justify-end gap-3 border-t border-slate-100 bg-slate-50/70 px-6 py-4">
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
          >
            Hủy
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={onConfirm}
            className="flex min-w-32 items-center justify-center gap-2 rounded-lg bg-red-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-red-600 disabled:opacity-50"
          >
            {busy ? 'Đang xử lý...' : 'Xóa biểu mẫu'}
          </button>
        </div>
      </div>
    </div>
  );
}
