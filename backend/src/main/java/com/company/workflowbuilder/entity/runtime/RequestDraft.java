package com.company.workflowbuilder.entity.runtime;

import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.form.FormVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_drafts", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "workflow_family_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "workflow_family_id", nullable = false)
    private UUID workflowFamilyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private Workflow workflowVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false)
    private FormVersion formVersion;

    @Column(name = "field_snapshot", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String fieldSnapshot = "{}";

    @Version
    private Long lockVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
