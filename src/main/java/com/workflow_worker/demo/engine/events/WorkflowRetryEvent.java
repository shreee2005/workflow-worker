package com.workflow_worker.demo.engine.events;

import java.util.UUID;

public class WorkflowRetryEvent {

    private final UUID runId;
    private final int attempt;

    public WorkflowRetryEvent(UUID runId, int attempt) {
        this.runId = runId;
        this.attempt = attempt;
    }

    public UUID getRunId() {
        return runId;
    }

    public int getAttempt() {
        return attempt;
    }
}