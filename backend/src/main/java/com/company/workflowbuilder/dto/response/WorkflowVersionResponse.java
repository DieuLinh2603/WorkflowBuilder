package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkflowVersionResponse {
    private UUID id;
    private UUID familyId;
    private String name;
    private String version;
    private String status;
    private UUID ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int stepCount;
    private int fieldCount;
    private int connectionCount;
    private List<String> changes;
    private boolean canRestore;
}
