package com.company.workflowbuilder.entity.data;

import com.company.workflowbuilder.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="data_pipelines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataPipeline {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false) private String name;
 @Column(columnDefinition="TEXT") private String description;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="owner_id",nullable=false) private User owner;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="shared_with_id") private User sharedWith;
 @Column(nullable=false) @Builder.Default private String status="DRAFT";
 @Column(name="definition_json",nullable=false,columnDefinition="TEXT") @Builder.Default private String definitionJson="{}";
 @Column(name="output_schema_json",nullable=false,columnDefinition="TEXT") @Builder.Default private String outputSchemaJson="[]";
 @Column(name="business_key",nullable=false) private String businessKey;
 @Column(name="schedule_type",nullable=false) @Builder.Default private String scheduleType="MANUAL";
 @Column(name="scheduled_at") private LocalDateTime scheduledAt;
 @Column(name="daily_time") private LocalTime dailyTime;
 @Column(nullable=false) @Builder.Default private String timezone="Asia/Ho_Chi_Minh";
 @Column(name="next_run_at") private LocalDateTime nextRunAt;
 @Column(name="published_at") private LocalDateTime publishedAt;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
