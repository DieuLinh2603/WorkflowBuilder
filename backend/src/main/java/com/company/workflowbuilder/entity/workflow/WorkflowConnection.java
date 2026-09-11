package com.company.workflowbuilder.entity.workflow;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_step_id", nullable = false)
    private WorkflowStep fromStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_step_id", nullable = false)
    private WorkflowStep toStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_operator", nullable = false)
    @Builder.Default
    private LogicalOperator logicalOperator = LogicalOperator.AND;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    @OneToMany(mappedBy = "connection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<WorkflowConditionClause> clauses = new ArrayList<>();
}
