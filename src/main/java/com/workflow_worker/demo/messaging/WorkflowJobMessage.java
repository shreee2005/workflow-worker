package com.workflow_worker.demo.messaging;
import java.util.UUID;
public class WorkflowJobMessage {
    private UUID runId;
    private UUID workflowId;
    private UUID workflowVersionId;
    private String payload;
    private int attempt;

    public UUID getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(UUID workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public UUID getRunId() { return runId; }
    public UUID getWorkflowId() { return workflowId; }
    public String getPayload() { return payload; }

    public void setRunId(UUID runId) { this.runId = runId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }
    public void setPayload(String payload) { this.payload = payload; }
}

