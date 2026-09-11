package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.data.DatasetVersion;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_batch_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowBatchRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private WorkflowInstance instance;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "business_key", length = 500)
    private String businessKey;

    @Column(nullable = false)
    @Builder.Default
    private int revision = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(length = 64)
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private WorkflowStep currentStep;

    @Column(nullable = false)
    private String status;

    @Column(name = "human_action_at")
    private LocalDateTime humanActionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_dataset_version_id")
    private DatasetVersion sourceDatasetVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_json", nullable = false, columnDefinition = "jsonb")
    private String stateJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
