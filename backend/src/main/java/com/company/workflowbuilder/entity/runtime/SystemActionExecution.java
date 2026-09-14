package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_action_executions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemActionExecution {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "instance_id", nullable = false)
    private WorkflowInstance instance;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "step_id", nullable = false)
    private WorkflowStep step;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "connector_id")
    private DataConnector connector;
    @Column(name = "batch_row_number") private Integer batchRowNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default private SystemActionExecutionStatus status = SystemActionExecutionStatus.QUEUED;
    @Column(name = "attempt_count", nullable = false) @Builder.Default private int attemptCount = 0;
    @Column(name = "max_attempts", nullable = false) @Builder.Default private int maxAttempts = 1;
    @Column(name = "timeout_seconds", nullable = false) @Builder.Default private int timeoutSeconds = 30;
    @Column(name = "http_method", nullable = false, length = 12) private String httpMethod;
    @Column(name = "request_url", nullable = false, length = 2048) private String requestUrl;
    @Column(name = "request_headers_json", nullable = false, columnDefinition = "TEXT") @Builder.Default private String requestHeadersJson = "{}";
    @Column(name = "request_body", columnDefinition = "TEXT") private String requestBody;
    @Column(name = "response_mappings_json", nullable = false, columnDefinition = "TEXT") @Builder.Default private String responseMappingsJson = "[]";
    @Column(name = "response_selector_json", nullable = false, columnDefinition = "TEXT") @Builder.Default private String responseSelectorJson = "{}";
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "response_body", columnDefinition = "TEXT") private String responseBody;
    @Column(name = "mapped_outputs_json", nullable = false, columnDefinition = "TEXT") @Builder.Default private String mappedOutputsJson = "{}";
    @Column(name = "error_message", length = 2000) private String errorMessage;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "resumed_at") private LocalDateTime resumedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Version private Long lockVersion;
}
