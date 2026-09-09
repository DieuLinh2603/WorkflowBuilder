package com.company.workflowbuilder.entity.data;
import jakarta.persistence.*; import lombok.*; import org.hibernate.annotations.CreationTimestamp; import java.time.*; import java.util.UUID;
@Entity @Table(name="data_pipeline_runs") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PipelineRun {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pipeline_id",nullable=false) private DataPipeline pipeline;
 @Column(name="trigger_type",nullable=false) private String triggerType;
 @Column(nullable=false) private String status;
 @Column(name="scheduled_for") private LocalDateTime scheduledFor;
 @Column(name="attempt_count",nullable=false) private int attemptCount;
 @Column(name="next_attempt_at") private LocalDateTime nextAttemptAt;
 @Column(name="source_count",nullable=false) private int sourceCount;
 @Column(name="input_count",nullable=false) private int inputCount;
 @Column(name="output_count",nullable=false) private int outputCount;
 @Column(name="changed_count",nullable=false) private int changedCount;
 @Column(name="error_stage") private String errorStage;
 @Column(name="error_message") private String errorMessage;
 @Column(name="started_at") private LocalDateTime startedAt;
 @Column(name="completed_at") private LocalDateTime completedAt;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
