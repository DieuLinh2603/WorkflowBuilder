package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.user.SystemRole;
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
public class AuthResponse {

    private String token;
    private String tokenType;
    private UUID userId;
    private String email;
    private String displayName;
    private Set<SystemRole> systemRoles;
}
