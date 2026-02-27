package com.workflow_worker.demo.worker;
import com.workflow_worker.demo.engine.WorkflowStepEngine;
import com.workflow_worker.demo.engine.lock.DistributedLockService;
import com.workflow_worker.demo.engine.retry.RetryService;
import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.repository.WorkflowRepository;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
            DistributedLockService distributedLockService, WorkflowRepository workflowRepository,
            WorkflowRunService workflowRunService,
            WorkflowRunStepService stepService,
            StepExecutorRegistry executorRegistry, RetryService retryService, WorkflowStepEngine stepEngine,
            WorkflowMetrics metrics,
            RabbitTemplate rabbitTemplate
    ) {
        this.distributedLockService = distributedLockService;

        this.workflowRunService = workflowRunService;
        this.retryService = retryService;
        this.stepEngine = stepEngine;
        this.metrics = metrics;

    }

    @RabbitListener(queues = "workflow.tasks")
    public void handleTask(WorkflowJobMessage message) {

        UUID runId = message.getRunId();
        UUID workflowId = message.getWorkflowId();

        if (!distributedLockService.acquire(runId)) {
            return;
        }

        try {
            workflowRunService.markRunning(runId);
            metrics.runsStarted.increment();
        } catch (IllegalStateException e) {
            return;
        }
        try{
            stepEngine.executeSteps(
                    runId,
                    workflowId,
                    message.getPayload()
            );

            workflowRunService.markSucceeded(runId);
            metrics.runsSucceeded.increment();
        }
        catch (Exception ex) {
            retryService.handleFailure(
                    runId,
                    workflowId,
                    message.getPayload(),
                    ex
            );
        }
        finally {
            distributedLockService.release(runId);
        }
    }
}
