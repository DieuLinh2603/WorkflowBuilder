package com.company.workflowbuilder.entity.data;
import jakarta.persistence.*; import lombok.*; import org.hibernate.annotations.CreationTimestamp; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="data_datasets") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Dataset { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="pipeline_id",nullable=false,unique=true) private DataPipeline pipeline; @Column(nullable=false) private String name; @Column(name="latest_version",nullable=false) private int latestVersion; @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt; }
