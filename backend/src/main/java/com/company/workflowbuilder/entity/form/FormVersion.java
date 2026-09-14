package com.company.workflowbuilder.entity.form;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="form_versions",uniqueConstraints=@UniqueConstraint(columnNames={"form_id","version_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FormVersion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="form_id",nullable=false) private RequestForm form;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false) @Builder.Default private FormStatus status=FormStatus.DRAFT;
    @Column(columnDefinition="TEXT") private String instruction;
    @Column(name="submission_mode",nullable=false) @Builder.Default private String submissionMode="SINGLE";
    @Column(name="record_recipient_field_key") private String recordRecipientFieldKey;
    @Column(name="max_batch_rows",nullable=false) @Builder.Default private int maxBatchRows=500;
    @Column(name="published_at") private LocalDateTime publishedAt;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
