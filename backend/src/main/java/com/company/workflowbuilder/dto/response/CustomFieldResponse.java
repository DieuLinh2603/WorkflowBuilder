package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.field.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomFieldResponse {
    private UUID id;
    private String fieldKey;
    private String label;
    private FieldType type;
    private boolean required;
    private String placeholder;
    private int displayOrder;
}
