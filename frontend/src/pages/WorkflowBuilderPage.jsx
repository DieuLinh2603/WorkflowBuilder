import { ArrowLeft, Save, Play, Settings, MoreHorizontal, MousePointer2, GitCommit, CheckSquare, MessageSquare, AlertCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export default function WorkflowBuilderPage() {
  const navigate = useNavigate();

  return (
    <div className="fixed inset-0 bg-grayLight flex flex-col z-50 overflow-hidden">
      {/* Top Header */}
      <header className="h-14 bg-white border-b border-grayBorder flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(-1)} className="text-gray-500 hover:text-gray-800 transition-colors">
            <ArrowLeft size={20} />
          </button>
          <div className="h-6 w-px bg-gray-200"></div>
          <div>
            <h1 className="font-bold text-gray-800 text-sm flex items-center gap-2">
              Onboarding nhân viên mới <span className="bg-gray-100 text-gray-500 px-2 py-0.5 rounded text-xs">v1.0 - Draft</span>
            </h1>
            <p className="text-[11px] text-gray-500 mt-0.5">Tự động lưu lúc 14:02</p>
          </div>
        </div>
        
        <div className="flex items-center gap-3">
          <button className="text-gray-600 hover:text-gray-800 font-medium text-sm flex items-center gap-1.5 px-3 py-1.5 rounded-lg hover:bg-gray-100">
            <Play size={16} /> Chạy thử
          </button>
          <button className="text-gray-600 hover:text-gray-800 font-medium text-sm flex items-center gap-1.5 px-3 py-1.5 rounded-lg hover:bg-gray-100">
            <Save size={16} /> Lưu nháp
          </button>
          <button className="btn-primary flex items-center gap-2 py-1.5 text-sm">
            Publish Workflow
          </button>
          <button className="text-gray-400 hover:text-gray-600">
            <MoreHorizontal size={20} />
          </button>
        </div>
      </header>

      <div className="flex-1 flex overflow-hidden">
        {/* Left Sidebar - Toolbox */}
        <div className="w-64 bg-white border-r border-grayBorder flex flex-col shrink-0">
          <div className="p-4 border-b border-grayBorder">
            <h2 className="font-bold text-gray-800 text-sm">Workflow Steps</h2>
            <p className="text-xs text-gray-500 mt-1">Kéo thả các bước vào canvas</p>
          </div>
          
          <div className="p-4 space-y-4 overflow-y-auto">
            <div>
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Sự kiện</h3>
              <div className="space-y-2">
                <div className="flex items-center gap-3 p-2.5 rounded-lg border border-grayBorder bg-white hover:border-green-400 hover:shadow-sm cursor-grab transition-all">
                  <div className="w-8 h-8 rounded-full bg-green-100 text-green-600 flex items-center justify-center">
                    <MousePointer2 size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Start Step</p>
                    <p className="text-[10px] text-gray-500">Điểm bắt đầu quy trình</p>
                  </div>
                </div>
                
                <div className="flex items-center gap-3 p-2.5 rounded-lg border border-grayBorder bg-white hover:border-red-400 hover:shadow-sm cursor-grab transition-all">
                  <div className="w-8 h-8 rounded-full bg-red-100 text-red-600 flex items-center justify-center">
                    <AlertCircle size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800">End Step</p>
                    <p className="text-[10px] text-gray-500">Kết thúc quy trình</p>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2 mt-4">Hành động</h3>
              <div className="space-y-2">
                <div className="flex items-center gap-3 p-2.5 rounded-lg border border-grayBorder bg-white hover:border-orange-400 hover:shadow-sm cursor-grab transition-all">
                  <div className="w-8 h-8 rounded-lg bg-orange-100 text-orange-600 flex items-center justify-center">
                    <CheckSquare size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Approval Step</p>
                    <p className="text-[10px] text-gray-500">Yêu cầu phê duyệt (Approve/Reject)</p>
                  </div>
                </div>

                <div className="flex items-center gap-3 p-2.5 rounded-lg border border-grayBorder bg-white hover:border-blue-400 hover:shadow-sm cursor-grab transition-all">
                  <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center">
                    <MessageSquare size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Review Step</p>
                    <p className="text-[10px] text-gray-500">Kiểm tra và sửa dữ liệu</p>
                  </div>
                </div>
                
                <div className="flex items-center gap-3 p-2.5 rounded-lg border border-grayBorder bg-white hover:border-purple-400 hover:shadow-sm cursor-grab transition-all">
                  <div className="w-8 h-8 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center">
                    <GitCommit size={16} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Assignment Step</p>
                    <p className="text-[10px] text-gray-500">Giao việc bổ sung form data</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Main Canvas Area */}
        <div className="flex-1 bg-[#F8FAFC] relative overflow-hidden flex flex-col">
          {/* Dot grid background */}
          <div className="absolute inset-0 pointer-events-none" style={{ backgroundImage: 'radial-gradient(#CBD5E1 1px, transparent 1px)', backgroundSize: '20px 20px' }}></div>
          
          <div className="flex-1 relative">
            {/* Mock Canvas Content - To be replaced by React Flow */}
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 flex items-center gap-12">
              
              {/* Start Node */}
              <div className="w-48 bg-white border-2 border-green-400 rounded-xl shadow-sm p-4 relative">
                <div className="w-8 h-8 rounded-full bg-green-100 text-green-600 flex items-center justify-center mb-3">
                  <MousePointer2 size={16} />
                </div>
                <h3 className="font-bold text-gray-800 text-sm">Bắt đầu</h3>
                <p className="text-xs text-gray-500 mt-1">Form nhập liệu NV mới</p>
                <div className="absolute top-1/2 -right-3 w-3 h-3 bg-white border-2 border-green-400 rounded-full"></div>
              </div>

              {/* Arrow */}
              <div className="w-12 h-0.5 bg-gray-300 relative">
                <div className="absolute right-0 top-1/2 -translate-y-1/2 border-t-4 border-b-4 border-l-6 border-transparent border-l-gray-300"></div>
              </div>

              {/* Approval Node */}
              <div className="w-48 bg-white border-2 border-orange-400 rounded-xl shadow-lg p-4 relative ring-4 ring-orange-100">
                <div className="absolute top-1/2 -left-3 w-3 h-3 bg-white border-2 border-orange-400 rounded-full"></div>
                <div className="w-8 h-8 rounded-lg bg-orange-100 text-orange-600 flex items-center justify-center mb-3">
                  <CheckSquare size={16} />
                </div>
                <h3 className="font-bold text-gray-800 text-sm">Trưởng phòng duyệt</h3>
                <p className="text-xs text-gray-500 mt-1">Approver: Manager của NS</p>
                
                <div className="absolute top-1/2 -right-3 w-3 h-3 bg-white border-2 border-orange-400 rounded-full"></div>
                <div className="absolute bottom-[-6px] left-1/2 -translate-x-1/2 w-3 h-3 bg-white border-2 border-red-400 rounded-full"></div>
              </div>

              {/* Arrow */}
              <div className="w-12 h-0.5 bg-gray-300 relative">
                <div className="absolute right-0 top-1/2 -translate-y-1/2 border-t-4 border-b-4 border-l-6 border-transparent border-l-gray-300"></div>
              </div>

              {/* End Node */}
              <div className="w-48 bg-white border-2 border-red-400 rounded-xl shadow-sm p-4 relative opacity-60">
                 <div className="absolute top-1/2 -left-3 w-3 h-3 bg-white border-2 border-red-400 rounded-full"></div>
                <div className="w-8 h-8 rounded-full bg-red-100 text-red-600 flex items-center justify-center mb-3">
                  <AlertCircle size={16} />
                </div>
                <h3 className="font-bold text-gray-800 text-sm">Kết thúc</h3>
                <p className="text-xs text-gray-500 mt-1">Hoàn thành quy trình</p>
              </div>
            </div>
          </div>
          
          {/* Zoom controls */}
          <div className="absolute bottom-6 left-6 flex bg-white rounded-lg shadow border border-grayBorder p-1">
             <button className="w-8 h-8 flex items-center justify-center text-gray-600 hover:bg-gray-100 rounded font-bold">-</button>
             <div className="w-12 flex items-center justify-center text-xs font-semibold text-gray-700 border-x border-grayBorder">100%</div>
             <button className="w-8 h-8 flex items-center justify-center text-gray-600 hover:bg-gray-100 rounded font-bold">+</button>
          </div>
        </div>

        {/* Right Sidebar - Properties Panel */}
        <div className="w-80 bg-white border-l border-grayBorder flex flex-col shrink-0 shadow-[-4px_0_15px_-3px_rgba(0,0,0,0.05)] z-10">
          <div className="p-4 border-b border-grayBorder flex items-center justify-between bg-orange-50/30">
             <div className="flex items-center gap-2">
                <div className="w-6 h-6 bg-orange-100 text-orange-600 rounded flex items-center justify-center">
                  <Settings size={14} />
                </div>
                <h2 className="font-bold text-gray-800 text-sm">Cấu hình Step</h2>
             </div>
          </div>
          
          <div className="flex-1 overflow-y-auto p-5 space-y-6">
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">Tên bước</label>
              <input type="text" defaultValue="Trưởng phòng duyệt" className="input-field py-2 text-sm font-medium" />
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">Người xử lý (Approver)</label>
              <select className="input-field py-2 text-sm bg-white text-gray-700">
                <option>Quản lý trực tiếp (Manager)</option>
                <option>Chọn User cụ thể...</option>
                <option>Bất kỳ ai trong nhóm...</option>
              </select>
            </div>

            <div className="border-t border-grayBorder pt-5">
              <div className="flex items-center justify-between mb-3">
                 <label className="block text-sm font-semibold text-gray-700">Custom Fields (Trường dữ liệu)</label>
              </div>
              <p className="text-xs text-gray-500 mb-3 italic">Approval Step không cho phép thêm Custom Fields. Trường dữ liệu chỉ được tạo ở Start Step hoặc Assignment Step.</p>
              
              <div className="bg-gray-50 p-3 rounded-lg border border-grayBorder border-dashed">
                 <p className="text-xs font-semibold text-gray-700 mb-2">Trường hiển thị từ bước trước (Read-only)</p>
                 <div className="space-y-2">
                   <div className="flex items-center justify-between text-xs bg-white border border-grayBorder p-2 rounded">
                      <span className="font-medium text-gray-700">Họ tên NV mới</span>
                      <span className="text-gray-400">Text</span>
                   </div>
                   <div className="flex items-center justify-between text-xs bg-white border border-grayBorder p-2 rounded">
                      <span className="font-medium text-gray-700">Vị trí tuyển dụng</span>
                      <span className="text-gray-400">Dropdown</span>
                   </div>
                 </div>
              </div>
            </div>

            <div className="border-t border-grayBorder pt-5">
              <label className="block text-sm font-semibold text-gray-700 mb-1">Thời gian SLA (giờ)</label>
              <div className="flex gap-2">
                <input type="number" defaultValue="48" className="input-field py-2 text-sm w-24 text-center" />
                <div className="flex-1 flex items-center px-3 bg-gray-50 border border-grayBorder rounded-lg text-xs text-gray-500">
                  Cảnh báo quá hạn sau 48h
                </div>
              </div>
            </div>
          </div>
          
          <div className="p-4 border-t border-grayBorder bg-gray-50 flex gap-3">
            <button className="flex-1 bg-white border border-grayBorder text-gray-700 py-2 rounded-lg font-medium text-sm hover:bg-gray-50 transition-colors">
              Hủy
            </button>
            <button className="flex-1 btn-primary py-2 text-sm">
              Lưu cấu hình
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
