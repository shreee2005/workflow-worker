package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.ExecutionOutcome;
import com.workflow_worker.demo.engine.WorkflowExecutor;
import com.workflow_worker.demo.engine.lock.DistributedLockService;
import com.workflow_worker.demo.engine.retry.RetryService;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkflowTaskConsumer {
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b([1-5][0-9]{2})\\b");

    private final WorkflowRunStepService stepService;
    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRunService workflowRunService;

    public WorkflowTaskConsumer(
            WorkflowRunStepService stepService,
            DistributedLockService distributedLockService,
            WorkflowRunService workflowRunService,
            RetryService retryService,
            WorkflowExecutor workflowExecutor
    ) {
        this.stepService = stepService;
        this.distributedLockService = distributedLockService;
        this.workflowRunService = workflowRunService;
        this.retryService = retryService;
        this.workflowExecutor = workflowExecutor;
    }

    @RabbitListener(queues = "workflow.tasks", containerFactory = "rabbitListenerContainerFactory")
    @WithSpan("queue.consume")
    public void handleTask(WorkflowJobMessage message) {
        UUID runId = message.getRunId();
        UUID workflowId = message.getWorkflowId();
        UUID workflowVersionId = message.getWorkflowVersionId();

        Span currentSpan = Span.current();
        currentSpan.setAttribute("run.id", runId.toString());
        currentSpan.setAttribute("workflow.id", workflowId.toString());
        currentSpan.setAttribute("message.attempt", message.getAttempt());

        if (!distributedLockService.acquire(runId)) {
            currentSpan.setAttribute("lock.acquired", false);
            return;
        }
        currentSpan.setAttribute("lock.acquired", true);

        try {
            WorkflowRun run = workflowRunService.getRun(runId);
            currentSpan.setAttribute("run.status.before", run.getStatus().toString());

            if (workflowVersionId == null) {
                workflowVersionId = run.getWorkflowVersionId();
            }

            boolean isRetryMessage = message.getAttempt() > 0;

            if (run.getStatus() == WorkflowRun.Status.QUEUED ||
                    run.getStatus() == WorkflowRun.Status.RETRYING ||
                    run.getStatus() == WorkflowRun.Status.WAITING ||
                    (run.getStatus() == WorkflowRun.Status.RUNNING && isRetryMessage)) {

                if (run.getStatus() == WorkflowRun.Status.RETRYING || isRetryMessage) {
                    stepService.resetFailedStepsForRetry(runId);
                }

                if (run.getStatus() != WorkflowRun.Status.RUNNING) {
                    workflowRunService.transition(runId, WorkflowRun.Status.RUNNING);
                }
            }

            ExecutionOutcome outcome = workflowExecutor.executeRun(
                    runId, workflowId, workflowVersionId, message.getPayload()
            );
            currentSpan.setAttribute("execution.outcome", outcome.toString());

            if (outcome == ExecutionOutcome.WAITING) {
                workflowRunService.transition(runId, WorkflowRun.Status.WAITING);
                currentSpan.setAttribute("run.status.after", "WAITING");
                return;
            }

            boolean hasFailed = stepService.hasFailedStep(runId);
            if (hasFailed) {
                workflowRunService.transition(runId, WorkflowRun.Status.FAILED);
                currentSpan.setAttribute("run.status.after", "FAILED");
            } else {
                workflowRunService.transition(runId, WorkflowRun.Status.SUCCEEDED);
                currentSpan.setAttribute("run.status.after", "SUCCEEDED");
            }

        } catch (Exception ex) {
            currentSpan.recordException(ex);
            WorkflowRun latest = workflowRunService.getRun(runId);

            if (latest.getStatus() == WorkflowRun.Status.WAITING) return;
            boolean retryable = isRetryableError(ex.getMessage());
            retryService.handleFailure(runId, workflowId, workflowVersionId, message.getPayload(), ex , retryable);
        } finally {
            distributedLockService.release(runId);
        }
    }

    private boolean isRetryableError(String msg) {
        if (msg == null) return true;
        String m = msg.toLowerCase();

        // common non-retryable validation/config errors
        if (m.contains("missing 'url'")) return false;
        if (m.contains("invalid")) return false;
        if (m.contains("bad request")) return false;

        // Any explicit HTTP status code:
        // 4xx (except 429) => non-retryable, 429/5xx => retryable
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(m);
        if (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            if (code == 429) return true;
            if (code >= 500) return true;
            if (code >= 400) return false;
        }

        // retryable transport/transient failures
        if (m.contains("connection") || m.contains("timeout") || m.contains("refused") || m.contains("i/o error"))
            return true;

        // keep explicit retry-test behavior retryable
        if (m.contains("intentional failure for retry test")) return true;

        return true; // default to retryable
    }
}
