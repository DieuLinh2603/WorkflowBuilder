package com.company.workflowbuilder.dto.response.form;

import com.company.workflowbuilder.entity.form.FormStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormVersionSummary {
    private UUID id;
    private UUID formId;
    private int versionNumber;
    private FormStatus status;
    private LocalDateTime publishedAt;
}
