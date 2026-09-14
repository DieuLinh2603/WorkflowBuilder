package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.service.runtime.WorkflowFieldValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowFieldValidationServiceTest {
    private final CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private WorkflowFieldValidationService service;
    private UUID stepId;

    @BeforeEach
    void setUp() {
        service = new WorkflowFieldValidationService(fields, users, new ObjectMapper());
        stepId = UUID.randomUUID();
    }

    @Test
    void validatesStaticChoiceAndDateTime() {
        CustomFieldDefinition choice = field("status", FieldType.SELECT,
                "{\"options\":[{\"label\":\"Mới\",\"value\":\"NEW\"}]}");
        CustomFieldDefinition dateTime = field("meetingAt", FieldType.DATETIME, "{}");
        when(fields.findByStepIdOrderByDisplayOrderAsc(stepId)).thenReturn(List.of(choice, dateTime));

        assertDoesNotThrow(() -> service.validateStep(stepId,
                Map.of("status", "NEW", "meetingAt", "2026-09-10T09:30")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateStep(stepId, Map.of("status", "UNKNOWN", "meetingAt", "not-a-date")));
    }

    @Test
    void validatesSingleAndMultipleActiveUsers() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        User active = User.builder().active(true).build();
        when(users.findById(first)).thenReturn(Optional.of(active));
        when(users.findById(second)).thenReturn(Optional.of(active));
        CustomFieldDefinition picker = field("reviewers", FieldType.USER_PICKER, "{\"allowMultiple\":true}");
        when(fields.findByStepIdOrderByDisplayOrderAsc(stepId)).thenReturn(List.of(picker));

        assertDoesNotThrow(() -> service.validateStep(stepId,
                Map.of("reviewers", List.of(first.toString(), second.toString()))));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateStep(stepId, Map.of("reviewers", first.toString())));
    }

    private CustomFieldDefinition field(String key, FieldType type, String config) {
        return CustomFieldDefinition.builder().fieldKey(key).label(key).type(type)
                .required(true).configurationJson(config).build();
    }
}
