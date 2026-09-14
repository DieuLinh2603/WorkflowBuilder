export async function apiFetch(url, options = {}) {
  const { successMessage, errorMessage, toast = true, successToast, errorToast, ...fetchOptions } = options;
  const showSuccessToast = successToast ?? toast;
  const showErrorToast = errorToast ?? toast;
  const token = localStorage.getItem('wf_token');
  const headers = new Headers(fetchOptions.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (fetchOptions.body && !(fetchOptions.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(url, { ...fetchOptions, headers });
  if (response.status === 401) {
    localStorage.removeItem('wf_token');
    localStorage.removeItem('wf_user');
    window.dispatchEvent(new Event('wf:unauthorized'));
  }
  const method = (fetchOptions.method || 'GET').toUpperCase();
  const isEditWorkingCopyRequest = method === 'POST' && /\/api\/workflows\/[^/]+\/versions$/.test(url);
  if (!isEditWorkingCopyRequest && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && !url.startsWith('/api/auth/')) {
    if (response.ok) {
      const message = successMessage || successText(method, url);
      if (showSuccessToast && message) window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message } }));
    } else if (showErrorToast) {
      const message = await apiError(response.clone(), errorMessage || 'Không thể thực hiện thao tác');
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'error', message } }));
    }
  }
  return response;
}

function successText(method, rawUrl) {
  const url = rawUrl.split('?')[0];
  const matches = pattern => pattern.test(url);

  if (matches(/^\/api\/workflows$/) && method === 'POST') return 'Đã tạo workflow mới.';
  if (matches(/\/workflows\/[^/]+\/publish$/)) return 'Đã xuất bản workflow.';
  if (matches(/\/workflows\/[^/]+\/archive$/)) return 'Đã lưu trữ workflow.';
  if (matches(/\/workflows\/[^/]+\/restore$/)) return 'Đã khôi phục phiên bản workflow.';
  if (matches(/\/workflows\/[^/]+\/duplicate$/)) return 'Đã nhân bản workflow.';
  if (matches(/\/workflows\/[^/]+\/audience$/)) return 'Đã cập nhật đối tượng được phép sử dụng workflow.';
  if (matches(/\/workflows\/[^/]+\/editors\/[^/]+$/)) return method === 'DELETE' ? 'Đã gỡ người chỉnh sửa khỏi workflow.' : 'Đã thêm người chỉnh sửa vào workflow.';
  if (matches(/\/workflows\/[^/]+\/connections\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa nhánh kết nối.' : 'Đã cập nhật điều kiện nhánh kết nối.';
  if (matches(/\/workflows\/[^/]+\/connections$/)) return 'Đã tạo nhánh kết nối.';
  if (matches(/\/workflows\/[^/]+\/steps\/[^/]+\/fields\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa trường dữ liệu.' : 'Đã cập nhật trường dữ liệu.';
  if (matches(/\/workflows\/[^/]+\/steps\/[^/]+\/fields$/)) return 'Đã thêm trường dữ liệu.';
  if (matches(/\/workflows\/[^/]+\/steps\/[^/]+\/start-config$/)) return 'Đã lưu cấu hình bước bắt đầu.';
  if (matches(/\/workflows\/[^/]+\/steps\/[^/]+\/config\/([^/]+)$/)) return 'Đã lưu cấu hình bước workflow.';
  if (matches(/\/workflows\/[^/]+\/steps\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa bước khỏi workflow.' : 'Đã cập nhật bước workflow.';
  if (matches(/\/workflows\/[^/]+\/steps$/)) return 'Đã thêm bước vào workflow.';
  if (matches(/^\/api\/users\/[^/]+\/deactivate$/)) return 'Đã vô hiệu hóa tài khoản người dùng.';
  if (matches(/^\/api\/users\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa người dùng.' : 'Đã cập nhật thông tin người dùng.';
  if (matches(/^\/api\/users$/)) return 'Đã tạo tài khoản người dùng.';
  if (matches(/^\/api\/connectors\/[^/]+\/test$/)) return 'Kết nối hoạt động bình thường.';
  if (matches(/^\/api\/connectors\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa connector.' : 'Đã cập nhật connector.';
  if (matches(/^\/api\/connectors$/)) return 'Đã tạo connector.';
  if (matches(/^\/api\/pipelines\/[^/]+\/files$/)) return 'Đã tải file nguồn lên pipeline.';
  if (matches(/^\/api\/pipelines\/[^/]+\/publish$/)) return 'Đã kích hoạt pipeline.';
  if (matches(/^\/api\/pipelines\/[^/]+\/schedule$/)) return 'Đã cập nhật lịch chạy pipeline.';
  if (matches(/^\/api\/pipelines\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa pipeline.' : 'Đã lưu cấu hình pipeline.';
  if (matches(/^\/api\/pipelines$/)) return 'Đã tạo pipeline.';
  if (matches(/^\/api\/workflow-data-bindings(\/[^/]+)?$/)) return method === 'POST' ? 'Đã tạo liên kết dữ liệu với workflow.' : 'Đã cập nhật liên kết dữ liệu với workflow.';
  if (matches(/^\/api\/groups\/[^/]+\/members\/[^/]+$/)) return method === 'DELETE' ? 'Đã xóa người dùng khỏi nhóm.' : 'Đã thêm người dùng vào nhóm.';
  if (matches(/^\/api\/groups$/)) return 'Đã tạo nhóm người dùng.';
  if (matches(/^\/api\/settings\/deadline-reminder$/)) return 'Đã cập nhật thời gian nhắc deadline.';
  if (matches(/^\/api\/settings\/reminder-endpoints$/)) return 'Đã cập nhật endpoint nhận nhắc việc.';
  if (matches(/^\/api\/metadata\/(modules|workflow-types)\/[^/]+\/status$/)) return rawUrl.includes('active=true') ? 'Đã kích hoạt mục danh mục.' : 'Đã ngừng sử dụng mục danh mục.';
  if (matches(/^\/api\/metadata\/(modules|workflow-types)(\/[^/]+)?$/)) return method === 'POST' ? 'Đã thêm mục vào danh mục nghiệp vụ.' : 'Đã cập nhật danh mục nghiệp vụ.';
  if (matches(/^\/api\/tasks\/[^/]+\/approve$/)) return 'Đã phê duyệt nhiệm vụ.';
  if (matches(/^\/api\/tasks\/[^/]+\/reject$/)) return 'Đã từ chối nhiệm vụ.';
  if (matches(/^\/api\/tasks\/[^/]+\/complete$/)) return 'Đã hoàn thành nhiệm vụ.';
  return null;
}

export async function apiError(response, fallback = 'Không thể thực hiện yêu cầu') {
  const raw = await response.text().catch(() => '');
  let body = null;
  if (raw) {
    try {
      body = JSON.parse(raw);
    } catch {
      body = null;
    }
  }

  if (body?.fieldErrors) {
    const firstFieldError = Object.values(body.fieldErrors).find(Boolean);
    if (firstFieldError) return firstFieldError;
  }
  if (body?.message) return body.message;
  if (response.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (response.status === 403) return 'Tài khoản hiện tại không có quyền thực hiện thao tác này.';
  if (response.status >= 500) return 'Backend gặp lỗi khi xử lý yêu cầu. Vui lòng kiểm tra log backend và thử lại.';
  return fallback;
}
