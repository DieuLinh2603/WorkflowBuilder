package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.ConnectionUpsertRequest;
import com.company.workflowbuilder.dto.response.ConnectionResponse;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class WorkflowConnectionService {
    private final ObjectMapper expressionMapper = new ObjectMapper();
    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowConnectionRepository connections;
    private final CustomFieldDefinitionRepository fields;
    private final WorkflowAuthorizationService authorization;

    @Transactional
    public ConnectionResponse create(UUID workflowId, ConnectionUpsertRequest request) {
        Workflow workflow = workflow(workflowId);
        authorization.requireEdit(workflow);
        requireDraft(workflow);
        WorkflowStep from = step(workflowId, request.getFromStepId());
        WorkflowStep to = step(workflowId, request.getToStepId());
        if (from.getId().equals(to.getId()))
            throw new IllegalArgumentException("Connection cannot target the same step");
        validateShape(workflowId, from, request, null);
        WorkflowConnection connection = WorkflowConnection.builder().workflow(workflow).fromStep(from).toStep(to)
                .type(request.getType()).logicalOperator(request.getLogicalOperator()).priority(request.getPriority()).build();
        for (int i = 0; i < request.getClauses().size(); i++) {
            var item = request.getClauses().get(i);
            connection.getClauses().add(WorkflowConditionClause.builder().connection(connection)
                    .fieldKey(item.getFieldKey()).operator(item.getOperator()).expectedValue(item.getExpectedValue())
                    .expressionJson(writeExpression(item.getExpression()))
                    .displayOrder(i).build());
        }
        return response(connections.save(connection));
    }

    @Transactional
    public ConnectionResponse update(UUID workflowId, UUID connectionId, ConnectionUpsertRequest request) {
        Workflow workflow = workflow(workflowId);
        authorization.requireEdit(workflow);
        requireDraft(workflow);
        WorkflowConnection connection = connections.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowConnection", "id", connectionId));
        if (!connection.getWorkflow().getId().equals(workflowId))
            throw new IllegalArgumentException("Connection does not belong to workflow");
        if (!connection.getFromStep().getId().equals(request.getFromStepId())
                || !connection.getToStep().getId().equals(request.getToStepId()))
            throw new IllegalArgumentException("Cannot change connection endpoints while editing its type");
        validateShape(workflowId, connection.getFromStep(), request, connectionId);
        connection.setType(request.getType());
        connection.setLogicalOperator(request.getLogicalOperator());
        connection.setPriority(request.getPriority());
        connection.getClauses().clear();
        for (int i = 0; i < request.getClauses().size(); i++) {
            var item = request.getClauses().get(i);
            connection.getClauses().add(WorkflowConditionClause.builder().connection(connection)
                    .fieldKey(item.getFieldKey()).operator(item.getOperator()).expectedValue(item.getExpectedValue())
                    .expressionJson(writeExpression(item.getExpression()))
                    .displayOrder(i).build());
        }
        return response(connections.save(connection));
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(UUID workflowId) {
        Workflow workflow = workflow(workflowId);
        authorization.requireView(workflow);
        return connections.findByWorkflowId(workflowId).stream().map(this::response).toList();
    }

    @Transactional
    public void delete(UUID workflowId, UUID connectionId) {
        Workflow workflow = workflow(workflowId);
        authorization.requireEdit(workflow);
        requireDraft(workflow);
        WorkflowConnection connection = connections.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowConnection", "id", connectionId));
        if (!connection.getWorkflow().getId().equals(workflowId))
            throw new IllegalArgumentException("Connection does not belong to workflow");
        connections.delete(connection);
    }

    private void validateShape(UUID workflowId, WorkflowStep from, ConnectionUpsertRequest request,
            UUID currentConnectionId) {
        Set<ConnectionType> approvalTypes = EnumSet.of(ConnectionType.APPROVE, ConnectionType.REJECT);
        Set<ConnectionType> reviewTypes = EnumSet.of(ConnectionType.REVIEW_PASS, ConnectionType.REVIEW_FAIL);
        Set<ConnectionType> assignmentTypes = EnumSet.of(ConnectionType.ASSIGNMENT_DONE, ConnectionType.ASSIGNMENT_FAIL);
        Set<ConnectionType> systemTypes = EnumSet.of(ConnectionType.SYSTEM_SUCCESS, ConnectionType.SYSTEM_FAIL);
        if (from.getType() == StepType.APPROVAL) {
            if (!approvalTypes.contains(request.getType()))
                throw new IllegalArgumentException("Approval Step chỉ hỗ trợ connection APPROVE hoặc REJECT");
            if (!request.getClauses().isEmpty())
                throw new IllegalArgumentException("APPROVE/REJECT connection không chứa condition clauses");
            boolean duplicate = connections.findByFromStepId(from.getId()).stream()
                    .anyMatch(connection -> !connection.getId().equals(currentConnectionId)
                            && connection.getType() == request.getType());
            if (duplicate)
                throw new IllegalArgumentException("Approval Step chỉ có tối đa một nhánh " + request.getType());
        } else if (from.getType() == StepType.REVIEW) {
            if (!reviewTypes.contains(request.getType()))
                throw new IllegalArgumentException("Review Step chỉ hỗ trợ connection REVIEW_PASS hoặc REVIEW_FAIL");
            if (!request.getClauses().isEmpty())
                throw new IllegalArgumentException("Nhánh kết quả Review không chứa condition clauses");
            boolean duplicate = connections.findByFromStepId(from.getId()).stream()
                    .anyMatch(connection -> !connection.getId().equals(currentConnectionId)
                            && connection.getType() == request.getType());
            if (duplicate)
                throw new IllegalArgumentException("Review Step chỉ có tối đa một nhánh " + request.getType());
        } else if (from.getType() == StepType.ASSIGNMENT) {
            if (!assignmentTypes.contains(request.getType()))
                throw new IllegalArgumentException("Assignment chỉ hỗ trợ connection kết quả hoàn thành hoặc thất bại");
            if (!request.getClauses().isEmpty())
                throw new IllegalArgumentException("Connection kết quả không chứa condition clauses");
            boolean duplicate = connections.findByFromStepId(from.getId()).stream()
                    .anyMatch(connection -> !connection.getId().equals(currentConnectionId)
                            && connection.getType() == request.getType());
            if (duplicate)
                throw new IllegalArgumentException(from.getType() + " chỉ có tối đa một nhánh " + request.getType());
        } else if (from.getType() == StepType.SYSTEM_ACTION) {
            Set<ConnectionType> allowed = EnumSet.of(ConnectionType.SYSTEM_SUCCESS, ConnectionType.SYSTEM_FAIL,
                    ConnectionType.IF, ConnectionType.ELSE);
            if (!allowed.contains(request.getType()))
                throw new IllegalArgumentException("System Action chỉ hỗ trợ nhánh thành công, điều kiện hoặc thất bại");
            List<WorkflowConnection> existing = connections.findByFromStepId(from.getId()).stream()
                    .filter(connection -> !connection.getId().equals(currentConnectionId)).toList();
            if (systemTypes.contains(request.getType())) {
                if (!request.getClauses().isEmpty())
                    throw new IllegalArgumentException("Connection kết quả không chứa condition clauses");
                if (existing.stream().anyMatch(connection -> connection.getType() == request.getType()))
                    throw new IllegalArgumentException("System Action chỉ có tối đa một nhánh " + request.getType());
            }
            if (request.getType() == ConnectionType.SYSTEM_SUCCESS
                    && existing.stream().anyMatch(connection -> connection.getType() == ConnectionType.IF
                            || connection.getType() == ConnectionType.ELSE))
                throw new IllegalArgumentException("Không thể dùng đồng thời nhánh Thành công và nhánh IF/ELSE");
            if ((request.getType() == ConnectionType.IF || request.getType() == ConnectionType.ELSE)
                    && existing.stream().anyMatch(connection -> connection.getType() == ConnectionType.SYSTEM_SUCCESS))
                throw new IllegalArgumentException("Hãy xóa nhánh Thành công trước khi cấu hình IF/ELSE");
        } else if (approvalTypes.contains(request.getType()) || reviewTypes.contains(request.getType())
                || assignmentTypes.contains(request.getType()) || systemTypes.contains(request.getType())) {
            throw new IllegalArgumentException(
                    "Loại connection kết quả chỉ được dùng cho đúng loại Approval/Review Step");
        }
        if (request.getType() == ConnectionType.IF && request.getClauses().isEmpty())
            throw new IllegalArgumentException("IF connection requires at least one condition clause");
        if (request.getType() == ConnectionType.IF)
            validateClauses(workflowId, request);
        if (request.getType() == ConnectionType.ELSE && !request.getClauses().isEmpty())
            throw new IllegalArgumentException("ELSE connection cannot contain condition clauses");
        if (request.getType() == ConnectionType.ELSE && connections.findByFromStepId(from.getId()).stream()
                .anyMatch(connection -> !connection.getId().equals(currentConnectionId)
                        && connection.getType() == ConnectionType.ELSE))
            throw new IllegalArgumentException("A step can have at most one ELSE connection");

        if (request.getType() == ConnectionType.IF || request.getType() == ConnectionType.ELSE) {
            ConnectionType otherType = request.getType() == ConnectionType.IF ? ConnectionType.ELSE : ConnectionType.IF;
            boolean sameTarget = connections.findByFromStepId(from.getId()).stream()
                    .anyMatch(connection -> !connection.getId().equals(currentConnectionId)
                            && connection.getType() == otherType
                            && connection.getToStep().getId().equals(request.getToStepId()));
            if (sameTarget)
                throw new IllegalArgumentException("Nhánh IF và ELSE phải nối tới hai step khác nhau");
        }
    }

    private void validateClauses(UUID workflowId, ConnectionUpsertRequest request) {
        Map<String, CustomFieldDefinition> workflowFields = new HashMap<>();
        fields.findByStepWorkflowId(workflowId)
                .forEach(field -> workflowFields.putIfAbsent(field.getFieldKey(), field));

        for (ConnectionUpsertRequest.Clause clause : request.getClauses()) {
            if (clause.getExpression() != null && !clause.getExpression().isEmpty()) {
                validateExpression(clause.getExpression(), workflowFields.keySet(), 0, new int[]{0});
                continue;
            }
            if (clause.getFieldKey() == null || clause.getFieldKey().isBlank() || clause.getOperator() == null)
                throw new IllegalArgumentException("Simple condition requires fieldKey and operator");
            CustomFieldDefinition field = workflowFields.get(clause.getFieldKey());
            if (field == null)
                throw new IllegalArgumentException(
                        "Trường điều kiện không tồn tại trong workflow: " + clause.getFieldKey());
            if (!allowedOperators(field.getType()).contains(clause.getOperator()))
                throw new IllegalArgumentException(
                        "Operator " + clause.getOperator() + " không phù hợp với trường " + field.getLabel());
            validateExpectedValue(field, clause);
        }
    }

    private Set<ConditionOperator> allowedOperators(FieldType type) {
        return switch (type) {
            case TEXT -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ, ConditionOperator.CONTAINS,
                    ConditionOperator.NOT_CONTAINS, ConditionOperator.STARTS_WITH, ConditionOperator.ENDS_WITH,
                    ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY,
                    ConditionOperator.IS_NULL, ConditionOperator.NOT_NULL);
            case NUMBER, DATE, DATETIME -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ, ConditionOperator.GT,
                    ConditionOperator.GTE, ConditionOperator.LT, ConditionOperator.LTE,
                    ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.BETWEEN,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY, ConditionOperator.IS_NULL, ConditionOperator.NOT_NULL);
            case CHECKBOX -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY, ConditionOperator.IS_NULL,
                    ConditionOperator.NOT_NULL, ConditionOperator.IS_TRUE, ConditionOperator.IS_FALSE);
            case FILE -> EnumSet.of(ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
            case SELECT, RADIO -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ,
                    ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.IS_EMPTY,
                    ConditionOperator.NOT_EMPTY, ConditionOperator.IS_NULL, ConditionOperator.NOT_NULL);
            case MULTI_CHOICE, USER_PICKER -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ,
                    ConditionOperator.CONTAINS, ConditionOperator.NOT_CONTAINS,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY,
                    ConditionOperator.IS_NULL, ConditionOperator.NOT_NULL);
        };
    }

    private void validateExpectedValue(CustomFieldDefinition field, ConnectionUpsertRequest.Clause clause) {
        if (EnumSet.of(ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY, ConditionOperator.IS_NULL,
                ConditionOperator.NOT_NULL, ConditionOperator.IS_TRUE, ConditionOperator.IS_FALSE).contains(clause.getOperator()))
            return;
        String value = clause.getExpectedValue();
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Giá trị điều kiện không được để trống: " + field.getLabel());
        try {
            switch (field.getType()) {
                case NUMBER -> new BigDecimal(value);
                case DATE -> LocalDate.parse(value);
                case DATETIME -> java.time.LocalDateTime.parse(value);
                case CHECKBOX -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                        throw new IllegalArgumentException("Giá trị checkbox phải là true hoặc false");
                }
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Giá trị điều kiện không đúng kiểu của trường " + field.getLabel());
        }
    }

    private void requireDraft(Workflow workflow) {
        if (workflow.getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalArgumentException("Chỉ được thay đổi connection của workflow DRAFT");
    }

    private Workflow workflow(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", id));
    }

    private WorkflowStep step(UUID workflowId, UUID id) {
        WorkflowStep step = steps.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", id));
        if (!step.getWorkflow().getId().equals(workflowId))
            throw new IllegalArgumentException("Step does not belong to workflow");
        return step;
    }

    private ConnectionResponse response(WorkflowConnection c) {
        return ConnectionResponse.builder().id(c.getId()).fromStepId(c.getFromStep().getId())
                .toStepId(c.getToStep().getId())
                .type(c.getType()).logicalOperator(c.getLogicalOperator()).priority(c.getPriority())
                .clauses(c.getClauses().stream()
                        .map(x -> ConnectionResponse.ClauseResponse.builder().id(x.getId()).fieldKey(x.getFieldKey())
                                .operator(x.getOperator()).expectedValue(x.getExpectedValue())
                                .expression(readExpression(x.getExpressionJson())).build())
                        .toList())
                .build();
    }

    private void validateExpression(Map<String,Object> node, Set<String> fieldKeys, int depth, int[] count) {
        if (depth > 12 || ++count[0] > 200) throw new IllegalArgumentException("Condition expression is too complex");
        String type = String.valueOf(node.getOrDefault("type", "")).toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of("FIELD","VALUE","LITERAL","ADD","SUBTRACT","MULTIPLY","DIVIDE","MOD",
                "SUM","AVG","MIN","MAX","COUNT","ABS","ROUND","COALESCE","CONCAT","LENGTH","TRIM",
                "LOWER","UPPER","COMPARE","AND","OR","NOT");
        if (!allowed.contains(type)) throw new IllegalArgumentException("Unsupported expression type: " + type);
        if ("FIELD".equals(type) && !fieldKeys.contains(String.valueOf(node.get("fieldKey"))))
            throw new IllegalArgumentException("Expression references an unknown workflow field: " + node.get("fieldKey"));
        if (node.get("fields") instanceof Collection<?> fields)
            for (Object field : fields) if (!fieldKeys.contains(String.valueOf(field)))
                throw new IllegalArgumentException("Expression references an unknown workflow field: " + field);
        if ("COMPARE".equals(type)) {
            try { ConditionOperator.valueOf(String.valueOf(node.get("operator")).toUpperCase(Locale.ROOT)); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("Unsupported comparison operator: " + node.get("operator")); }
            if (!(node.get("left") instanceof Map<?,?>)) throw new IllegalArgumentException("COMPARE requires left expression");
        }
        for (String childKey : List.of("left","right","operand","child"))
            if (node.get(childKey) instanceof Map<?,?> child) validateExpression(expressionMapper.convertValue(child, new TypeReference<>(){}), fieldKeys, depth+1, count);
        for (String childrenKey : List.of("operands","children"))
            if (node.get(childrenKey) instanceof Collection<?> children) for (Object child : children)
                if (child instanceof Map<?,?> map) validateExpression(expressionMapper.convertValue(map, new TypeReference<>(){}), fieldKeys, depth+1, count);
    }
    private String writeExpression(Map<String,Object> expression) { try { return expression == null || expression.isEmpty() ? null : expressionMapper.writeValueAsString(expression); } catch (Exception ex) { throw new IllegalArgumentException("Invalid condition expression", ex); } }
    private Map<String,Object> readExpression(String value) { if (value == null || value.isBlank()) return null; try { return expressionMapper.readValue(value, new TypeReference<>(){}); } catch (Exception ex) { throw new IllegalStateException("Stored condition expression is invalid", ex); } }
}
