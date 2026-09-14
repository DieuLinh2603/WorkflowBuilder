package com.company.workflowbuilder.entity.metadata;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowTypeDefinition {
    @Id @Column(length = 64)
    private String code;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "guidance_json", nullable = false, columnDefinition = "TEXT") @Builder.Default
    private String guidanceJson = "{}";
    @Column(nullable = false) @Builder.Default
    private boolean active = true;
    @Column(name = "sort_order", nullable = false) @Builder.Default
    private int sortOrder = 0;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
