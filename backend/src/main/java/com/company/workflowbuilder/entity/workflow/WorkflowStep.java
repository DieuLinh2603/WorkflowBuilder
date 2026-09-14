package com.company.workflowbuilder.entity.workflow;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * WorkflowStep — one node on the canvas.
 * Each workflow starts with exactly one START step (auto-created).
 * configJson stores step-specific configuration as JSONB.
 */
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

    @Column(name = "position_x")
    @Builder.Default
    private int positionX = 0;

    @Column(name = "position_y")
    @Builder.Default
    private int positionY = 0;

    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private String configJson = "{}";
}
