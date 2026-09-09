package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.field.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomFieldCreateRequest {
    
    @NotBlank(message = "Tên field không được để trống")
    private String label;
    
    private String fieldKey;
    
    @NotNull(message = "Loại dữ liệu không được để trống")
    private FieldType type;
    
    private boolean required;
    
    private String placeholder;
}
