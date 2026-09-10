package com.company.workflowbuilder.entity.user;

/**
 * System-level roles controlling access to Workflow Builder features.
 * These are NOT the same as step-level actors (Approver/Reviewer/Assignee)
 * which are resolved at runtime via StepActorRule.
 *
 * Every user must have at least one system role. Approver, reviewer and
 * assignee are step-level responsibilities that can be assigned to a viewer.
 */
public enum SystemRole {
    ADMIN,
    WORKFLOW_OWNER,
    EDITOR,
    VIEWER
}
