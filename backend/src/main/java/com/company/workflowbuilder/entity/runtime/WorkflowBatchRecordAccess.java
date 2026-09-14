package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_batch_record_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"batch_record_id", "user_id", "access_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowBatchRecordAccess {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "batch_record_id", nullable = false)
    private WorkflowBatchRecord batchRecord;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "access_type", nullable = false, length = 32) private String accessType;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
