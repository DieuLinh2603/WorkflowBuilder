package com.company.workflowbuilder.entity.form;

import com.company.workflowbuilder.entity.field.FieldType;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name="form_fields",uniqueConstraints=@UniqueConstraint(columnNames={"form_version_id","field_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FormField {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="form_version_id",nullable=false) private FormVersion formVersion;
    @Column(name="field_key",nullable=false) private String fieldKey;
    @Column(nullable=false) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private FieldType type;
    @Column(nullable=false) private boolean required;
    private String placeholder;
    @Column(name="configuration_json",nullable=false,columnDefinition="TEXT") @Builder.Default private String configurationJson="{}";
    @Column(name="display_order",nullable=false) private int displayOrder;
}
