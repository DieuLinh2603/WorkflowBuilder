package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.*;

@Data
public class NotificationStepConfigRequest {
    @NotBlank
    private String titleTemplate;
    @NotBlank
    private String bodyTemplate;
    private List<UUID> recipientUserIds = new ArrayList<>();
    private List<UUID> recipientGroupIds = new ArrayList<>();
    private Set<String> recipientRoles = new HashSet<>();
    private Set<String> channels = new HashSet<>(Set.of("IN_APP"));
    private Set<String> triggers = new HashSet<>(Set.of("STEP_ACTIVATED"));
    private String webhookUrl;
    private boolean includeRequester;
    private boolean includeNextStepActors;
    private boolean includePreviousActor;
    /** Resolve an active application user from the configured row email/user-id field. */
    private boolean includeRecordRecipient;
}
