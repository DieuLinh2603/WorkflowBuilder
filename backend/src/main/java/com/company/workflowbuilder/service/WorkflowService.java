package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.StepCreateRequest;
import com.company.workflowbuilder.dto.request.StepUpdateRequest;
import com.company.workflowbuilder.dto.request.StepLayoutUpdateRequest;
import com.company.workflowbuilder.dto.request.WorkflowCreateRequest;
import com.company.workflowbuilder.dto.request.WorkflowMetadataUpdateRequest;
import com.company.workflowbuilder.dto.request.DuplicateWorkflowRequest;
import com.company.workflowbuilder.dto.response.WorkflowResponse;
import com.company.workflowbuilder.dto.response.WorkflowStepResponse;
import com.company.workflowbuilder.dto.response.StepDeleteResponse;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.dto.response.WorkflowVersionResponse;
import com.company.workflowbuilder.dto.response.WorkflowVersionComparisonResponse;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.WorkflowAudienceRepository;
import com.company.workflowbuilder.repository.WorkflowInstanceRepository;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.service.workflow.WorkflowDefinitionAnalysis;
import com.company.workflowbuilder.service.workflow.WorkflowViewMapper;
import com.company.workflowbuilder.service.workflow.WorkflowQueryUseCase;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final UserRepository userRepository;
    private final WorkflowConnectionRepository connectionRepository;
    private final CustomFieldDefinitionRepository fieldRepository;
    private final CurrentUserService currentUser;
    private final WorkflowAuthorizationService authorization;
    private final WorkflowValidationService validationService;
    private final WorkflowAudienceRepository audienceRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowDefinitionAnalysis definitionAnalysis;
    private final WorkflowQueryUseCase queries;
    private final WorkflowMetadataService metadata;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowService(WorkflowRepository workflowRepository, WorkflowStepRepository workflowStepRepository,
            UserRepository userRepository, WorkflowConnectionRepository connectionRepository,
            CustomFieldDefinitionRepository fieldRepository, CurrentUserService currentUser,
            WorkflowAuthorizationService authorization, WorkflowValidationService validationService,
            WorkflowAudienceRepository audienceRepository, WorkflowInstanceRepository instanceRepository,
            WorkflowViewMapper viewMapper, WorkflowDefinitionAnalysis definitionAnalysis, WorkflowQueryUseCase queries,
            WorkflowMetadataService metadata) {
        this.workflowRepository = workflowRepository;
        this.workflowStepRepository = workflowStepRepository;
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.fieldRepository = fieldRepository;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.validationService = validationService;
        this.audienceRepository = audienceRepository;
        this.instanceRepository = instanceRepository;
        this.viewMapper = viewMapper;
        this.definitionAnalysis = definitionAnalysis;
        this.queries = queries;
        this.metadata = metadata;
    }

    /** Backward-compatible constructor used by focused unit tests. */
    public WorkflowService(WorkflowRepository workflowRepository, WorkflowStepRepository workflowStepRepository,
            UserRepository userRepository, WorkflowConnectionRepository connectionRepository,
            CustomFieldDefinitionRepository fieldRepository, CurrentUserService currentUser,
            WorkflowAuthorizationService authorization, WorkflowValidationService validationService,
            WorkflowAudienceRepository audienceRepository, WorkflowInstanceRepository instanceRepository,
            WorkflowViewMapper viewMapper, WorkflowDefinitionAnalysis definitionAnalysis, WorkflowQueryUseCase queries) {
        this(workflowRepository, workflowStepRepository, userRepository, connectionRepository, fieldRepository,
                currentUser, authorization, validationService, audienceRepository, instanceRepository, viewMapper,
                definitionAnalysis, queries, null);
    }

    /**
     * Create a new workflow with DRAFT status, version "1.0",
     * and auto-create a START step (label "Nhận đề xuất").
     */
    @Transactional
    public WorkflowResponse createWorkflow(WorkflowCreateRequest request) {
        if (!currentUser.hasRole(SystemRole.ADMIN) && !currentUser.hasRole(SystemRole.WORKFLOW_OWNER)) {
            throw new AccessDeniedException("Only admin or workflow owner can create workflows");
        }
        String module = request.getModule().trim().toUpperCase(Locale.ROOT);
        String type = request.getWorkflowType().trim().toUpperCase(Locale.ROOT);
        metadata.requireActiveModule(module);
        metadata.requireActiveType(type);
        String customTypeName = request.getCustomWorkflowType() == null
                ? null : request.getCustomWorkflowType().trim();
        if ("CUSTOM".equals(type) && (customTypeName == null || customTypeName.isBlank()))
            throw new IllegalArgumentException("Vui lòng nhập loại Workflow khác");
        if (!"CUSTOM".equals(type)) customTypeName = null;
        requireCurrentUserModule(module);
        requireUniqueRootName(module, request.getName());
        final UUID ownerId = request.getOwnerId() != null && currentUser.hasRole(SystemRole.ADMIN)
                ? request.getOwnerId()
                : currentUser.id();

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));
        requireUserModule(owner, module);

        Workflow workflow = Workflow.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .type(type)
                .customTypeName(customTypeName)
                .module(module)
                .owner(owner)
                .version("1.0")
                .status(WorkflowStatus.DRAFT)
                .familyId(UUID.randomUUID())
                .build();

        Workflow saved = workflowRepository.save(workflow);

        // Auto-create the mandatory START step
        WorkflowStep startStep = WorkflowStep.builder()
                .workflow(saved)
                .type(StepType.START)
                .label("Nhận đề xuất")
                .positionX(250)
                .positionY(200)
                .build();
        workflowStepRepository.save(startStep);

        log.info("Created workflow '{}' (id={}) with auto START step", saved.getName(), saved.getId());
        return viewMapper.workflow(saved);
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflowById(UUID id) {
        return queries.get(id);
    }

    @Transactional
    public WorkflowResponse updateMetadata(UUID workflowId, WorkflowMetadataUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireEdit(workflow);
        String name = request.getName().trim();
        if (workflowRepository.existsByModuleAndVersionAndNameIgnoreCaseAndFamilyIdNot(
                workflow.getModule(), "1.0", name, workflow.getFamilyId()))
            throw new com.company.workflowbuilder.exception.DuplicateResourceException(
                    "Tên workflow đã tồn tại trong module này");
        workflow.setName(name);
        workflow.setDescription(trimToNull(request.getDescription()));
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getActiveVersion(UUID workflowId) {
        return queries.activeVersion(workflowId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listAccessible() {
        return queries.accessible();
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listCatalog() {
        return queries.catalog();
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listManaged() {
        return queries.managed();
    }

    @Transactional(readOnly = true)
    public List<WorkflowStepResponse> getStepsByWorkflowId(UUID workflowId) {
        return queries.steps(workflowId);
    }

    @Transactional(readOnly = true)
    public List<CustomFieldResponse> getWorkflowFields(UUID workflowId) {
        return queries.fields(workflowId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowVersionResponse> getVersionHistory(UUID workflowId) {
        Workflow selected = findWorkflowOrThrow(workflowId);
        authorization.requireView(selected);
        List<Workflow> versions = workflowRepository.findByFamilyId(selected.getFamilyId()).stream()
                .filter(version -> !definitionAnalysis.isUnchangedDerivedDraft(version))
                .sorted(definitionAnalysis.versionComparator().reversed()).toList();
        List<Workflow> ascending = versions.stream().sorted(definitionAnalysis.versionComparator()).toList();
        Map<UUID, Workflow> previousById = new HashMap<>();
        for (int index = 0; index < ascending.size(); index++) {
            Workflow current = ascending.get(index);
            Workflow previous = current.getSourceWorkflow() != null
                    && current.getSourceWorkflow().getFamilyId().equals(current.getFamilyId())
                            ? current.getSourceWorkflow()
                            : index > 0 ? ascending.get(index - 1) : null;
            previousById.put(current.getId(), previous);
        }
        return versions.stream().map(version -> {
            List<WorkflowStep> versionSteps = workflowStepRepository
                    .findByWorkflowIdOrderByPositionXAsc(version.getId());
            return WorkflowVersionResponse.builder().id(version.getId()).familyId(version.getFamilyId())
                    .name(version.getName())
                    .version(version.getVersion()).status(version.getStatus().name())
                    .ownerId(version.getOwner().getId())
                    .ownerName(version.getOwner().getDisplayName()).createdAt(version.getCreatedAt())
                    .updatedAt(version.getUpdatedAt())
                    .stepCount(versionSteps.size())
                    .fieldCount(fieldRepository.findByStepWorkflowId(version.getId()).size())
                    .connectionCount(connectionRepository.findByWorkflowId(version.getId()).size())
                    .changes(definitionAnalysis.compare(previousById.get(version.getId()), version))
                    .canRestore(version.getStatus() != WorkflowStatus.DRAFT
                            && version.getStatus() != WorkflowStatus.PUBLISHED
                            && (currentUser.hasRole(SystemRole.ADMIN)
                                    || version.getOwner().getId().equals(currentUser.id())))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowVersionComparisonResponse compareVersions(UUID fromId, UUID toId) {
        Workflow from = findWorkflowOrThrow(fromId), to = findWorkflowOrThrow(toId);
        authorization.requireView(from);
        authorization.requireView(to);
        if (!from.getFamilyId().equals(to.getFamilyId()))
            throw new IllegalArgumentException("Hai phiên bản không thuộc cùng workflow");
        return WorkflowVersionComparisonResponse.builder().fromVersionId(from.getId()).fromVersion(from.getVersion())
                .toVersionId(to.getId()).toVersion(to.getVersion()).changes(definitionAnalysis.compare(from, to)).build();
    }

    @Transactional
    public WorkflowResponse restoreVersion(UUID workflowId) {
        Workflow source = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(source);
        if (source.getStatus() == WorkflowStatus.DRAFT)
            throw new IllegalArgumentException("Phiên bản DRAFT không thể được kích hoạt trực tiếp");
        List<Workflow> family = workflowRepository.findByFamilyId(source.getFamilyId());
        family.stream().filter(version -> version.getStatus() == WorkflowStatus.PUBLISHED)
                .filter(version -> !version.getId().equals(source.getId()))
                .forEach(version -> {
                    version.setStatus(WorkflowStatus.SUSPENDED);
                    workflowRepository.save(version);
                });
        source.setStatus(WorkflowStatus.PUBLISHED);
        return viewMapper.workflow(workflowRepository.save(source));
    }

    /**
     * Add a new step to a workflow. Validates:
     * - Cannot add more than 1 START step per workflow.
     */
    @Transactional
    public WorkflowStepResponse addStep(UUID workflowId, StepCreateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireEdit(workflow);

        // Validate: only 1 START allowed
        if (request.getType() == StepType.START) {
            boolean startExists = workflowStepRepository.existsByWorkflowIdAndType(workflowId, StepType.START);
            if (startExists) {
                throw new IllegalArgumentException(
                        "Workflow đã có Start Step. Mỗi workflow chỉ được phép có đúng 1 Start.");
            }
        }

        // Default label based on step type if not provided
        String label = request.getLabel();
        if (label == null || label.isBlank()) {
            label = getDefaultLabel(request.getType());
        }

        WorkflowStep step = WorkflowStep.builder()
                .workflow(workflow)
                .type(request.getType())
                .label(label)
                .positionX(request.getPositionX() != null ? request.getPositionX() : 250)
                .positionY(request.getPositionY() != null ? request.getPositionY() : 200)
                .build();

        WorkflowStep saved = workflowStepRepository.save(step);
        log.info("Added step '{}' (type={}) to workflow {}", saved.getLabel(), saved.getType(), workflowId);
        return viewMapper.step(saved);
    }

    @Transactional
    public WorkflowStepResponse updateStep(UUID workflowId, UUID stepId, StepUpdateRequest request) {
        WorkflowStep step = workflowStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", stepId));
        if (!step.getWorkflow().getId().equals(workflowId))
            throw new IllegalArgumentException("Step does not belong to the given workflow");
        authorization.requireEdit(step.getWorkflow());
        step.setLabel(request.getLabel().trim());
        return viewMapper.step(workflowStepRepository.save(step));
    }

    @Transactional
    public List<WorkflowStepResponse> updateLayout(UUID workflowId, StepLayoutUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireEdit(workflow);
        if (workflow.getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalArgumentException("Chỉ được thay đổi layout của workflow DRAFT");
        Set<UUID> uniqueIds = new HashSet<>();
        List<WorkflowStep> changed = new ArrayList<>();
        for (StepLayoutUpdateRequest.Position position : request.getPositions()) {
            if (!uniqueIds.add(position.getStepId()))
                throw new IllegalArgumentException("Layout chứa step bị trùng: " + position.getStepId());
            WorkflowStep step = workflowStepRepository.findById(position.getStepId())
                    .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", position.getStepId()));
            if (!step.getWorkflow().getId().equals(workflowId))
                throw new IllegalArgumentException("Step không thuộc workflow hiện tại");
            step.setPositionX(position.getPositionX());
            step.setPositionY(position.getPositionY());
            changed.add(step);
        }
        return workflowStepRepository.saveAll(changed).stream().map(viewMapper::step).toList();
    }

    @Transactional
    public StepDeleteResponse deleteStep(UUID workflowId, UUID stepId,
            UUID incomingConnectionId, UUID outgoingConnectionId) {
        WorkflowStep step = workflowStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", stepId));
        authorization.requireEdit(step.getWorkflow());

        if (!step.getWorkflow().getId().equals(workflowId)) {
            throw new IllegalArgumentException("Step does not belong to the given workflow");
        }

        // Prevent deleting the only START step
        if (step.getType() == StepType.START) {
            throw new IllegalArgumentException("Không thể xóa Start Step. Mỗi workflow phải có đúng 1 Start.");
        }
        if (step.getWorkflow().getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalArgumentException("Chỉ được xóa step của workflow đang ở trạng thái DRAFT");
        if ((incomingConnectionId == null) != (outgoingConnectionId == null))
            throw new IllegalArgumentException("Phải chọn đồng thời connection đi vào và connection đi ra");

        List<WorkflowConnection> workflowConnections = connectionRepository.findByWorkflowId(workflowId);
        List<WorkflowConnection> affected = workflowConnections.stream()
                .filter(connection -> connection.getFromStep().getId().equals(stepId)
                        || connection.getToStep().getId().equals(stepId))
                .toList();
        List<UUID> deletedConnectionIds = affected.stream().map(WorkflowConnection::getId).toList();
        WorkflowConnection replacement = null;

        if (incomingConnectionId != null) {
            WorkflowConnection incoming = workflowConnections.stream()
                    .filter(connection -> connection.getId().equals(incomingConnectionId)
                            && connection.getToStep().getId().equals(stepId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Connection đi vào không thuộc step cần xóa"));
            WorkflowConnection outgoing = workflowConnections.stream()
                    .filter(connection -> connection.getId().equals(outgoingConnectionId)
                            && connection.getFromStep().getId().equals(stepId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Connection đi ra không thuộc step cần xóa"));
            WorkflowStep replacementFrom = incoming.getFromStep();
            WorkflowStep replacementTo = outgoing.getToStep();
            if (replacementFrom.getId().equals(replacementTo.getId()))
                throw new IllegalArgumentException("Nối lại sẽ tạo connection quay về chính node nguồn");

            replacement = workflowConnections.stream()
                    .filter(connection -> !deletedConnectionIds.contains(connection.getId()))
                    .filter(connection -> connection.getFromStep().getId().equals(replacementFrom.getId())
                            && connection.getToStep().getId().equals(replacementTo.getId())
                            && connection.getType() == incoming.getType())
                    .findFirst().orElse(null);

            if (replacement == null) {
                WorkflowConnection newConnection = WorkflowConnection.builder()
                        .workflow(step.getWorkflow()).fromStep(replacementFrom).toStep(replacementTo)
                        .type(incoming.getType()).logicalOperator(incoming.getLogicalOperator())
                        .priority(incoming.getPriority()).build();
                for (int index = 0; index < incoming.getClauses().size(); index++) {
                    WorkflowConditionClause clause = incoming.getClauses().get(index);
                    newConnection.getClauses().add(WorkflowConditionClause.builder()
                            .connection(newConnection).fieldKey(clause.getFieldKey())
                            .operator(clause.getOperator()).expectedValue(clause.getExpectedValue())
                            .expressionJson(clause.getExpressionJson())
                            .displayOrder(index).build());
                }
                replacement = newConnection;
            }
        }

        connectionRepository.deleteAll(affected);
        connectionRepository.flush();
        if (replacement != null && replacement.getId() == null)
            replacement = connectionRepository.save(replacement);
        workflowStepRepository.delete(step);
        log.info("Deleted step {} from workflow {}", stepId, workflowId);
        return StepDeleteResponse.builder().deletedStepId(stepId)
                .deletedConnectionIds(deletedConnectionIds)
                .replacementConnection(replacement == null ? null : viewMapper.connection(replacement))
                .build();
    }

    @Transactional
    public WorkflowResponse duplicate(UUID workflowId, DuplicateWorkflowRequest request) {
        Workflow root = findWorkflowOrThrow(workflowId);
        Workflow source = request.getSourceVersionId() == null ? root
                : findWorkflowOrThrow(request.getSourceVersionId());
        if (!source.getFamilyId().equals(root.getFamilyId()))
            throw new IllegalArgumentException("Source version does not belong to this workflow family");
        authorization.requireView(source);
        if (!currentUser.hasRole(SystemRole.ADMIN) && !currentUser.hasRole(SystemRole.WORKFLOW_OWNER))
            throw new AccessDeniedException("Only admin or workflow owner can duplicate workflows");
        final UUID ownerId = request.getOwnerId() != null && currentUser.hasRole(SystemRole.ADMIN)
                ? request.getOwnerId()
                : currentUser.id();
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));
        String targetModule = request.getModule() == null || request.getModule().isBlank()
                ? source.getModule() : request.getModule().trim().toUpperCase(Locale.ROOT);
        metadata.requireActiveModule(targetModule);
        requireCurrentUserModule(targetModule);
        requireUserModule(owner, targetModule);
        if (!request.isWorkingCopy()) requireUniqueRootName(targetModule, request.getName());
        Workflow copy = workflowRepository
                .save(Workflow.builder().name(request.getName().trim()).description(source.getDescription())
                        .type(source.getType()).customTypeName(source.getCustomTypeName())
                        .module(targetModule).owner(owner).familyId(UUID.randomUUID())
                        .formVersion(source.getFormVersion())
                        .sourceWorkflow(source).version(request.isWorkingCopy() ? "WORKING" : "1.0")
                        .status(WorkflowStatus.DRAFT).build());
        Map<UUID, WorkflowStep> stepMap = new HashMap<>();
        for (WorkflowStep old : workflowStepRepository.findByWorkflowIdOrderByPositionXAsc(source.getId())) {
            WorkflowStep fresh = workflowStepRepository.save(WorkflowStep.builder().workflow(copy).type(old.getType())
                    .label(old.getLabel()).positionX(old.getPositionX()).positionY(old.getPositionY())
                    .configJson(old.getConfigJson()).build());
            stepMap.put(old.getId(), fresh);
            for (CustomFieldDefinition field : fieldRepository.findByStepIdOrderByDisplayOrderAsc(old.getId())) {
                fieldRepository.save(CustomFieldDefinition.builder().step(fresh).fieldKey(field.getFieldKey())
                        .label(field.getLabel())
                        .type(field.getType()).required(field.isRequired()).placeholder(field.getPlaceholder())
                        .configurationJson(field.getConfigurationJson())
                        .displayOrder(field.getDisplayOrder()).build());
            }
        }
        for (WorkflowConnection old : connectionRepository.findByWorkflowId(source.getId())) {
            WorkflowConnection fresh = WorkflowConnection.builder().workflow(copy)
                    .fromStep(stepMap.get(old.getFromStep().getId()))
                    .toStep(stepMap.get(old.getToStep().getId())).type(old.getType())
                    .logicalOperator(old.getLogicalOperator()).priority(old.getPriority()).build();
            for (WorkflowConditionClause clause : old.getClauses())
                fresh.getClauses().add(WorkflowConditionClause.builder()
                        .connection(fresh).fieldKey(clause.getFieldKey()).operator(clause.getOperator())
                        .expectedValue(clause.getExpectedValue()).expressionJson(clause.getExpressionJson())
                        .displayOrder(clause.getDisplayOrder()).build());
            connectionRepository.save(fresh);
        }
        return viewMapper.workflow(copy);
    }

    @Transactional
    public WorkflowResponse createNextDraft(UUID workflowId) {
        Workflow source = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(source);
        if (source.getStatus() != WorkflowStatus.PUBLISHED && source.getStatus() != WorkflowStatus.SUSPENDED)
            throw new IllegalStateException("A new version can only be created from published or suspended workflow");
        Optional<Workflow> existingDraft = workflowRepository.findByFamilyId(source.getFamilyId()).stream()
                .filter(version -> version.getStatus() == WorkflowStatus.DRAFT).findFirst();
        if (existingDraft.isPresent())
            return viewMapper.workflow(existingDraft.get());
        List<Workflow> family = workflowRepository.findByFamilyId(source.getFamilyId());
        Workflow latest = family.stream().max(definitionAnalysis.versionComparator()).orElse(source);
        DuplicateWorkflowRequest request = new DuplicateWorkflowRequest();
        request.setName(source.getName());
        request.setOwnerId(source.getOwner().getId());
        request.setSourceVersionId(source.getId());
        request.setModule(source.getModule());
        request.setWorkingCopy(true);
        WorkflowResponse duplicated = duplicate(source.getId(), request);
        Workflow draft = findWorkflowOrThrow(duplicated.getId());
        draft.setFamilyId(source.getFamilyId());
        draft.setVersion(definitionAnalysis.nextVersion(latest.getVersion()));
        draft.setSourceWorkflow(source);
        draft.setStatus(WorkflowStatus.DRAFT);
        draft.getEditors().addAll(source.getEditors());
        workflowRepository.save(draft);
        for (WorkflowAudience audience : audienceRepository.findByWorkflowId(source.getId()))
            audienceRepository.save(WorkflowAudience.builder().workflow(draft).subjectType(audience.getSubjectType())
                    .subjectValue(audience.getSubjectValue()).build());
        return viewMapper.workflow(draft);
    }

    /** Delete an edit working copy when no business definition was changed. */
    @Transactional
    public boolean discardUnchangedDraft(UUID workflowId) {
        Workflow draft = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(draft);
        if (!definitionAnalysis.isUnchangedDerivedDraft(draft) || instanceRepository.existsByWorkflowId(workflowId))
            return false;
        workflowRepository.delete(draft);
        return true;
    }

    @Transactional
    public void deleteWorkflow(UUID workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        if (workflow.getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalStateException("Chỉ được xóa workflow đang ở trạng thái DRAFT");
        if (instanceRepository.existsByWorkflowId(workflowId))
            throw new IllegalStateException("Workflow đã được sử dụng và không thể xóa vĩnh viễn");
        workflowRepository.delete(workflow);
    }

    @Transactional
    public WorkflowResponse publish(UUID workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        if (workflow.getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalStateException("Only a draft can be published");
        metadata.requireActiveModule(workflow.getModule());
        List<String> errors = validationService.validate(workflowId);
        if (!errors.isEmpty())
            throw new IllegalArgumentException(String.join("; ", errors));
        workflowRepository.findByFamilyIdAndStatus(workflow.getFamilyId(), WorkflowStatus.PUBLISHED).stream()
                .filter(old -> !old.getId().equals(workflow.getId())).forEach(old -> {
                    old.setStatus(WorkflowStatus.SUSPENDED);
                    workflowRepository.save(old);
                });
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    @Transactional
    public WorkflowResponse changeStatus(UUID workflowId, WorkflowStatus status) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        if (status != WorkflowStatus.SUSPENDED && status != WorkflowStatus.ARCHIVED)
            throw new IllegalArgumentException("Only SUSPENDED or ARCHIVED is supported");
        workflow.setStatus(status);
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    @Transactional(readOnly = true)
    public List<com.company.workflowbuilder.dto.response.WorkflowEditorResponse> editorCandidates(UUID workflowId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        requireDraftForEditorManagement(workflow);
        return userRepository.findByActiveTrueAndManager_Id(workflow.getOwner().getId()).stream()
                .filter(user -> user.getSystemRoles().contains(SystemRole.EDITOR)
                        || user.getSystemRoles().contains(SystemRole.VIEWER))
                .filter(user -> workflow.getModule() == null || workflow.getModule().isBlank()
                        || user.getSystemRoles().contains(SystemRole.ADMIN)
                        || user.getModuleCodes().contains(workflow.getModule()))
                .filter(user -> !user.getId().equals(workflow.getOwner().getId()))
                .filter(user -> workflow.getEditors().stream().noneMatch(editor -> editor.getId().equals(user.getId())))
                .map(user -> com.company.workflowbuilder.dto.response.WorkflowEditorResponse.builder()
                        .id(user.getId()).displayName(user.getDisplayName()).email(user.getEmail())
                        .jobTitle(user.getJobTitle()).build()).toList();
    }

    @Transactional
    public WorkflowResponse addEditor(UUID workflowId, UUID userId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        requireDraftForEditorManagement(workflow);
        User editor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!editor.getSystemRoles().contains(SystemRole.EDITOR)
                && !editor.getSystemRoles().contains(SystemRole.VIEWER))
            throw new IllegalArgumentException("Only an EDITOR or VIEWER account can be assigned as workflow editor");
        if (!editor.isActive())
            throw new IllegalArgumentException("Inactive user cannot be assigned as workflow editor");
        requireUserModule(editor, workflow.getModule());
        if (workflow.getOwner().getId().equals(editor.getId()))
            throw new IllegalArgumentException("Workflow owner does not need editor access");
        if (editor.getManager() == null || !workflow.getOwner().getId().equals(editor.getManager().getId()))
            throw new IllegalArgumentException("Chỉ được chọn Editor thuộc sự quản lý trực tiếp của Workflow Owner");
        workflow.getEditors().add(editor);
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    @Transactional
    public WorkflowResponse removeEditor(UUID workflowId, UUID userId) {
        Workflow workflow = findWorkflowOrThrow(workflowId);
        authorization.requireOwnerOrAdmin(workflow);
        requireDraftForEditorManagement(workflow);
        boolean removed = workflow.getEditors().removeIf(editor -> editor.getId().equals(userId));
        if (!removed)
            throw new ResourceNotFoundException("Workflow editor", "userId", userId);
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    private void requireDraftForEditorManagement(Workflow workflow) {
        if (workflow.getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalStateException("Editors can only be changed on a workflow draft");
    }

    @Transactional
    public WorkflowResponse transferOwner(UUID workflowId, UUID userId) {
        currentUser.requireAdmin();
        Workflow workflow = findWorkflowOrThrow(workflowId);
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!owner.getSystemRoles().contains(SystemRole.WORKFLOW_OWNER))
            throw new IllegalArgumentException("New owner must have WORKFLOW_OWNER role");
        requireUserModule(owner, workflow.getModule());
        workflow.setOwner(owner);
        return viewMapper.workflow(workflowRepository.save(workflow));
    }

    // ─── Helpers ───────────────────────────────────────────

    private Workflow findWorkflowOrThrow(UUID id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", id));
    }

    private void requireCurrentUserModule(String module) {
        if (!currentUser.hasRole(SystemRole.ADMIN) && !currentUser.user().getModuleCodes().contains(module))
            throw new AccessDeniedException("Bạn không thuộc module " + module);
    }

    private void requireUserModule(User user, String module) {
        if (module == null || module.isBlank()) return;
        if (!user.getSystemRoles().contains(SystemRole.ADMIN) && !user.getModuleCodes().contains(module))
            throw new IllegalArgumentException("Người dùng không thuộc module " + module);
    }

    private void requireUniqueRootName(String module, String name) {
        if (workflowRepository.existsByModuleAndVersionAndNameIgnoreCase(module, "1.0", name.trim()))
            throw new com.company.workflowbuilder.exception.DuplicateResourceException(
                    "Tên workflow đã tồn tại trong module này");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String getDefaultLabel(StepType type) {
        return switch (type) {
            case START -> "Nhận đề xuất";
            case APPROVAL -> "Phê duyệt";
            case REVIEW -> "Review";
            case ASSIGNMENT -> "Giao việc";
            case NOTIFICATION -> "Thông báo";
            case SYSTEM_ACTION -> "Xử lý tự động";
            case END -> "Kết thúc";
        };
    }
}
