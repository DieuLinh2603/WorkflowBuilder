package com.company.workflowbuilder.entity.workflow;

/**
 * All possible step types in a workflow.
 * START must appear exactly once per workflow.
 * END can appear multiple times (multiple terminal branches).
 */
public enum StepType {
    START,
    APPROVAL,
    REVIEW,
    ASSIGNMENT,
    NOTIFICATION,
    SYSTEM_ACTION,
    END
}
