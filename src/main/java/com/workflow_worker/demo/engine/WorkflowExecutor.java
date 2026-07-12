package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.dag.*;
import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.entity.WorkflowVersion;
import com.workflow_worker.demo.entity.WorkflowWaitState;
import com.workflow_worker.demo.repository.WorkflowVersionRepository;
import com.workflow_worker.demo.repository.WorkflowWaitStateRepository;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class WorkflowExecutor {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowWaitStateRepository workflowWaitStateRepository;
    private final WorkflowRunStepService stepService;
    private final StepDispatcher dispatcher;
    private final CheckpointManager checkpointManager;

    // Timeout for parallel step execution (in milliseconds)
    private static final long STAGE_EXECUTION_TIMEOUT_MS = 300000; // 5 minutes

    public WorkflowExecutor(
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowWaitStateRepository workflowWaitStateRepository,
            WorkflowRunStepService stepService,
            StepDispatcher dispatcher,
            WorkflowRunService workflowRunService,
            CheckpointManager checkpointManager
    ) {
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowWaitStateRepository = workflowWaitStateRepository;
        this.stepService = stepService;
        this.dispatcher = dispatcher;
        this.checkpointManager = checkpointManager;
    }

    @WithSpan("workflow.execute")
    public ExecutionOutcome executeRun(
            @SpanAttribute("run.id") UUID runId,
            @SpanAttribute("workflow.id") UUID workflowId,
            @SpanAttribute("workflow.version.id") UUID workflowVersionId,
            String payload
    ) {
        Span currentSpan = Span.current();

        try {
            WorkflowVersion wfVersion = workflowVersionRepository.findById(workflowVersionId)
                    .orElseThrow(() -> new RuntimeException("Workflow version not found"));

            if (!wfVersion.getWorkflowId().equals(workflowId)) {
                throw new RuntimeException("Workflow version does not belong to workflow");
            }

            // Parse steps from JSON spec string
            List<StepDefinition> steps = parseWorkflowSpec(wfVersion.getSpec());
            currentSpan.setAttribute("workflow.total.steps", steps.size());

            // Build execution plan from DAG
            ExecutionPlan executionPlan = DagParser.parse(steps);
            currentSpan.setAttribute("workflow.stages", executionPlan.getStageCount());

            // Load from checkpoint if exists, otherwise fallback to rebuilding from DB steps
            WorkflowContext context = checkpointManager.loadCheckpoint(runId)
                    .orElseGet(() -> stepService.buildContextFromSteps(runId));

            // Merge any additional succeeded steps from the DB
            // that might not have been captured in the last written checkpoint context
            WorkflowContext dbContext = stepService.buildContextFromSteps(runId);
            for (int stepIdx : dbContext.getCompletedStepIndices()) {
                if (!context.isStepCompleted(stepIdx)) {
                    context.setStepOutput(stepIdx, dbContext.getStepOutput(stepIdx));
                }
            }

            // Execute stages
            ExecutionOutcome outcome = executeStages(
                    runId, workflowId, workflowVersionId,
                    steps, executionPlan, context, payload, currentSpan
            );

            currentSpan.setAttribute("execution.outcome", outcome.toString());
            return outcome;

        } catch (Exception ex) {
            currentSpan.recordException(ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Parse workflow spec JSON string into StepDefinition list.
     */
    private List<StepDefinition> parseWorkflowSpec(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            throw new IllegalArgumentException("Workflow spec cannot be null or empty");
        }

        try {
            return WorkflowSpecParser.parse(specJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse workflow spec: " + ex.getMessage(), ex);
        }
    }


    private ExecutionOutcome executeStages(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            List<StepDefinition> steps,
            ExecutionPlan executionPlan,
            WorkflowContext context,
            String payload,
            Span span
    ) throws Exception {
        List<ExecutionStage> stages = executionPlan.getStages();

        // Find first incomplete stage
        int startStageIndex = findFirstIncompleteStage(runId, executionPlan, context);

        if (startStageIndex == -1) {
            // All stages completed
            span.setAttribute("workflow.status", "completed");
            return ExecutionOutcome.COMPLETED;
        }

        // Execute from first incomplete stage onwards
        for (int stageIndex = startStageIndex; stageIndex < stages.size(); stageIndex++) {
            ExecutionStage stage = stages.get(stageIndex);

            span.setAttribute("stage.number", stageIndex);
            span.setAttribute("stage.step_count", stage.getStepCount());
            span.setAttribute("stage.parallel", stage.isParallel());

            try {
                ExecutionOutcome stageOutcome = executeStage(
                        runId, workflowId, workflowVersionId,
                        stage, steps, context, payload
                );

                if (stageOutcome == ExecutionOutcome.WAITING) {
                    span.setAttribute("stage.status", "waiting");
                    return ExecutionOutcome.WAITING;
                }

                span.setAttribute("stage.status", "completed");

                // Save checkpoint after stage completion (satisfies "after successful parallel stage" and "Save state after each major step")
                int lastStepIndex = stage.getStepIndices().get(stage.getStepIndices().size() - 1);
                checkpointManager.saveCheckpoint(runId, lastStepIndex, context);
            } catch (Exception ex) {
                span.recordException(ex);
                span.setAttribute("stage.status", "failed");
                throw ex;
            }
        }

        return ExecutionOutcome.COMPLETED;
    }


    /**
     * Find the first stage that hasn't been completed yet.
     * A stage is complete if all its steps are in SUCCEEDED, FAILED, or WAITING status.
     */
    private int findFirstIncompleteStage(UUID runId, ExecutionPlan executionPlan, WorkflowContext context) {
        List<ExecutionStage> stages = executionPlan.getStages();
        List<WorkflowRunStep> existingSteps = stepService.getStepsForRun(runId);

        for (ExecutionStage stage : stages) {
            boolean stageComplete = stage.getStepIndices().stream()
                    .allMatch(stepIndex -> context.isStepCompleted(stepIndex) || existingSteps.stream()
                            .filter(s -> s.getStepIndex() == stepIndex)
                            .anyMatch(s -> s.getStatus() == WorkflowRunStep.Status.SUCCEEDED
                                     || s.getStatus() == WorkflowRunStep.Status.FAILED
                                     || s.getStatus() == WorkflowRunStep.Status.WAITING)
                    );

            if (!stageComplete) {
                return stage.getStageNumber();
            }
        }

        return -1;  // All stages complete
    }

    /**
     * Execute a single stage (all steps in parallel via CompletableFuture).
     */
    private ExecutionOutcome executeStage(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            ExecutionStage stage,
            List<StepDefinition> steps,
            WorkflowContext context,
            String payload
    ) throws Exception {
        List<Integer> stepIndices = stage.getStepIndices();

        if (stepIndices.isEmpty()) {
            return ExecutionOutcome.COMPLETED;
        }

        // Skip steps already in a terminal/waiting state
        List<WorkflowRunStep> existingSteps = stepService.getStepsForRun(runId);
        Set<Integer> alreadyExecutedInStage = stepIndices.stream()
                .filter(idx -> existingSteps.stream()
                        .anyMatch(s -> s.getStepIndex() == idx &&
                                (s.getStatus() == WorkflowRunStep.Status.SUCCEEDED ||
                                        s.getStatus() == WorkflowRunStep.Status.FAILED ||
                                        s.getStatus() == WorkflowRunStep.Status.WAITING ||
                                        s.getStatus() == WorkflowRunStep.Status.RUNNING)))
                .collect(Collectors.toSet());

        List<Integer> stepsToExecute = stepIndices.stream()
                .filter(idx -> !alreadyExecutedInStage.contains(idx))
                .toList();

        if (stepsToExecute.isEmpty()) {
            return checkStageResults(runId, stepIndices);
        }

        // Execute remaining steps in parallel
        List<CompletableFuture<StepExecutionResult>> futures = stepsToExecute.stream()
                .map(stepIndex -> CompletableFuture.supplyAsync(() ->
                        executeStep(runId, workflowId, workflowVersionId, stepIndex, steps, context, payload)
                ))
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(STAGE_EXECUTION_TIMEOUT_MS / 1000, java.util.concurrent.TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Stage execution timed out after " + STAGE_EXECUTION_TIMEOUT_MS + "ms");
        } catch (Exception e) {
            throw new RuntimeException("Stage execution failed: " + e.getMessage(), e);
        }

        return checkStageResults(runId, stepIndices);
    }

    /**
     * Execute a single step: resolve variables, dispatch, store output.
     */
    private StepExecutionResult executeStep(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            int stepIndex,
            List<StepDefinition> steps,
            WorkflowContext context,
            String payload
    ) {
        try {
            StepDefinition stepDef = steps.get(stepIndex);
            String stepType = normalize(stepDef.getType());

            // Save checkpoint BEFORE long-running step execution
            if (isLongRunningStep(stepType)) {
                checkpointManager.saveCheckpoint(runId, stepIndex, context);
            }

            // Handle WAIT_FOR_CALLBACK before creating a step record
            if ("WAIT_FOR_CALLBACK".equals(stepType)) {
                String correlationId = createWaitState(
                        runId, workflowId, workflowVersionId, stepIndex, stepDef.getConfig()
                );
                stepService.markStepWaiting(
                        runId, stepIndex, "WAIT_FOR_CALLBACK",
                        "Waiting for callback. correlationId=" + correlationId
                );
                return StepExecutionResult.success("");
            }

            // Create step record
            WorkflowRunStep step = stepService.startStep(runId, stepIndex, stepType);

            // Resolve variables in config
            Map<String, Object> resolvedConfig = new HashMap<>(stepDef.getConfig() != null ? stepDef.getConfig() : new HashMap<>());
            ContextVariableResolver.resolveVariablesInConfig(resolvedConfig, context);

            // Create new StepDefinition with resolved config
            StepDefinition resolvedStepDef = new StepDefinition(stepType, resolvedConfig);

            // Dispatch to executor
            StepExecutionResult result = dispatcher.dispatch(resolvedStepDef, payload);

            // Save result
            if (result.getStatus() == StepExecutionResult.Status.SUCCESS) {
                stepService.succeedStep(step, result.getOutput(), result.getOutput());
                context.setStepOutput(stepIndex, result.getOutput());
            } else {
                stepService.failStep(step, result.getError());
            }

            return result;

        } catch (Exception ex) {
            WorkflowRunStep existingStep = stepService.getStepByRunAndIndex(runId, stepIndex);
            if (existingStep != null && existingStep.getStatus() != WorkflowRunStep.Status.FAILED) {
                stepService.failStep(existingStep, ex.getMessage());
            }
            return StepExecutionResult.failure(ex.getMessage());
        }
    }

    /**
     * Check stage results: WAITING if any step is waiting, else COMPLETED.
     */
    private ExecutionOutcome checkStageResults(UUID runId, List<Integer> stageStepIndices) {
        List<WorkflowRunStep> stageSteps = stepService.getStepsForRun(runId).stream()
                .filter(s -> stageStepIndices.contains(s.getStepIndex()))
                .toList();

        boolean anyWaiting = stageSteps.stream()
                .anyMatch(s -> s.getStatus() == WorkflowRunStep.Status.WAITING);

        if (anyWaiting) {
            return ExecutionOutcome.WAITING;
        }

        return ExecutionOutcome.COMPLETED;
    }

    private String createWaitState(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            int stepIndex,
            Map<String, Object> config
    ) {
        var existing = workflowWaitStateRepository.findByRunId(runId);
        if (existing.isPresent()) {
            return existing.get().getCorrelationId();
        }

        String correlationId = null;
        if (config != null && config.get("correlationId") != null) {
            correlationId = String.valueOf(config.get("correlationId")).trim();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = runId + ":" + stepIndex;
        }

        WorkflowWaitState wait = new WorkflowWaitState();
        wait.setId(UUID.randomUUID());
        wait.setRunId(runId);
        wait.setWorkflowId(workflowId);
        wait.setWorkflowVersionId(workflowVersionId);
        wait.setStepIndex(stepIndex);
        wait.setCorrelationId(correlationId);
        wait.setStatus("WAITING");
        wait.setCreatedAt(OffsetDateTime.now());

        workflowWaitStateRepository.save(wait);
        return correlationId;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private boolean isLongRunningStep(String stepType) {
        if (stepType == null) return false;
        String t = stepType.trim().toUpperCase();
        return "HTTP_CALL".equals(t) || "WAIT_FOR_CALLBACK".equals(t)
                || "HTTP".equals(t) || "SLACK".equals(t)
                || "DATABASE".equals(t) || "S3".equals(t);
    }
}