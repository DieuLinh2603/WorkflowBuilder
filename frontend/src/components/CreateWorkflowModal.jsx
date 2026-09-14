import { useState, useEffect } from 'react';
import { X, Search } from 'lucide-react';
import { apiError, apiFetch } from '../api';

export default function CreateWorkflowModal({ isOpen, onClose, onCreate, currentUser, fetchDropdownUsers }) {
  const isAdmin = currentUser?.systemRoles?.includes('ADMIN');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [workflowType, setWorkflowType] = useState('');
  const [customWorkflowType, setCustomWorkflowType] = useState('');
  const [module, setModule] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [owners, setOwners] = useState([]);
  const [showOwnerDropdown, setShowOwnerDropdown] = useState(false);
  const [ownerSearch, setOwnerSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [workflowTypes, setWorkflowTypes] = useState([]);
  const [modules, setModules] = useState([]);

  useEffect(() => {
    if (isOpen) {
      setName('');
      setDescription('');
      setWorkflowType('');
      setCustomWorkflowType('');
      setModule('');
      setOwnerId(currentUser?.id || '');
      setError('');
      Promise.all([apiFetch('/api/metadata/workflow-types'), apiFetch('/api/metadata/modules')])
        .then(async ([typeResponse, moduleResponse]) => {
          if (!typeResponse.ok || !moduleResponse.ok) throw new Error(await apiError(!typeResponse.ok ? typeResponse : moduleResponse, 'Không thể tải danh mục nghiệp vụ'));
          setWorkflowTypes(await typeResponse.json());
          setModules(await moduleResponse.json());
        }).catch(requestError => setError(requestError.message));

      // Fetch users for owner dropdown
      if (fetchDropdownUsers && isAdmin) {
        fetchDropdownUsers()
          .then(data => setOwners(data))
          .catch(() => {});
      }
    }
  }, [isOpen, currentUser, fetchDropdownUsers]);

  if (!isOpen) return null;

  const selectedOwner = owners.find(o => o.id === ownerId) || (currentUser ? {
    id: currentUser.id,
    displayName: currentUser.displayName || currentUser.name,
    avatarInitials: (currentUser.displayName || currentUser.name || 'NA').split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase(),
    avatarColor: 'bg-orange-500'
  } : null);

  const handleSubmit = async () => {
    if (!name.trim()) {
      setError('Tên Workflow là bắt buộc');
      return;
    }
    if (!currentUser?.id) {
      setError('Không xác định được tài khoản hiện tại. Vui lòng đăng nhập lại.');
      return;
    }
    if (!workflowType || !module) {
      setError('Vui lòng chọn đầy đủ Loại Workflow và Module áp dụng.');
      return;
    }
    if (workflowType === 'CUSTOM' && !customWorkflowType.trim()) {
      setError('Vui lòng nhập loại Workflow khác.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await onCreate({
        name: name.trim(),
        description: description.trim() || null,
        workflowType: workflowType || null,
        customWorkflowType: workflowType === 'CUSTOM' ? customWorkflowType.trim() : null,
        module: module || null,
        ...(isAdmin ? { ownerId: ownerId || null } : {})
      });
    } catch (err) {
      setError(err?.message || 'Không thể tạo workflow. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      {/* Overlay */}
      <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={onClose}>
        {/* Modal */}
        <div
          className="bg-white rounded-xl shadow-2xl w-full max-w-[560px] relative"
          onClick={e => e.stopPropagation()}
        >
          {/* Header */}
          <div className="px-8 pt-8 pb-2 flex items-start justify-between">
            <h2 className="text-xl font-bold text-gray-800">Tạo Workflow mới</h2>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
              <X size={20} />
            </button>
          </div>

          {/* Body */}
          <div className="px-8 py-6 space-y-5">
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-2">
                {error}
              </div>
            )}

            {/* Tên Workflow */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Tên Workflow <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                placeholder="Phê duyệt đề xuất mua sắm"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-colors"
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </div>

            {/* Mô tả */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">Mô tả Workflow</label>
              <textarea
                placeholder="Quy trình áp dụng cho việc mua sắm trang thiết bị văn phòng có giá trị dưới 50 triệu đồng."
                rows={3}
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none resize-none transition-colors"
                value={description}
                onChange={e => setDescription(e.target.value)}
              />
            </div>

            {/* Loại + Module */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Loại Workflow <span className="text-red-500">*</span></label>
                <select
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-colors"
                  value={workflowType}
                  onChange={e => { setWorkflowType(e.target.value); if (e.target.value !== 'CUSTOM') setCustomWorkflowType(''); }}
                  required
                >
                  <option value="" disabled>Chọn loại</option>
                  {workflowTypes.map(t => <option key={t.code} value={t.code}>{t.code === 'CUSTOM' ? 'Khác' : t.name}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Module áp dụng <span className="text-red-500">*</span></label>
                <select
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-colors"
                  value={module}
                  onChange={e => setModule(e.target.value)}
                  required
                >
                  <option value="">Chọn module...</option>
                  {modules.map(m => <option key={m.code} value={m.code}>{m.name}</option>)}
                </select>
              </div>
            </div>
            <div className="rounded-lg border border-blue-100 bg-blue-50/60 px-4 py-3 text-xs leading-5 text-blue-700"><b>Loại workflow</b> mô tả mục đích và đưa ra cấu trúc gợi ý. <b>Module</b> xác định phạm vi nghiệp vụ, quyền truy cập và nơi kiểm tra trùng tên workflow.</div>
            {workflowType === 'CUSTOM' && <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">Loại Workflow khác <span className="text-red-500">*</span></label>
              <input type="text" maxLength={255} required autoFocus placeholder="Nhập loại workflow"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-colors"
                value={customWorkflowType} onChange={e => setCustomWorkflowType(e.target.value)} />
            </div>}
            {workflowType && (() => { const selected = workflowTypes.find(item => item.code === workflowType); return selected ? <div className="rounded-lg border border-orange-100 bg-orange-50/60 px-4 py-3 text-xs text-gray-600"><p className="font-semibold text-orange-700">Gợi ý cho {selected.name}</p>{selected.description && <p className="mt-1">{selected.description}</p>}{selected.checklist?.length > 0 && <p className="mt-1">Checklist: {selected.checklist.join(' • ')}</p>}</div> : null; })()}

            {/* Owner + Version */}
            <div className="grid grid-cols-2 gap-4">
              <div className="relative">
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Người sở hữu Workflow</label>
                <div
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white flex items-center gap-2 cursor-pointer hover:border-gray-400 transition-colors"
                  onClick={() => isAdmin && setShowOwnerDropdown(!showOwnerDropdown)}
                >
                  {selectedOwner ? (
                    <>
                      <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0 ${selectedOwner.avatarColor || 'bg-orange-500'}`}>
                        {selectedOwner.avatarInitials}
                      </div>
                      <span className="truncate">{selectedOwner.displayName}</span>
                    </>
                  ) : (
                    <span className="text-gray-400">Chọn người sở hữu...</span>
                  )}
                </div>

                {isAdmin && showOwnerDropdown && (
                  <div className="absolute z-20 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-auto">
                    <div className="p-2 border-b border-gray-100">
                      <input
                        type="text"
                        placeholder="Tìm kiếm..."
                        className="w-full px-2 py-1 border border-gray-200 rounded text-sm outline-none"
                        value={ownerSearch}
                        onChange={e => setOwnerSearch(e.target.value)}
                      />
                    </div>
                    {owners.filter(o => o.displayName.toLowerCase().includes(ownerSearch.toLowerCase())).map(o => (
                      <div
                        key={o.id}
                        className="px-3 py-2 text-sm text-gray-700 hover:bg-orange-50 cursor-pointer flex items-center gap-2"
                        onClick={() => { setOwnerId(o.id); setShowOwnerDropdown(false); }}
                      >
                        <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-white ${o.avatarColor || 'bg-gray-400'}`}>
                          {o.avatarInitials}
                        </div>
                        {o.displayName}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Phiên bản</label>
                <input
                  type="text"
                  value="v1.0 (Auto)"
                  disabled
                  className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm bg-gray-50 text-gray-500 cursor-not-allowed"
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="px-8 pb-8 flex items-center justify-end gap-3">
            <button
              onClick={onClose}
              className="px-5 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-600 hover:bg-gray-50 transition-colors"
            >
              Huỷ
            </button>
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="px-5 py-2.5 bg-orange-500 hover:bg-orange-600 text-white rounded-lg text-sm font-semibold transition-colors shadow-sm disabled:opacity-50"
            >
              {submitting ? 'Đang tạo...' : 'Tạo & Thiết kế luồng'}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
