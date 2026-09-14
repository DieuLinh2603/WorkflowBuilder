package com.company.workflowbuilder.entity.workflow;

/**
 * System-level workflow statuses.
 * DRAFT → PUBLISHED → SUSPENDED → DELETED
 */
public enum WorkflowStatus {
    DRAFT,
    PUBLISHED,
    SUSPENDED,
    ARCHIVED,
    DELETED
}
