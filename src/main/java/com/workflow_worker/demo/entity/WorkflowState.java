package com.workflow_worker.demo.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_states")
public class WorkflowState {

    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "execution_context", columnDefinition = "text")
    private String executionContext;

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Integer currentStep) {
        this.currentStep = currentStep;
    }

    public String getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(String executionContext) {
        this.executionContext = executionContext;
    }

    public UUID getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(UUID checkpointId) {
        this.checkpointId = checkpointId;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
