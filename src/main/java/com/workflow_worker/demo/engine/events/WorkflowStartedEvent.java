package com.workflow_worker.demo.engine.events;

import java.util.UUID;

public class WorkflowStartedEvent {

    private final UUID runId;
    private final UUID workflowId;

    public WorkflowStartedEvent(UUID runId, UUID workflowId) {
        this.runId = runId;
        this.workflowId = workflowId;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }
}