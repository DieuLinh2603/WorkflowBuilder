package com.company.workflowbuilder.entity.user;

/**
 * System-level roles controlling access to Workflow Builder features.
 * These are NOT the same as step-level actors (Approver/Reviewer/Assignee)
 * which are resolved at runtime via StepActorRule.
 *
 * A User may have zero SystemRoles (e.g., an "Approver-only" user).
 */
public enum SystemRole {
    ADMIN,
    WORKFLOW_OWNER,
    EDITOR,
    VIEWER
}
