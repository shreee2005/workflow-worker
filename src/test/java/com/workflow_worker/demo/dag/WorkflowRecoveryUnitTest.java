package com.workflow_worker.demo.dag;

import com.workflow_worker.demo.engine.*;
import com.workflow_worker.demo.entity.WorkflowState;
import com.workflow_worker.demo.entity.WorkflowVersion;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.repository.WorkflowStateRepository;
import com.workflow_worker.demo.repository.WorkflowVersionRepository;
import com.workflow_worker.demo.repository.WorkflowWaitStateRepository;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowRecoveryUnitTest {

    private WorkflowStateRepository stateRepo;
    private CheckpointManager checkpointManager;

    @BeforeEach
    void setUp() {
        stateRepo = mock(WorkflowStateRepository.class);
        checkpointManager = new CheckpointManager(stateRepo);
    }

    @Test
    void testWorkflowContextSerializationAndDeserialization() {
        UUID runId = UUID.randomUUID();
        WorkflowContext original = new WorkflowContext(runId);

        original.setStepOutput(0, "{\"userId\": \"123\", \"name\": \"John\"}");
        original.setStepOutput(1, "{\"status\": \"active\"}");
        original.setVariable("attempt", 2);
        original.setVariable("mode", "dry-run");

        // Serialize using CheckpointManager (internally ObjectMapper)
        String json = null;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(original);
        } catch (Exception e) {
            fail("Serialization failed", e);
        }

        assertNotNull(json);
        assertTrue(json.contains("stepOutputs"));
        assertTrue(json.contains("variables"));

        // Deserialize
        WorkflowContext restored = WorkflowContext.deserialize(runId, json);

        assertEquals(original.getRunId(), restored.getRunId());
        assertEquals(original.getStepOutput(0), restored.getStepOutput(0));
        assertEquals(original.getStepOutput(1), restored.getStepOutput(1));
        assertEquals(original.getVariable("attempt"), restored.getVariable("attempt"));
        assertEquals(original.getVariable("mode"), restored.getVariable("mode"));

        assertTrue(restored.isStepCompleted(0));
        assertTrue(restored.isStepCompleted(1));
        assertFalse(restored.isStepCompleted(2));
    }

    @Test
    void testCheckpointManagerSaveAndLoad() {
        UUID runId = UUID.randomUUID();
        WorkflowContext context = new WorkflowContext(runId);
        context.setStepOutput(0, "{\"success\":true}");
        context.setVariable("testVar", "hello");

        // Mock state repository behavior
        WorkflowState mockState = new WorkflowState();
        mockState.setRunId(runId);
        when(stateRepo.findById(runId)).thenReturn(Optional.of(mockState));

        UUID checkpointId = checkpointManager.saveCheckpoint(runId, 0, context);
        assertNotNull(checkpointId);

        // Verify save interaction
        ArgumentCaptor<WorkflowState> stateCaptor = ArgumentCaptor.forClass(WorkflowState.class);
        verify(stateRepo, times(1)).saveAndFlush(stateCaptor.capture());

        WorkflowState savedState = stateCaptor.getValue();
        assertEquals(runId, savedState.getRunId());
        assertEquals(0, savedState.getCurrentStep());
        assertEquals(checkpointId, savedState.getCheckpointId());
        assertNotNull(savedState.getExecutionContext());
        assertNotNull(savedState.getUpdatedAt());

        // Test loading
        when(stateRepo.findById(runId)).thenReturn(Optional.of(savedState));
        Optional<WorkflowContext> loadedOpt = checkpointManager.loadCheckpoint(runId);
        assertTrue(loadedOpt.isPresent());

        WorkflowContext loaded = loadedOpt.get();
        assertEquals(context.getStepOutput(0), loaded.getStepOutput(0));
        assertEquals(context.getVariable("testVar"), loaded.getVariable("testVar"));
    }

    @Test
    void testWorkflowExecutorUsesCheckpointOnResumption() {
        UUID runId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        WorkflowVersionRepository versionRepo = mock(WorkflowVersionRepository.class);
        WorkflowWaitStateRepository waitRepo = mock(WorkflowWaitStateRepository.class);
        WorkflowRunStepService stepService = mock(WorkflowRunStepService.class);
        StepDispatcher dispatcher = mock(StepDispatcher.class);
        WorkflowRunService runService = mock(WorkflowRunService.class);

        WorkflowExecutor executor = new WorkflowExecutor(
                versionRepo, waitRepo, stepService, dispatcher, runService, checkpointManager
        );

        // Mock spec: Step 0 -> Step 1
        String spec = """
            {
              "steps": [
                {"type": "LOG", "config": {"message": "Step 0"}},
                {"type": "LOG", "config": {"message": "Step 1", "dependsOn": [0]}}
              ]
            }
            """;

        WorkflowVersion mockVersion = new WorkflowVersion();
        mockVersion.setWorkflowId(workflowId);
        mockVersion.setSpec(spec);
        when(versionRepo.findById(versionId)).thenReturn(Optional.of(mockVersion));

        // Create saved state context where Step 0 has completed already
        WorkflowContext savedContext = new WorkflowContext(runId);
        savedContext.setStepOutput(0, "{\"status\":\"ok\"}");

        WorkflowState state = new WorkflowState();
        state.setRunId(runId);
        state.setCurrentStep(0);
        state.setCheckpointId(UUID.randomUUID());
        state.setUpdatedAt(OffsetDateTime.now());
        try {
            state.setExecutionContext(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(savedContext));
        } catch (Exception e) {
            fail(e);
        }

        when(stateRepo.findById(runId)).thenReturn(Optional.of(state));
        when(stepService.buildContextFromSteps(runId)).thenReturn(new WorkflowContext(runId));

        // Mock dispatcher to succeed for Step 1
        when(dispatcher.dispatch(any(), any())).thenReturn(StepExecutionResult.success("{\"result\":\"success\"}"));

        // Execute Run
        ExecutionOutcome outcome = executor.executeRun(runId, workflowId, versionId, "{}");

        assertEquals(ExecutionOutcome.COMPLETED, outcome);

        // Verify that Step 0 was NEVER executed (since context marked it completed, and we resume from checkpoint)
        verify(stepService, never()).startStep(eq(runId), eq(0), anyString());

        // Verify Step 1 WAS executed
        verify(stepService, times(1)).startStep(eq(runId), eq(1), anyString());
    }
}
