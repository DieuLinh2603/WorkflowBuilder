package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.user.SystemRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String jobTitle;

    private UUID managerId;

    @NotEmpty(message = "At least one system role is required")
    private Set<SystemRole> systemRoles;

    private Set<String> moduleCodes;

    /**
     * Optional: reset password. Null means keep current password.
     */
    private String newPassword;
}
