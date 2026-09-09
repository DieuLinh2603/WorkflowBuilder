package com.company.workflowbuilder.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity — represents an account in the system.
 * Accounts are created by Admin (no self sign-up).
 *
 * A user CAN have zero SystemRoles — in that case they are an
 * "Approver-only" user who can only act on steps they are assigned to
 * via StepActorRule at runtime.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "job_title")
    private String jobTitle;

    /**
     * Self-reference: used by Dynamic ActorMode to resolve
     * REQUEST_CREATOR_MANAGER at runtime.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(name = "data_source", nullable = false)
    @Builder.Default
    private String dataSource = "MANUAL";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "auth_version", nullable = false)
    @Builder.Default
    private int authVersion = 0;

    /**
     * SystemRoles stored as @ElementCollection in user_system_role table.
     * Can be empty — "Approver-only" users have no system-level access.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_system_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<SystemRole> systemRoles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
