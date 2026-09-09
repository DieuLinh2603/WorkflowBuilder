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
      if (showSuccessToast) window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'success', message: successMessage || successText(method) } }));
    } else if (showErrorToast) {
      const message = await apiError(response.clone(), errorMessage || 'Không thể thực hiện thao tác');
      window.dispatchEvent(new CustomEvent('wf:toast', { detail: { type: 'error', message } }));
    }
  }
  return response;
}

function successText(method) {
  if (method === 'POST') return 'Đã tạo hoặc thực hiện thao tác thành công.';
  if (method === 'DELETE') return 'Đã xóa dữ liệu thành công.';
  return 'Đã lưu thay đổi thành công.';
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
