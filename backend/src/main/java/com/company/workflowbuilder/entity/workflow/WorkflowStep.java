package com.company.workflowbuilder.entity.workflow;

import com.company.workflowbuilder.entity.form.FormVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "workflow_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType type;

    private String label;

    @Builder.Default
    @Column(name = "position_x")
    private int positionX = 0;

    @Builder.Default
    @Column(name = "position_y")
    private int positionY = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

    @ManyToMany
    @JoinTable(
        name = "step_forms",
        joinColumns = @JoinColumn(name = "step_id"),
        inverseJoinColumns = @JoinColumn(name = "form_version_id")
    )
    @Builder.Default
    private Set<FormVersion> formVersions = new HashSet<>();
}
