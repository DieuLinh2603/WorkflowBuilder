package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.user.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserListItemResponse {
    private UUID id;
    private String avatarInitials;
    private String avatarColor;
    private String displayName;
    private String email;
    private String jobTitle;
    private UUID managerId;
    private String managerName;
    private Set<SystemRole> systemRoles;
    private Set<String> moduleCodes;
    private LocalDateTime createdAt;
}
