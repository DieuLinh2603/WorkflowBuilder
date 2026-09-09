package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.user.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * User response DTO — never exposes password/token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String displayName;
    private String jobTitle;
    private UUID managerId;
    private String managerName;
    private String dataSource;
    private boolean active;
    private Set<SystemRole> systemRoles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
