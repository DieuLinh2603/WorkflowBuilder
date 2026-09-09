package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="workflow_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowTask {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="instance_id",nullable=false) private WorkflowInstance instance;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="step_id",nullable=false) private WorkflowStep step;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assignee_id",nullable=false) private User assignee;
    @Column(name="activation_id",nullable=false) private UUID activationId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) @Builder.Default private TaskStatus status=TaskStatus.PENDING;
    @Column(name="deadline_at") private LocalDateTime deadlineAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
