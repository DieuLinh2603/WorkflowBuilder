package com.company.workflowbuilder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;

@Data
public class SystemActionConfigRequest {
    public enum ActionType {
        API_CALL, SEND_NOTIFICATION, UPDATE_DATA, CREATE_RECORD, UPDATE_STATUS, CALCULATE_OUTPUT
    }

    public enum FailurePolicy {
        STOP, CONTINUE, RETRY
    }

    @NotNull
    private ActionType actionType = ActionType.API_CALL;
    private String endpointUrl;
    private String httpMethod = "POST";
    private String payloadTemplate = "{}";
    private String notificationChannel = "IN_APP";
    private String notificationTitle;
    private String notificationBody;
    private String webhookUrl;
    private String targetType = "REQUEST";
    private String recordType;
    private String newStatus;
    private List<com.company.workflowbuilder.dto.CalculatedOutput> calculatedOutputs = new ArrayList<>();
    @Valid
    private List<Mapping> mappings = new ArrayList<>();
    @NotNull
    private FailurePolicy failurePolicy = FailurePolicy.STOP;
    @Min(1)
    @Max(10)
    private int retryCount = 3;
    @Min(1)
    @Max(300)
    private int timeoutSeconds = 30;

    @Data
    public static class Mapping {
        private String sourceFieldKey;
        @NotBlank
        private String targetField;
        private String valueTemplate;
    }
}
