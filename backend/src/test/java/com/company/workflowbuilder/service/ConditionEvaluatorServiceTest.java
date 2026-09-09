package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.workflow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorServiceTest {
    private final ConditionEvaluatorService service = new ConditionEvaluatorService();

    @Test void evaluatesAndConditionsAndNumericOperators() {
        WorkflowConnection connection = connection(LogicalOperator.AND,
                clause("amount", ConditionOperator.LTE, "10000000"),
                clause("department", ConditionOperator.EQ, "IT"));
        assertTrue(service.matches(connection, Map.of("amount", 9_000_000, "department", "IT")));
        assertFalse(service.matches(connection, Map.of("amount", 11_000_000, "department", "IT")));
    }

    @Test void evaluatesOrAndEmptyOperators() {
        WorkflowConnection connection = connection(LogicalOperator.OR,
                clause("note", ConditionOperator.IS_EMPTY, null),
                clause("title", ConditionOperator.CONTAINS, "urgent"));
        assertTrue(service.matches(connection, Map.of("note", "", "title", "normal")));
        assertTrue(service.matches(connection, Map.of("note", "x", "title", "urgent request")));
        assertFalse(service.matches(connection, Map.of("note", "x", "title", "normal")));
    }

    @Test void elseAlwaysMatchesAndIfWithoutClausesDoesNot() {
        assertTrue(service.matches(WorkflowConnection.builder().type(ConnectionType.ELSE).build(), Map.of()));
        assertFalse(service.matches(WorkflowConnection.builder().type(ConnectionType.IF).clauses(new ArrayList<>()).build(), Map.of()));
    }

    @Test void evaluatesNestedArithmeticAggregateAndMembershipExpression() {
        String expression = """
                {"type":"AND","children":[
                  {"type":"COMPARE","operator":"GT",
                   "left":{"type":"SUM","fields":["projectCount","bonusProjectCount"]},
                   "right":{"type":"VALUE","value":5}},
                  {"type":"COMPARE","operator":"IN",
                   "left":{"type":"FIELD","fieldKey":"certificate"},
                   "right":{"type":"VALUE","value":["N4","N3"]}}
                ]}
                """;
        WorkflowConditionClause clause = WorkflowConditionClause.builder().expressionJson(expression).build();
        WorkflowConnection connection = connection(LogicalOperator.AND, clause);
        assertTrue(service.matches(connection, Map.of("projectCount", 4, "bonusProjectCount", 2, "certificate", "N4")));
        assertFalse(service.matches(connection, Map.of("projectCount", 4, "bonusProjectCount", 0, "certificate", "N4")));
        assertFalse(service.matches(connection, Map.of("projectCount", 6, "bonusProjectCount", 0, "certificate", "N2")));
    }

    @Test void comparesCalculatedFieldToAnotherField() {
        String expression = """
                {"type":"COMPARE","operator":"GTE",
                 "left":{"type":"SUBTRACT","operands":[
                   {"type":"FIELD","fieldKey":"revenue"},{"type":"FIELD","fieldKey":"cost"}]},
                 "right":{"type":"FIELD","fieldKey":"minimumProfit"}}
                """;
        WorkflowConnection connection = connection(LogicalOperator.AND,
                WorkflowConditionClause.builder().expressionJson(expression).build());
        assertTrue(service.matches(connection, Map.of("revenue", 120, "cost", 70, "minimumProfit", 50)));
        assertFalse(service.matches(connection, Map.of("revenue", 100, "cost", 70, "minimumProfit", 50)));
    }

    private WorkflowConnection connection(LogicalOperator logical, WorkflowConditionClause... clauses) {
        return WorkflowConnection.builder().type(ConnectionType.IF).logicalOperator(logical).clauses(new ArrayList<>(List.of(clauses))).build();
    }
    private WorkflowConditionClause clause(String field, ConditionOperator operator, String expected) {
        return WorkflowConditionClause.builder().fieldKey(field).operator(operator).expectedValue(expected).build();
    }
}
