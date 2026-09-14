package com.company.workflowbuilder.entity.workflow;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "workflow_audiences", uniqueConstraints = @UniqueConstraint(columnNames = { "workflow_id", "subject_type",
        "subject_value" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowAudience {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private AudienceType subjectType;
    @Column(name = "subject_value", nullable = false)
    private String subjectValue;
}
