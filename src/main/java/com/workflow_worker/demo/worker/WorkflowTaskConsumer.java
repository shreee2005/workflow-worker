package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.WorkflowStepEngine;
import com.workflow_worker.demo.engine.lock.DistributedLockService;
import com.workflow_worker.demo.engine.retry.RetryService;
import com.workflow_worker.demo.entity.Workflow;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.messaging.WorkflowDlqMessage;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.repository.WorkflowRepository;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class WorkflowTaskConsumer {
    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStepEngine stepEngine;
    private final WorkflowRunService workflowRunService;
    private final WorkflowRunStepService stepService;
    private final WorkflowMetrics metrics;
    private final RabbitTemplate rabbitTemplate;

    public WorkflowTaskConsumer(
            DistributedLockService distributedLockService, WorkflowRepository workflowRepository,
            WorkflowRunService workflowRunService,
            WorkflowRunStepService stepService,
            StepExecutorRegistry executorRegistry, RetryService retryService, WorkflowStepEngine stepEngine,
            WorkflowMetrics metrics,
            RabbitTemplate rabbitTemplate
    ) {
        this.distributedLockService = distributedLockService;
        this.workflowRepository = workflowRepository;
        this.workflowRunService = workflowRunService;
        this.stepService = stepService;
        this.retryService = retryService;
        this.stepEngine = stepEngine;
        this.metrics = metrics;
        this.rabbitTemplate = rabbitTemplate;
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
            // duplicate / already finished – do nothing, just exit
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
            // Always release distributed lock
            distributedLockService.release(runId);
        }
    }
}
