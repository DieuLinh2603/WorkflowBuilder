package com.company.workflowbuilder.entity.workflow;

import com.company.workflowbuilder.entity.form.FormVersion;
import com.company.workflowbuilder.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow entity — represents a workflow definition (design-time).
 * Created by Admin or Workflow Owner.
 * Always starts as DRAFT with version "1.0".
 */
@Entity
@Table(name = "workflows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** E.g. "Phê duyệt chi phí", "Nhân sự" */
    private String type;

    /** User-provided label when type is CUSTOM. */
    @Column(name = "custom_type_name")
    private String customTypeName;

    /** E.g. "Hành chính - Nhân sự", "Kinh doanh" */
    private String module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id")
    private FormVersion formVersion;

    @Column(nullable = false)
    @Builder.Default
    private String version = "1.0";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    /** Stable id shared by all immutable versions of one business workflow. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_workflow_id")
    private Workflow sourceWorkflow;

    @ManyToMany
    @JoinTable(
        name = "workflow_editors",
        joinColumns = @JoinColumn(name = "workflow_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> editors = new HashSet<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkflowStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkflowConnection> connections = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeFamilyId() {
        if (this.familyId == null) {
            this.familyId = (this.id != null) ? this.id : UUID.randomUUID();
        }
    }
}
