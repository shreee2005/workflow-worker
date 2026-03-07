package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.WorkflowStepEngine;
import com.workflow_worker.demo.engine.lock.DistributedLockService;
import com.workflow_worker.demo.engine.retry.RetryService;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.service.WorkflowRunService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class WorkflowTaskConsumer {

    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final WorkflowStepEngine stepEngine;
    private final WorkflowRunService workflowRunService;
    private final WorkflowMetrics metrics;

    public WorkflowTaskConsumer(
            DistributedLockService distributedLockService,
            WorkflowRunService workflowRunService,
            RetryService retryService,
            WorkflowStepEngine stepEngine,
            WorkflowMetrics metrics
    ) {
        this.distributedLockService = distributedLockService;
        this.workflowRunService = workflowRunService;
        this.retryService = retryService;
        this.stepEngine = stepEngine;
        this.metrics = metrics;
    }

    @RabbitListener(
            queues = "workflow.tasks",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleTask(WorkflowJobMessage message) {

        UUID runId = message.getRunId();
        UUID workflowId = message.getWorkflowId();

        if (!distributedLockService.acquire(runId)) {
            return;
        }

        try {

            WorkflowRun run = workflowRunService.getRun(runId);

            if (run.getStatus() == WorkflowRun.Status.QUEUED ||
                    run.getStatus() == WorkflowRun.Status.RETRYING) {

                workflowRunService.transition(runId, WorkflowRun.Status.RUNNING);
                metrics.runsStarted.increment();
            }

            stepEngine.executeSteps(
                    runId,
                    workflowId,
                    message.getPayload()
            );

            workflowRunService.transition(runId, WorkflowRun.Status.SUCCEEDED);
            metrics.runsSucceeded.increment();

        } catch (Exception ex) {

            retryService.handleFailure(
                    runId,
                    workflowId,
                    message.getPayload(),
                    ex
            );

        } finally {
            distributedLockService.release(runId);
        }
    }
}