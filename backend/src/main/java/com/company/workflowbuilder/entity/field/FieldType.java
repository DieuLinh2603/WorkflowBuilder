package com.company.workflowbuilder.entity.field;

public enum FieldType {
    // --- Text ---
    TEXT,           // Input 1 dòng
    TEXTAREA,       // Nhập nhiều dòng
    EMAIL,          // Email (validate format)
    PHONE,          // Số điện thoại
    URL,            // Đường dẫn web

    // --- Numeric ---
    NUMBER,         // Số nguyên / thập phân
    CURRENCY,       // Tiền tệ (number + đơn vị tiền tệ)

    // --- Date/Time ---
    DATE,           // Ngày (yyyy-MM-dd)
    DATE_TIME,      // Ngày + giờ (yyyy-MM-ddTHH:mm)
    TIME,           // Giờ (HH:mm)

    // --- Choice ---
    CHECKBOX,       // Boolean (true/false, có/không)
    RADIO,          // Chọn 1 từ danh sách cố định
    SELECT,         // Dropdown chọn 1
    MULTI_SELECT,   // Dropdown chọn nhiều

    // --- File ---
    FILE,           // Upload 1 file
    MULTI_FILE,     // Upload nhiều file

    // --- System Reference ---
    USER_PICKER,    // Chọn user từ hệ thống → {id, displayName}
    GROUP_PICKER,   // Chọn nhóm từ hệ thống

    // --- Advanced ---
    RICH_TEXT,      // Editor soạn thảo HTML
    SIGNATURE,      // Chữ ký điện tử (canvas → base64)
    RATING,         // Đánh giá sao (1-5 hoặc 1-10)
    HIDDEN          // Field ẩn trong UI, có trong payload
}
