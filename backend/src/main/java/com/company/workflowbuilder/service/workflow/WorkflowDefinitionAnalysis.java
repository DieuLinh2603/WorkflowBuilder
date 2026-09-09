package com.company.workflowbuilder.service.workflow;

import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowConnection;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionAnalysis {
    private final WorkflowStepRepository steps;
    private final CustomFieldDefinitionRepository fields;
    private final WorkflowConnectionRepository connections;

    public String nextVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            return parts[0] + "." + (Integer.parseInt(parts.length > 1 ? parts[1] : "0") + 1);
        } catch (Exception exception) {
            return version + ".1";
        }
    }

    public Comparator<Workflow> versionComparator() {
        return (left, right) -> {
            String[] a = left.getVersion().split("\\.");
            String[] b = right.getVersion().split("\\.");
            int length = Math.max(a.length, b.length);
            for (int index = 0; index < length; index++) {
                int av = index < a.length ? versionPart(a[index]) : 0;
                int bv = index < b.length ? versionPart(b[index]) : 0;
                if (av != bv)
                    return Integer.compare(av, bv);
            }
            return Comparator.nullsFirst(LocalDateTime::compareTo).compare(left.getCreatedAt(), right.getCreatedAt());
        };
    }

    public boolean isUnchangedDerivedDraft(Workflow workflow) {
        Workflow source = workflow.getSourceWorkflow();
        return workflow.getStatus() == WorkflowStatus.DRAFT && source != null
                && Objects.equals(source.getFamilyId(), workflow.getFamilyId())
                && Objects.equals(fingerprint(source), fingerprint(workflow));
    }

    public List<String> compare(Workflow from, Workflow to) {
        List<WorkflowStep> targetSteps = steps.findByWorkflowIdOrderByPositionXAsc(to.getId());
        if (from == null)
            return List.of("Tạo workflow ban đầu với " + targetSteps.size() + " steps");
        List<String> changes = new ArrayList<>();
        List<WorkflowStep> sourceSteps = steps.findByWorkflowIdOrderByPositionXAsc(from.getId());
        Map<String, WorkflowStep> sourceByKey = sourceSteps.stream()
                .collect(Collectors.toMap(this::stepKey, step -> step, (first, ignored) -> first));
        Map<String, WorkflowStep> targetByKey = targetSteps.stream()
                .collect(Collectors.toMap(this::stepKey, step -> step, (first, ignored) -> first));
        targetByKey.forEach((key, step) -> {
            if (!sourceByKey.containsKey(key)) changes.add("Thêm step: " + step.getLabel());
        });
        sourceByKey.forEach((key, step) -> {
            if (!targetByKey.containsKey(key)) changes.add("Xóa step: " + step.getLabel());
        });
        targetByKey.forEach((key, step) -> {
            WorkflowStep old = sourceByKey.get(key);
            if (old != null && !Objects.equals(old.getConfigJson(), step.getConfigJson()))
                changes.add("Cập nhật cấu hình step: " + step.getLabel());
        });
        Set<String> sourceFields = fields.findByStepWorkflowId(from.getId()).stream().map(this::fieldSignature)
                .collect(Collectors.toSet());
        Set<String> targetFields = fields.findByStepWorkflowId(to.getId()).stream().map(this::fieldSignature)
                .collect(Collectors.toSet());
        long addedFields = targetFields.stream().filter(field -> !sourceFields.contains(field)).count();
        long removedFields = sourceFields.stream().filter(field -> !targetFields.contains(field)).count();
        if (addedFields > 0) changes.add("Thêm hoặc cập nhật " + addedFields + " trường form");
        if (removedFields > 0) changes.add("Xóa hoặc thay đổi " + removedFields + " trường form cũ");
        Set<String> sourceConnections = connections.findByWorkflowId(from.getId()).stream()
                .map(this::connectionSignature).collect(Collectors.toSet());
        Set<String> targetConnections = connections.findByWorkflowId(to.getId()).stream()
                .map(this::connectionSignature).collect(Collectors.toSet());
        if (!sourceConnections.equals(targetConnections)) changes.add("Cập nhật sơ đồ connection và nhánh xử lý");
        if (!Objects.equals(from.getDescription(), to.getDescription())) changes.add("Cập nhật mô tả workflow");
        if (changes.isEmpty()) changes.add("Tạo phiên bản mới, không thay đổi cấu trúc");
        return changes;
    }

    private String fingerprint(Workflow workflow) {
        List<String> stepDefinitions = steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId()).stream()
                .map(step -> step.getType() + "|" + Objects.toString(step.getLabel(), "") + "|"
                        + Objects.toString(step.getConfigJson(), "")).sorted().toList();
        List<String> fieldDefinitions = fields.findByStepWorkflowId(workflow.getId()).stream()
                .map(field -> stepKey(field.getStep()) + "|" + field.getFieldKey() + "|" + field.getLabel() + "|"
                        + field.getType() + "|" + field.isRequired() + "|" + Objects.toString(field.getPlaceholder(), "")
                        + "|" + field.getDisplayOrder()).sorted().toList();
        List<String> connectionDefinitions = connections.findByWorkflowId(workflow.getId()).stream()
                .map(connection -> stepKey(connection.getFromStep()) + "->" + connection.getType() + "->"
                        + stepKey(connection.getToStep()) + "|" + connection.getLogicalOperator() + "|"
                        + connection.getClauses().stream().map(clause -> clause.getFieldKey() + "|"
                                + clause.getOperator() + "|" + Objects.toString(clause.getExpectedValue(), "") + "|"
                                + clause.getDisplayOrder()).sorted().toList()).sorted().toList();
        return String.join("\n", List.of(Objects.toString(workflow.getName(), ""),
                Objects.toString(workflow.getDescription(), ""), Objects.toString(workflow.getType(), ""),
                Objects.toString(workflow.getModule(), ""), stepDefinitions.toString(), fieldDefinitions.toString(),
                connectionDefinitions.toString()));
    }

    private int versionPart(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")); }
        catch (Exception ignored) { return 0; }
    }

    private String stepKey(WorkflowStep step) {
        return step.getType() + "|" + Objects.toString(step.getLabel(), "").trim().toLowerCase(Locale.ROOT);
    }

    private String fieldSignature(CustomFieldDefinition field) {
        return stepKey(field.getStep()) + "|" + field.getFieldKey() + "|" + field.getLabel() + "|" + field.getType()
                + "|" + field.isRequired();
    }

    private String connectionSignature(WorkflowConnection connection) {
        return stepKey(connection.getFromStep()) + "->" + connection.getType() + "->"
                + stepKey(connection.getToStep()) + "|" + connection.getClauses().stream()
                .map(clause -> clause.getFieldKey() + clause.getOperator() + clause.getExpectedValue()).toList();
    }
}
