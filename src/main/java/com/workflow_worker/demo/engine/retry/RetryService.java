package com.workflow_worker.demo.engine.retry;

import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.messaging.WorkflowDlqMessage;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.repository.WorkflowRunRepository;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.worker.WorkflowMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RetryService {

    private final WorkflowRunService workflowRunService;
    private final WorkflowRunRepository workflowRunRepository;
    private final RabbitTemplate rabbitTemplate;
    private final WorkflowMetrics metrics;

    public RetryService(
            WorkflowRunService workflowRunService,
            WorkflowRunRepository workflowRunRepository,
            RabbitTemplate rabbitTemplate,
            WorkflowMetrics metrics
    ) {
        this.workflowRunService = workflowRunService;
        this.workflowRunRepository = workflowRunRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.metrics = metrics;
    }

    @WithSpan("workflow.retry")
    public void handleFailure(
            @SpanAttribute("run.id") UUID runId,
            @SpanAttribute("workflow.id") UUID workflowId,
            @SpanAttribute("workflow.version.id") UUID workflowVersionId,
            Object payload,
            Exception ex,
            boolean retryable
    ) {
        Span.current().setAttribute("retry.scheduled", retryable);
        Span.current().setAttribute("error.category", retryable ? "transient_or_server" : "non_retryable");
        if (!retryable) {
            Span.current().recordException(ex);
            Span.current().setStatus(StatusCode.ERROR, "workflow_dead_lettered");
        }

        WorkflowRun run = workflowRunService.getRun(runId);
        Span.current().setAttribute("retry.attempt", run.getAttempt());
        UUID effectiveVersionId = workflowVersionId != null ? workflowVersionId : run.getWorkflowVersionId();

        if (run.getStatus() == WorkflowRun.Status.WAITING) return;
        if (run.getStatus() == WorkflowRun.Status.FAILED || run.getStatus() == WorkflowRun.Status.SUCCEEDED) return;

        if (!retryable) {
            run.setErrorMessage(ex.getMessage());
            workflowRunRepository.save(run);
            workflowRunService.transition(runId, WorkflowRun.Status.FAILED);
            workflowRunRepository.markDeadLettered(runId);
            metrics.runsDeadLettered.increment();

            WorkflowDlqMessage dlqMsg = new WorkflowDlqMessage();
            dlqMsg.setRunId(runId);
            dlqMsg.setWorkflowId(workflowId);
            dlqMsg.setAttempt(run.getAttempt());
            dlqMsg.setError(ex.getMessage());
            dlqMsg.setFailedAt(OffsetDateTime.now());
            rabbitTemplate.convertAndSend("", "workflow.tasks.dlq", dlqMsg);
            return;
        }

        if (workflowRunService.canRetry(run)) {

            workflowRunService.incrementAttempt(runId);
            WorkflowRun updatedRun = workflowRunService.getRun(runId);

            try {
                workflowRunService.transition(runId, WorkflowRun.Status.RETRYING);
            } catch (Exception ignored) {
                // Backward-compatible fallback: some DBs still have older status CHECK constraints.
                // Retry scheduling still proceeds and consumer can continue based on message attempt.
            }

            int attempt = updatedRun.getAttempt();
            Span.current().setAttribute("retry.attempt", attempt);

            WorkflowJobMessage retryMsg = new WorkflowJobMessage();
            retryMsg.setRunId(runId);
            retryMsg.setWorkflowId(workflowId);
            retryMsg.setWorkflowVersionId(effectiveVersionId);
            retryMsg.setPayload((String) payload);
            retryMsg.setAttempt(attempt);

            String retryQueue = resolveRetryQueue(attempt);

            rabbitTemplate.convertAndSend(
                    "",
                    retryQueue,
                    retryMsg
            );

            metrics.runsRetried.increment();
            return;
        }

        workflowRunService.transition(runId, WorkflowRun.Status.FAILED);
        workflowRunRepository.markDeadLettered(runId);
        metrics.runsDeadLettered.increment();

        WorkflowDlqMessage dlqMsg = new WorkflowDlqMessage();
        dlqMsg.setRunId(runId);
        dlqMsg.setWorkflowId(workflowId);
        dlqMsg.setAttempt(run.getAttempt());
        dlqMsg.setError(ex.getMessage());
        dlqMsg.setFailedAt(OffsetDateTime.now());

        rabbitTemplate.convertAndSend(
                "",
                "workflow.tasks.dlq",
                dlqMsg
        );
    }

    private String resolveRetryQueue(int attempt) {
        return switch (attempt) {
            case 1 -> "workflow.retry.5s";
            case 2 -> "workflow.retry.10s";
            case 3 -> "workflow.retry.20s";
            default -> "workflow.retry.40s";
        };
    }
}
