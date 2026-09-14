package com.company.workflowbuilder.entity.workflow;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "workflow_condition_clauses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowConditionClause {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private WorkflowConnection connection;
    @Column(name = "field_key")
    private String fieldKey;
    @Enumerated(EnumType.STRING)
    @Column
    private ConditionOperator operator;
    @Column(name = "expected_value")
    private String expectedValue;
    @Column(name = "expression_json", columnDefinition = "TEXT")
    private String expressionJson;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
