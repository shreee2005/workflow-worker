package com.workflow_worker.demo.engine.events;

import java.util.UUID;

public class WorkflowFailedEvent {

    private final UUID runId;
    private final String error;

    public WorkflowFailedEvent(UUID runId, String error) {
        this.runId = runId;
        this.error = error;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getError() {
        return error;
    }
}