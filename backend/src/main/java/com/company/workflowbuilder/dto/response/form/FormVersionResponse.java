package com.company.workflowbuilder.dto.response.form;

import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.entity.form.FormStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormVersionResponse {
    private UUID id;
    private UUID formId;
    private String formName;
    private String description;
    private int versionNumber;
    private FormStatus status;
    private String instruction;
    private String submissionMode;
    private int maxBatchRows;
    private String recordRecipientFieldKey;
    private List<CustomFieldResponse> fields;
    private LocalDateTime publishedAt;
}
