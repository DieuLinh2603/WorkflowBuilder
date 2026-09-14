package com.company.workflowbuilder.entity.form;

public enum FormStatus {
    /** Đang chỉnh sửa, chưa public. Fields có thể thêm/sửa/xóa tự do. */
    DRAFT,
    /** Đã publish, bất biến (immutable). Có thể gắn vào workflow step. */
    PUBLISHED,
    /** Không còn dùng để gắn mới. Các step cũ vẫn giữ nguyên. */
    ARCHIVED
}
