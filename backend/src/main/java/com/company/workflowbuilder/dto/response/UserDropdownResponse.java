package com.company.workflowbuilder.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDropdownResponse {
    private UUID id;
    private String displayName;
    private String jobTitle;
    private String avatarInitials;
    private String avatarColor;
}
