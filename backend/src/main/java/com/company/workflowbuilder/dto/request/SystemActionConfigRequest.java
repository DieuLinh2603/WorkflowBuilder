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
    private UUID connectorId;
    private String pathTemplate = "";
    @NotBlank
    private String httpMethod = "POST";
    private String payloadTemplate = "{}";
    @Valid
    private List<NameValue> queryParams = new ArrayList<>();
    @Valid
    private List<NameValue> headers = new ArrayList<>();
    @Valid
    private List<ResponseMapping> responseMappings = new ArrayList<>();
    @Valid
    private ResponseSelection responseSelection = new ResponseSelection();
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
    @Max(10)
    private int maxAttempts = 3;
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

    @Data
    public static class NameValue {
        @NotBlank
        private String name;
        private String valueTemplate = "";
    }

    @Data
    public static class ResponseMapping {
        @NotBlank
        private String jsonPath;
        @NotBlank
        private String targetField;
        private boolean required;
        private Object defaultValue;
    }

    @Data
    public static class ResponseSelection {
        /** ROOT, FIRST, LAST, FILTER_FIRST or FILTER_LAST. */
        @NotBlank
        private String mode = "ROOT";
        private String collectionJsonPath = "$";
        private String filterJsonPath = "$.id";
        private String expectedValueTemplate = "";
    }
}
