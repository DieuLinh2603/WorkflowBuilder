package com.company.workflowbuilder.entity.data;

import com.company.workflowbuilder.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name="data_connectors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataConnector {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,unique=true) private String name;
    @Column(columnDefinition="TEXT") private String description;
    @Column(name="connector_type",nullable=false) private String connectorType;
    @Column(name="config_json",nullable=false,columnDefinition="TEXT") @Builder.Default private String configJson="{}";
    @Column(name="encrypted_credentials",columnDefinition="TEXT") private String encryptedCredentials;
    @Column(nullable=false) @Builder.Default private boolean active=true;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false) private User createdBy;
    @ManyToMany(fetch=FetchType.LAZY) @JoinTable(name="data_connector_grants",joinColumns=@JoinColumn(name="connector_id"),inverseJoinColumns=@JoinColumn(name="user_id")) @Builder.Default private Set<User> grantedUsers=new HashSet<>();
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
