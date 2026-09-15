package com.company.workflowbuilder.dto.request.form;

import com.company.workflowbuilder.dto.FieldOption;
import com.company.workflowbuilder.entity.field.FieldType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormFieldRequest {
    private String fieldKey;       // nullable — server auto-generate nếu null
    @NotBlank
    private String label;
    @NotNull
    private FieldType type;
    private boolean required;
    private String placeholder;
    private String configurationJson;
    private List<FieldOption> options;
    private Boolean allowMultiple;
}
