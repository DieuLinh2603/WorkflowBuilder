package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkflowVersionComparisonResponse {
    private UUID fromVersionId;
    private String fromVersion;
    private UUID toVersionId;
    private String toVersion;
    private List<String> changes;
}
