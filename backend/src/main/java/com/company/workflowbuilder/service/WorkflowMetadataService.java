package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.BusinessModuleRequest;
import com.company.workflowbuilder.dto.request.WorkflowTypeDefinitionRequest;
import com.company.workflowbuilder.dto.response.BusinessModuleResponse;
import com.company.workflowbuilder.dto.response.WorkflowTypeDefinitionResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.metadata.BusinessModule;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.entity.metadata.WorkflowTypeDefinition;
import com.company.workflowbuilder.exception.DuplicateResourceException;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.BusinessModuleRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowTypeDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkflowMetadataService {
    private final BusinessModuleRepository modules;
    private final WorkflowTypeDefinitionRepository types;
    private final WorkflowRepository workflows;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<BusinessModuleResponse> activeModulesForCurrentUser() {
        List<BusinessModule> active = modules.findByActiveTrueOrderBySortOrderAscNameAsc();
        if (currentUser.hasRole(SystemRole.ADMIN)) return active.stream().map(this::moduleResponse).toList();
        Set<String> allowed = currentUser.user().getModuleCodes();
        return active.stream().filter(item -> allowed.contains(item.getCode())).map(this::moduleResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessModuleResponse> allModules() {
        currentUser.requireAdmin();
        return modules.findAllByOrderBySortOrderAscNameAsc().stream().map(this::moduleResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTypeDefinitionResponse> activeTypes() {
        return types.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(this::typeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTypeDefinitionResponse> allTypes() {
        currentUser.requireAdmin();
        return types.findAllByOrderBySortOrderAscNameAsc().stream().map(this::typeResponse).toList();
    }

    @Transactional
    public BusinessModuleResponse createModule(BusinessModuleRequest request) {
        currentUser.requireAdmin();
        String code = normalizeCode(request.getCode());
        if (modules.existsById(code)) throw new DuplicateResourceException("Module code '" + code + "' already exists");
        if (modules.existsByNameIgnoreCase(request.getName().trim())) throw new DuplicateResourceException("Module name already exists");
        BusinessModule saved = modules.save(BusinessModule.builder().code(code).name(request.getName().trim())
                .description(trimToNull(request.getDescription())).sortOrder(request.getSortOrder()).active(true).build());
        return moduleResponse(saved);
    }

    @Transactional
    public BusinessModuleResponse updateModule(String code, BusinessModuleRequest request) {
        currentUser.requireAdmin();
        String normalized = normalizeCode(code);
        requireMatchingCode(normalized, request.getCode());
        BusinessModule item = module(normalized);
        if (modules.existsByNameIgnoreCaseAndCodeNot(request.getName().trim(), normalized))
            throw new DuplicateResourceException("Module name already exists");
        item.setName(request.getName().trim());
        item.setDescription(trimToNull(request.getDescription()));
        item.setSortOrder(request.getSortOrder());
        return moduleResponse(modules.save(item));
    }

    @Transactional
    public BusinessModuleResponse setModuleActive(String code, boolean active) {
        currentUser.requireAdmin();
        BusinessModule item = module(normalizeCode(code));
        if (!active && workflows.existsByModuleAndStatus(item.getCode(), WorkflowStatus.PUBLISHED))
            throw new IllegalStateException("Không thể ngừng module đang có workflow Published");
        item.setActive(active);
        return moduleResponse(modules.save(item));
    }

    @Transactional
    public WorkflowTypeDefinitionResponse createType(WorkflowTypeDefinitionRequest request) {
        currentUser.requireAdmin();
        String code = normalizeCode(request.getCode());
        if (types.existsById(code)) throw new DuplicateResourceException("Workflow type code '" + code + "' already exists");
        if (types.existsByNameIgnoreCase(request.getName().trim())) throw new DuplicateResourceException("Workflow type name already exists");
        WorkflowTypeDefinition saved = types.save(WorkflowTypeDefinition.builder().code(code).name(request.getName().trim())
                .description(trimToNull(request.getDescription())).guidanceJson(guidance(request))
                .sortOrder(request.getSortOrder()).active(true).build());
        return typeResponse(saved);
    }

    @Transactional
    public WorkflowTypeDefinitionResponse updateType(String code, WorkflowTypeDefinitionRequest request) {
        currentUser.requireAdmin();
        String normalized = normalizeCode(code);
        requireMatchingCode(normalized, request.getCode());
        WorkflowTypeDefinition item = type(normalized);
        if (types.existsByNameIgnoreCaseAndCodeNot(request.getName().trim(), normalized))
            throw new DuplicateResourceException("Workflow type name already exists");
        item.setName(request.getName().trim());
        item.setDescription(trimToNull(request.getDescription()));
        item.setGuidanceJson(guidance(request));
        item.setSortOrder(request.getSortOrder());
        return typeResponse(types.save(item));
    }

    @Transactional
    public WorkflowTypeDefinitionResponse setTypeActive(String code, boolean active) {
        currentUser.requireAdmin();
        WorkflowTypeDefinition item = type(normalizeCode(code));
        item.setActive(active);
        return typeResponse(types.save(item));
    }

    @Transactional(readOnly = true)
    public BusinessModule requireActiveModule(String code) {
        BusinessModule item = module(normalizeCode(code));
        if (!item.isActive()) throw new IllegalArgumentException("Module đã ngừng hoạt động");
        return item;
    }

    @Transactional(readOnly = true)
    public WorkflowTypeDefinition requireActiveType(String code) {
        WorkflowTypeDefinition item = type(normalizeCode(code));
        if (!item.isActive()) throw new IllegalArgumentException("Loại workflow đã ngừng hoạt động");
        return item;
    }

    @Transactional(readOnly = true)
    public BusinessModuleResponse moduleByCode(String code) {
        return moduleResponse(module(normalizeCode(code)));
    }

    @Transactional(readOnly = true)
    public WorkflowTypeDefinitionResponse typeByCode(String code) {
        return typeResponse(type(normalizeCode(code)));
    }

    private BusinessModule module(String code) {
        return modules.findById(code).orElseThrow(() -> new ResourceNotFoundException("BusinessModule", "code", code));
    }

    private WorkflowTypeDefinition type(String code) {
        return types.findById(code).orElseThrow(() -> new ResourceNotFoundException("WorkflowType", "code", code));
    }

    private BusinessModuleResponse moduleResponse(BusinessModule item) {
        return BusinessModuleResponse.builder().code(item.getCode()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).sortOrder(item.getSortOrder()).build();
    }

    private WorkflowTypeDefinitionResponse typeResponse(WorkflowTypeDefinition item) {
        Map<String, Object> value = parseGuidance(item.getGuidanceJson());
        return WorkflowTypeDefinitionResponse.builder().code(item.getCode()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).sortOrder(item.getSortOrder())
                .recommendedStepTypes(strings(value.get("recommendedStepTypes")))
                .checklist(strings(value.get("checklist"))).build();
    }

    private String guidance(WorkflowTypeDefinitionRequest request) {
        List<String> steps = request.getRecommendedStepTypes() == null ? List.of() : request.getRecommendedStepTypes();
        for (String step : steps) {
            try { StepType.valueOf(step); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("Unsupported recommended step type: " + step); }
        }
        try {
            return mapper.writeValueAsString(Map.of("recommendedStepTypes", steps,
                    "checklist", request.getChecklist() == null ? List.of() : request.getChecklist()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid workflow type guidance");
        }
    }

    private Map<String, Object> parseGuidance(String json) {
        try { return mapper.readValue(json == null ? "{}" : json, new TypeReference<>() {}); }
        catch (Exception exception) { return Map.of(); }
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private String normalizeCode(String code) {
        if (code == null || !code.trim().toUpperCase(Locale.ROOT).matches("[A-Z][A-Z0-9_]{1,63}"))
            throw new IllegalArgumentException("Mã chỉ được dùng chữ in hoa, số và dấu gạch dưới");
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void requireMatchingCode(String pathCode, String bodyCode) {
        if (!pathCode.equals(normalizeCode(bodyCode))) throw new IllegalArgumentException("Không thể thay đổi mã danh mục");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
