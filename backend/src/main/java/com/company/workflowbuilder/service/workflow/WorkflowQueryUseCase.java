package com.company.workflowbuilder.service.workflow;

import com.company.workflowbuilder.dto.FieldOption;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.dto.response.WorkflowResponse;
import com.company.workflowbuilder.dto.response.WorkflowStepResponse;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.form.FormField;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.FormFieldRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.service.CurrentUserService;
import com.company.workflowbuilder.service.WorkflowAuthorizationService;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowQueryUseCase {
    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final CustomFieldDefinitionRepository fields;
    private final CurrentUserService currentUser;
    private final WorkflowAuthorizationService authorization;
    private final WorkflowViewMapper mapper;
    private final WorkflowDefinitionAnalysis analysis;
    private final ObjectMapper objectMapper;
    private final FormFieldRepository formFields;

    public WorkflowResponse get(UUID id) {
        Workflow workflow = find(id);
        authorization.requireView(workflow);
        return mapper.workflow(workflow);
    }

    public WorkflowResponse activeVersion(UUID id) {
        Workflow selected = find(id);
        Workflow active = workflows.findByFamilyIdAndStatus(selected.getFamilyId(), WorkflowStatus.PUBLISHED)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException(
                        "Workflow này hiện không có phiên bản đang chạy"));
        authorization.requireView(active);
        return mapper.workflow(active);
    }

    public List<WorkflowResponse> accessible() {
        return workflows.findAll().stream().filter(authorization::canView).map(mapper::workflow).toList();
    }

    public List<WorkflowResponse> catalog() {
        return workflows.findAll().stream().filter(authorization::canSubmit)
                .sorted(Comparator.comparing(Workflow::getName, String.CASE_INSENSITIVE_ORDER))
                .map(mapper::workflow).toList();
    }

    public List<WorkflowResponse> managed() {
        var stream = currentUser.hasRole(SystemRole.ADMIN) ? workflows.findAll().stream()
                : currentUser.hasRole(SystemRole.WORKFLOW_OWNER) ? workflows.findByOwnerId(currentUser.id()).stream()
                : workflows.findAll().stream()
                        .filter(workflow -> workflow.getEditors().stream()
                                .anyMatch(editor -> editor.getId().equals(currentUser.id())))
                ;
        return stream.filter(authorization::canView)
                .filter(workflow -> !analysis.isUnchangedDerivedDraft(workflow)).map(mapper::workflow).toList();
    }

    public List<WorkflowStepResponse> steps(UUID workflowId) {
        Workflow workflow = find(workflowId);
        authorization.requireView(workflow);
        return steps.findByWorkflowIdOrderByPositionXAsc(workflowId).stream().map(mapper::step).toList();
    }

    public List<CustomFieldResponse> fields(UUID workflowId) {
        Workflow workflow = find(workflowId);
        authorization.requireView(workflow);
        Map<UUID, Integer> stepOrder = new HashMap<>();
        var orderedSteps = steps.findByWorkflowIdOrderByPositionXAsc(workflowId);
        for (int index = 0; index < orderedSteps.size(); index++) stepOrder.put(orderedSteps.get(index).getId(), index);
        Map<String, CustomFieldDefinition> uniqueByKey = new LinkedHashMap<>();
        fields.findByStepWorkflowId(workflowId).stream()
                .filter(field -> field.getStep().getType() != com.company.workflowbuilder.entity.workflow.StepType.START)
                .sorted(Comparator.comparingInt((CustomFieldDefinition field) ->
                        stepOrder.getOrDefault(field.getStep().getId(), Integer.MAX_VALUE))
                        .thenComparingInt(CustomFieldDefinition::getDisplayOrder))
                .forEach(field -> uniqueByKey.putIfAbsent(field.getFieldKey(), field));
        List<CustomFieldResponse> result=new java.util.ArrayList<>();
        if(workflow.getFormVersion()!=null) result.addAll(formFields.findByFormVersionIdOrderByDisplayOrderAsc(workflow.getFormVersion().getId()).stream().map(this::fieldResponse).toList());
        Set<String> present=result.stream().map(CustomFieldResponse::getFieldKey).collect(java.util.stream.Collectors.toSet());
        uniqueByKey.values().stream().filter(field->present.add(field.getFieldKey())).map(this::fieldResponse).forEach(result::add);
        return result;
    }

    private CustomFieldResponse fieldResponse(CustomFieldDefinition field) {
        try {
            Map<String, Object> config = objectMapper.readValue(field.getConfigurationJson(), new TypeReference<>() {});
            List<FieldOption> options = objectMapper.convertValue(config.getOrDefault("options", List.of()),
                    new TypeReference<List<FieldOption>>() {});
            return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey())
                    .label(field.getLabel()).type(field.getType()).required(field.isRequired())
                    .placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder())
                    .options(options).allowMultiple(Boolean.TRUE.equals(config.get("allowMultiple"))).build();
        } catch (Exception ignored) {
            return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey())
                    .label(field.getLabel()).type(field.getType()).required(field.isRequired())
                    .placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder())
                    .options(List.of()).build();
        }
    }

    private CustomFieldResponse fieldResponse(FormField field) {
        try {Map<String,Object> config=objectMapper.readValue(field.getConfigurationJson(),new TypeReference<>(){});List<FieldOption> options=objectMapper.convertValue(config.getOrDefault("options",List.of()),new TypeReference<List<FieldOption>>(){});return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey()).label(field.getLabel()).type(field.getType()).required(field.isRequired()).placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder()).options(options).allowMultiple(Boolean.TRUE.equals(config.get("allowMultiple"))).build();}
        catch(Exception ignored){return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey()).label(field.getLabel()).type(field.getType()).required(field.isRequired()).placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder()).options(List.of()).build();}
    }

    private Workflow find(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", id));
    }
}
