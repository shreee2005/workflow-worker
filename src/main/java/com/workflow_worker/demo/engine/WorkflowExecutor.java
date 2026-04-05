package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.entity.WorkflowRun;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WorkflowExecutor {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowWaitStateRepository workflowWaitStateRepository;
    private final WorkflowRunStepService stepService;
    private final StepDispatcher dispatcher;
    private final WorkflowRunService workflowRunService;

    public WorkflowExecutor(
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowWaitStateRepository workflowWaitStateRepository,
            WorkflowRunStepService stepService,
            StepDispatcher dispatcher,
            WorkflowRunService workflowRunService
    ) {
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowWaitStateRepository = workflowWaitStateRepository;
        this.stepService = stepService;
        this.dispatcher = dispatcher;
        this.workflowRunService = workflowRunService;
    }

    @WithSpan("workflow.execute")
    public ExecutionOutcome executeRun(
            @SpanAttribute("run.id") UUID runId,
            @SpanAttribute("workflow.id") UUID workflowId,
            @SpanAttribute("workflow.version.id") UUID workflowVersionId,
            String payload
    ) {
        Span currentSpan = Span.current();

        WorkflowVersion wfVersion = workflowVersionRepository.findById(workflowVersionId)
                .orElseThrow(() -> new RuntimeException("Workflow version not found"));

        if (!wfVersion.getWorkflowId().equals(workflowId)) {
            throw new RuntimeException("Workflow version does not belong to workflow");
        }

        List<StepDefinition> steps = WorkflowSpecParser.parse(wfVersion.getSpec());
        currentSpan.setAttribute("workflow.total.steps", steps.size());

        int nextStepIndex = stepService.getNextPendingStepIndex(runId);
        currentSpan.setAttribute("workflow.next.step.index", nextStepIndex);

        System.out.println("[EXECUTOR] runId=" + runId + " nextStepIndex=" + nextStepIndex);
        if (nextStepIndex == Integer.MAX_VALUE) return ExecutionOutcome.WAITING;
        if (nextStepIndex == -1) return ExecutionOutcome.COMPLETED;
        if (nextStepIndex >= steps.size()) return ExecutionOutcome.COMPLETED;

        StepDefinition stepDef = steps.get(nextStepIndex);
        String stepType = normalize(stepDef.getType());
        currentSpan.setAttribute("step.type", stepType);
        currentSpan.setAttribute("step.index", nextStepIndex);

        if ("WAIT_FOR_CALLBACK".equals(stepType)) {
            String correlationId = createWaitState(runId, workflowId, workflowVersionId, nextStepIndex, stepDef.getConfig());

            stepService.markStepWaiting(runId, nextStepIndex, "WAIT_FOR_CALLBACK",
                    "Waiting for callback. correlationId=" + correlationId);

            workflowRunService.transition(runId, WorkflowRun.Status.WAITING);
            return ExecutionOutcome.WAITING;
        }

        WorkflowRunStep step = stepService.startStep(runId, nextStepIndex, stepType);
        StepExecutionResult result = dispatcher.dispatch(stepDef, payload);

        if (result.getStatus() == StepExecutionResult.Status.SUCCESS) {
            stepService.succeedStep(step, result.getOutput());
            return ExecutionOutcome.COMPLETED;
        }

        stepService.failStep(step, result.getError());
        throw new RuntimeException(result.getError() == null ? "Step execution failed" : result.getError());
    }

    private String createWaitState(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            int stepIndex,
            Map<String, Object> config
    ) {
        var existing = workflowWaitStateRepository.findByRunId(runId);
        if (existing.isPresent()) return existing.get().getCorrelationId();

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
}