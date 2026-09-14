package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.entity.form.FormVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "workflow_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    /** Form definition frozen when this ticket was submitted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false)
    private FormVersion formVersion;

    @Column(name = "request_code", nullable = false, unique = true)
    private String requestCode;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "batch_row_number")
    private Integer batchRowNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private WorkflowStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InstanceStatus status = InstanceStatus.RUNNING;

    @Column(name = "field_snapshot", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String fieldSnapshot = "{}";

    @Column(name = "condition_blocked", nullable = false)
    @Builder.Default
    private boolean conditionBlocked = false;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawn_by")
    private User withdrawnBy;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdrawal_reason", columnDefinition = "TEXT")
    private String withdrawalReason;

    @Column(name = "requester_withdrawal_allowed", nullable = false)
    @Builder.Default
    private boolean requesterWithdrawalAllowed = true;

    @Version
    private Long lockVersion;
}
