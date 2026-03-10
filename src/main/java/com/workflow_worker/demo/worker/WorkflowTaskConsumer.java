package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.WorkflowExecutor;
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
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRunService workflowRunService;

    public WorkflowTaskConsumer(
            DistributedLockService distributedLockService,
            WorkflowRunService workflowRunService,
            RetryService retryService,
            WorkflowExecutor workflowExecutor
    ) {
        this.distributedLockService = distributedLockService;
        this.workflowRunService = workflowRunService;
        this.retryService = retryService;
        this.workflowExecutor = workflowExecutor;
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

                workflowRunService.transition(
                        runId,
                        WorkflowRun.Status.RUNNING
                );
            }

            workflowExecutor.executeRun(
                    runId,
                    workflowId,
                    message.getPayload()
            );

            workflowRunService.transition(
                    runId,
                    WorkflowRun.Status.SUCCEEDED
            );

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