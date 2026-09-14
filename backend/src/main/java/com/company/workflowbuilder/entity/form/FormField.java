package com.company.workflowbuilder.entity.form;

import com.company.workflowbuilder.entity.field.FieldType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(
    name = "form_fields",
    uniqueConstraints = @UniqueConstraint(columnNames = {"form_version_id", "field_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false)
    private FormVersion formVersion;

    /** Snake_case key, unique trong một form version. Auto-generated từ label. */
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType type;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;

    private String placeholder;

    /**
     * JSON array: [{value: "...", label: "..."}]
     * Dùng cho: SELECT, RADIO, MULTI_SELECT
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "jsonb")
    private String optionsJson;

    /**
     * JSON object: {minLength, maxLength, min, max, pattern, maxFileSizeMB, allowedExtensions}
     * Dùng để validate giá trị khi submit.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json", columnDefinition = "jsonb")
    private String validationJson;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
