package com.company.workflowbuilder.entity.field;

import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "custom_field_definitions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"step_id", "field_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    private WorkflowStep step;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType type;

    @Column(nullable = false)
    private boolean required;

    private String placeholder;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
