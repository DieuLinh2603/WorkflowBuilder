package com.company.workflowbuilder.dto.request.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class FormVersionUpdateRequest {
    private String name;           // optional – update form name
    private String description;    // optional – update form description
    private String instruction;
    private String submissionMode; // "SINGLE" hoặc "BATCH"
    @Min(1)
    @Max(2000)
    private Integer maxBatchRows;
    private String recordRecipientFieldKey;
    @Valid
    private List<FormFieldRequest> fields;
}
