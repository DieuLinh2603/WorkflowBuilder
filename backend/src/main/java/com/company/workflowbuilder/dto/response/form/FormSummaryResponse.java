package com.company.workflowbuilder.dto.response.form;

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
public class FormSummaryResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean archived;
    private List<FormVersionSummary> versions;
    private FormVersionSummary latestPublishedVersion;
    private FormVersionSummary draftVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
