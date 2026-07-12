package com.workflow_worker.demo.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow_worker.demo.dag.WorkflowContext;
import com.workflow_worker.demo.entity.WorkflowState;
import com.workflow_worker.demo.repository.WorkflowStateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class CheckpointManager {

    private final WorkflowStateRepository workflowStateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CheckpointManager(WorkflowStateRepository workflowStateRepository) {
        this.workflowStateRepository = workflowStateRepository;
    }

    @Transactional
    public UUID saveCheckpoint(UUID runId, int currentStep, WorkflowContext context) {
        try {
            // Serialize context to JSON
            String executionContextJson = objectMapper.writeValueAsString(context);
            UUID checkpointId = UUID.randomUUID();

            WorkflowState state = workflowStateRepository.findById(runId)
                    .orElseGet(() -> {
                        WorkflowState s = new WorkflowState();
                        s.setRunId(runId);
                        return s;
                    });

            state.setCurrentStep(currentStep);
            state.setExecutionContext(executionContextJson);
            state.setCheckpointId(checkpointId);
            state.setUpdatedAt(OffsetDateTime.now());

            workflowStateRepository.saveAndFlush(state);
            return checkpointId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save workflow state checkpoint: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowContext> loadCheckpoint(UUID runId) {
        return workflowStateRepository.findById(runId)
                .map(state -> WorkflowContext.deserialize(runId, state.getExecutionContext()));
    }
}
