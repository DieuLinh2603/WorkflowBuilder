package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.entity.workflow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates a workflow's structure before publishing.
 * Returns a list of human-readable error strings (empty = valid).
 */
@Service
@RequiredArgsConstructor
public class WorkflowValidationService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowConnectionRepository connectionRepository;
    private final CustomFieldDefinitionRepository fieldRepository;
    private final WorkflowAuthorizationService authorization;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<String> validate(UUID workflowId) {
        var workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", workflowId));
        authorization.requireEdit(workflow);

        List<WorkflowStep> steps = workflowStepRepository.findByWorkflowIdOrderByPositionXAsc(workflowId);
        List<String> errors = new ArrayList<>();

        // Rule 1: Exactly 1 START step
        long startCount = steps.stream().filter(s -> s.getType() == StepType.START).count();
        if (startCount == 0) {
            errors.add("Workflow phải có đúng 1 bước Start.");
        } else if (startCount > 1) {
            errors.add("Workflow chỉ được phép có 1 bước Start, hiện đang có " + startCount + ".");
        }

        // Rule 2: At least 1 END step
        long endCount = steps.stream().filter(s -> s.getType() == StepType.END).count();
        if (endCount == 0) {
            errors.add("Workflow phải có ít nhất 1 bước End.");
        }

        // Rule 3: Must have at least 1 business step (besides START/END)
        long businessSteps = steps.stream()
                .filter(s -> s.getType() != StepType.START && s.getType() != StepType.END)
                .count();
        if (businessSteps == 0) {
            errors.add("Workflow cần ít nhất 1 bước nghiệp vụ (Approval, Review, Assignment...).");
        }

        List<WorkflowConnection> connections = connectionRepository.findByWorkflowId(workflowId);
        Set<UUID> incoming = new HashSet<>();
        Set<UUID> outgoing = new HashSet<>();
        Map<UUID, List<WorkflowConnection>> bySource = new HashMap<>();
        for (WorkflowConnection connection : connections) {
            incoming.add(connection.getToStep().getId());
            outgoing.add(connection.getFromStep().getId());
            bySource.computeIfAbsent(connection.getFromStep().getId(), ignored -> new ArrayList<>()).add(connection);
        }
        for (WorkflowStep step : steps) {
            if (step.getType() != StepType.START && !incoming.contains(step.getId()))
                errors.add("Step '" + step.getLabel() + "' không có connection đi vào.");
            if (step.getType() != StepType.END && !outgoing.contains(step.getId()))
                errors.add("Step '" + step.getLabel() + "' không có connection đi ra.");
        }
        Set<String> fieldKeys = new HashSet<>();
        fieldRepository.findByStepWorkflowId(workflowId).forEach(field -> {
            if (!fieldKeys.add(field.getFieldKey()))
                errors.add("Field key bị trùng: " + field.getFieldKey());
        });
        for (var entry : bySource.entrySet()) {
            List<WorkflowConnection> branches = entry.getValue();
            List<WorkflowConnection> ifs = branches.stream().filter(c -> c.getType() == ConnectionType.IF).toList();
            List<WorkflowConnection> elses = branches.stream().filter(c -> c.getType() == ConnectionType.ELSE).toList();
            if (elses.size() > 1)
                errors.add("Mỗi step chỉ được có một nhánh ELSE.");
            if (!ifs.isEmpty() && elses.isEmpty())
                errors.add("Step có nhánh IF bắt buộc phải có một nhánh ELSE.");
            if (!ifs.isEmpty() && !elses.isEmpty() && ifs.stream()
                    .anyMatch(branch -> branch.getToStep().getId().equals(elses.get(0).getToStep().getId())))
                errors.add("IF và ELSE phải nối tới hai node khác nhau.");
            if (!ifs.isEmpty() && branches.stream().anyMatch(c -> c.getType() == ConnectionType.DEFAULT))
                errors.add("Step có IF không được đồng thời có DEFAULT; hãy dùng ELSE.");
            for (WorkflowConnection condition : ifs) {
                if (condition.getClauses().isEmpty())
                    errors.add("Connection IF phải có condition.");
                condition.getClauses().forEach(clause -> {
                    if ((clause.getExpressionJson() == null || clause.getExpressionJson().isBlank())
                            && !fieldKeys.contains(clause.getFieldKey()))
                        errors.add("Condition tham chiếu field không tồn tại: " + clause.getFieldKey());
                });
            }
        }
        for (WorkflowStep step : steps)
            validateStepConfig(step, bySource.getOrDefault(step.getId(), List.of()), errors);
        if (hasCycle(steps, connections))
            errors.add(
                    "Workflow không được chứa vòng lặp tự động. Chỉ nhánh Review Không đạt hoặc Reject của Approval thủ công được phép quay về bước trước.");

        return errors;
    }

    private void validateStepConfig(WorkflowStep step, List<WorkflowConnection> outgoing, List<String> errors) {
        if (step.getType() != StepType.APPROVAL && step.getType() != StepType.REVIEW
                && step.getType() != StepType.ASSIGNMENT
                && step.getType() != StepType.NOTIFICATION && step.getType() != StepType.SYSTEM_ACTION
                && step.getType() != StepType.END)
            return;
        Map<String, Object> config;
        try {
            config = step.getConfigJson() == null ? Map.of() : objectMapper.readValue(step.getConfigJson(), Map.class);
        } catch (Exception e) {
            errors.add("Config JSON không hợp lệ tại step '" + step.getLabel() + "'.");
            return;
        }
        if (step.getType() == StepType.NOTIFICATION) {
            if (Objects.toString(config.get("titleTemplate"), "").isBlank()
                    || Objects.toString(config.get("bodyTemplate"), "").isBlank())
                errors.add("Notification Step '" + step.getLabel() + "' thiếu template.");
            return;
        }
        if (step.getType() == StepType.SYSTEM_ACTION) {
            boolean directSuccess = outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.SYSTEM_SUCCESS);
            boolean conditionalSuccess = outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.IF);
            boolean fallback = outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.ELSE);
            if (!directSuccess && !(conditionalSuccess && fallback))
                errors.add("System Action Step '" + step.getLabel()
                        + "' phải có nhánh Thành công hoặc đầy đủ nhánh IF/ELSE.");
            if (directSuccess && (conditionalSuccess || fallback))
                errors.add("System Action Step '" + step.getLabel()
                        + "' không được dùng đồng thời nhánh Thành công và IF/ELSE.");
        }
        if (step.getType() == StepType.SYSTEM_ACTION
                && outgoing.stream().noneMatch(connection -> connection.getType() == ConnectionType.SYSTEM_FAIL))
            errors.add("System Action Step '" + step.getLabel() + "' phải có nhánh Thất bại.");
        if (step.getType() == StepType.SYSTEM_ACTION) {
            if (Objects.toString(config.get("actionType"), "").isBlank())
                errors.add("System Action Step '" + step.getLabel() + "' chưa được cấu hình.");
            return;
        }
        if (step.getType() == StepType.END) {
            if (Objects.toString(config.get("outcome"), "").isBlank()
                    || Objects.toString(config.get("title"), "").isBlank())
                errors.add("End Step '" + step.getLabel() + "' chưa được cấu hình kết quả.");
            return;
        }
        if (step.getType() == StepType.ASSIGNMENT
                && outgoing.stream().noneMatch(connection -> connection.getType() == ConnectionType.ASSIGNMENT_DONE))
            errors.add("Assignment Step '" + step.getLabel() + "' phải có nhánh Hoàn thành.");
        if (step.getType() == StepType.ASSIGNMENT
                && outgoing.stream().noneMatch(connection -> connection.getType() == ConnectionType.ASSIGNMENT_FAIL))
            errors.add("Assignment Step '" + step.getLabel() + "' phải có nhánh Thất bại.");
        if (step.getType() == StepType.ASSIGNMENT) {
            String target = Objects.toString(config.get("assignmentTargetType"), "");
            if ("USER".equals(target) && (!(config.get("actorUserIds") instanceof Collection<?> c) || c.isEmpty()))
                errors.add("Assignment Step '" + step.getLabel() + "' chưa chọn user.");
            if ("GROUP".equals(target)
                    && (!(config.get("assignmentGroupIds") instanceof Collection<?> c) || c.isEmpty()))
                errors.add("Assignment Step '" + step.getLabel() + "' chưa chọn group.");
            if (target.isBlank())
                errors.add("Assignment Step '" + step.getLabel() + "' chưa được cấu hình.");
            return;
        }
        String mode = Objects.toString(config.get("mode"), "");
        if ("MANUAL".equals(mode)) {
            String approverMode = Objects.toString(config.get("approverMode"), "FIXED_USER");
            if ("FIXED_USER".equals(approverMode)
                    && (!(config.get("actorUserIds") instanceof Collection<?> c) || c.isEmpty()))
                errors.add("Approval '" + step.getLabel() + "' thiếu approver cố định.");
            if ("ROLE_BASED".equals(approverMode) && Objects.toString(config.get("actorRole"), "").isBlank())
                errors.add("Approval '" + step.getLabel() + "' chưa chọn role approver.");
            if ("DYNAMIC".equals(approverMode) && Objects.toString(config.get("dynamicActorSource"), "").isBlank())
                errors.add("Approval '" + step.getLabel() + "' chưa chọn nguồn approver động.");
        }
        if (step.getType() == StepType.REVIEW) {
            boolean hasPass = outgoing.stream()
                    .anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_PASS
                            || connection.getType() == ConnectionType.DEFAULT);
            if (!hasPass)
                errors.add("Review Step '" + step.getLabel() + "' phải có nhánh Đạt.");
            String resultMode = Objects.toString(config.get("resultMode"), "REQUIRE_APPROVAL");
            boolean hasFail = outgoing.stream()
                    .anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_FAIL);
            if ("REQUIRE_APPROVAL".equals(resultMode) && !hasFail)
                errors.add("Review Step '" + step.getLabel() + "' phải có nhánh Không đạt để xác định hướng xử lý.");
            if ("COMMENT_ONLY".equals(resultMode) && hasFail)
                errors.add("Review COMMENT_ONLY '" + step.getLabel() + "' không được có nhánh Không đạt.");
            if (mode.isBlank())
                errors.add("Review Step '" + step.getLabel() + "' chưa được cấu hình.");
            return;
        }
        if ("AUTO".equals(mode)) {
            if (!(config.get("autoConditions") instanceof Collection<?> c) || c.isEmpty())
                errors.add("Auto Approval '" + step.getLabel() + "' thiếu condition.");
            if (outgoing.stream().noneMatch(x -> x.getType() == ConnectionType.APPROVE)
                    || outgoing.stream().noneMatch(x -> x.getType() == ConnectionType.REJECT))
                errors.add("Auto Approval '" + step.getLabel() + "' phải có đủ nhánh APPROVE và REJECT.");
        }
        if (mode.isBlank())
            errors.add("Approval Step '" + step.getLabel() + "' chưa được cấu hình.");
    }

    boolean hasCycle(List<WorkflowStep> steps, List<WorkflowConnection> connections) {
        Map<UUID, List<UUID>> graph = new HashMap<>();
        connections.stream().filter(connection -> !isHumanCorrectionBranch(connection))
                .forEach(c -> graph.computeIfAbsent(c.getFromStep().getId(), ignored -> new ArrayList<>())
                        .add(c.getToStep().getId()));
        Set<UUID> visiting = new HashSet<>(), visited = new HashSet<>();
        for (WorkflowStep step : steps)
            if (cycle(step.getId(), graph, visiting, visited))
                return true;
        return false;
    }

    private boolean isHumanCorrectionBranch(WorkflowConnection connection) {
        if (connection.getType() == ConnectionType.REVIEW_FAIL
                || connection.getType() == ConnectionType.ASSIGNMENT_FAIL)
            return true;
        if (connection.getType() != ConnectionType.REJECT
                || connection.getFromStep().getType() != StepType.APPROVAL)
            return false;
        try {
            Map<?, ?> config = connection.getFromStep().getConfigJson() == null
                    ? Map.of()
                    : objectMapper.readValue(connection.getFromStep().getConfigJson(), Map.class);
            return "MANUAL".equalsIgnoreCase(Objects.toString(config.get("mode"), ""));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean cycle(UUID node, Map<UUID, List<UUID>> graph, Set<UUID> visiting, Set<UUID> visited) {
        if (visiting.contains(node))
            return true;
        if (!visited.add(node))
            return false;
        visiting.add(node);
        for (UUID next : graph.getOrDefault(node, List.of()))
            if (cycle(next, graph, visiting, visited))
                return true;
        visiting.remove(node);
        return false;
    }
}
