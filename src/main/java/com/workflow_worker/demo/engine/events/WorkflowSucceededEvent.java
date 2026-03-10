package com.workflow_worker.demo.engine.events;

import java.util.UUID;

public class WorkflowSucceededEvent {

    private final UUID runId;

    public WorkflowSucceededEvent(UUID runId) {
        this.runId = runId;
    }

    public UUID getRunId() {
        return runId;
    }
}