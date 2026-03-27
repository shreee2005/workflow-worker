package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.ExecutionOutcome;
import com.workflow_worker.demo.engine.WorkflowExecutor;
import com.workflow_worker.demo.engine.lock.DistributedLockService;
import com.workflow_worker.demo.engine.retry.RetryService;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkflowTaskConsumer {
    private final WorkflowRunStepService stepService;
    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRunService workflowRunService;

    public WorkflowTaskConsumer(
            WorkflowRunStepService stepService, DistributedLockService distributedLockService,
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
    public void handleTask(WorkflowJobMessage message) {
        UUID runId = message.getRunId();
        UUID workflowId = message.getWorkflowId();
        UUID workflowVersionId = message.getWorkflowVersionId();

        if (!distributedLockService.acquire(runId)) return;

        try {
            WorkflowRun run = workflowRunService.getRun(runId);

            if (run.getStatus() == WorkflowRun.Status.QUEUED ||
                    run.getStatus() == WorkflowRun.Status.RETRYING ||
                    run.getStatus() == WorkflowRun.Status.WAITING) {
                workflowRunService.transition(runId, WorkflowRun.Status.RUNNING);
            }

            ExecutionOutcome outcome = workflowExecutor.executeRun(
                    runId, workflowId, workflowVersionId, message.getPayload()
            );

            if (outcome == ExecutionOutcome.WAITING) {
                // IMPORTANT: keep run consistent
                workflowRunService.transition(runId, WorkflowRun.Status.WAITING);
                return;
            }
            System.out.println("[CONSUMER] runId=" + runId + " outcome=" + outcome);
            System.out.println("[CONSUMER] runId=" + runId + " hasFailed=" + stepService.hasFailedStep(runId));

            boolean hasFailed = stepService.hasFailedStep(runId);
            if (hasFailed) {
                workflowRunService.transition(runId, WorkflowRun.Status.FAILED);
            } else {
                workflowRunService.transition(runId, WorkflowRun.Status.SUCCEEDED);
            }

        } catch (Exception ex) {
            WorkflowRun latest = workflowRunService.getRun(runId);

            // never retry if already waiting
            if (latest.getStatus() == WorkflowRun.Status.WAITING) return;

            retryService.handleFailure(runId, workflowId, message.getPayload(), ex);
        } finally {
            distributedLockService.release(runId);
        }
    }
}