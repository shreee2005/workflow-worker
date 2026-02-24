package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.WorkflowStepEngine;
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
import java.util.List;
import java.util.UUID;

@Component
public class WorkflowTaskConsumer {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepEngine stepEngine;
    private final WorkflowRunService workflowRunService;
    private final WorkflowRunStepService stepService;
    private final WorkflowMetrics metrics;
    private final RabbitTemplate rabbitTemplate;

    public WorkflowTaskConsumer(
            WorkflowRepository workflowRepository,
            WorkflowRunService workflowRunService,
            WorkflowRunStepService stepService,
            StepExecutorRegistry executorRegistry, WorkflowStepEngine stepEngine,
            WorkflowMetrics metrics,
            RabbitTemplate rabbitTemplate
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRunService = workflowRunService;
        this.stepService = stepService;
        this.stepEngine = stepEngine;
        this.metrics = metrics;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "workflow.tasks")
    public void handleTask(WorkflowJobMessage message) {

        UUID runId = message.getRunId();
        UUID workflowId = message.getWorkflowId();

        // 1️⃣ Idempotency gate
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

            WorkflowRun run = workflowRunService.getRun(runId);

            // 6️⃣ Retry path
            if (workflowRunService.canRetry(run)) {

                workflowRunService.incrementAttempt(runId);
                workflowRunService.markQueuedForRetry(runId);

                metrics.runsRetried.increment();

                WorkflowJobMessage retryMsg = new WorkflowJobMessage();
                retryMsg.setRunId(runId);
                retryMsg.setWorkflowId(workflowId);
                retryMsg.setPayload(message.getPayload());
                // use DB value that was just persisted
                retryMsg.setAttempt(run.getAttempt());

                // re‑queue to the same queue as before
                rabbitTemplate.convertAndSend("workflow.tasks", retryMsg);

                return;
            }

            // 7️⃣ DLQ path (terminal failure)
            workflowRunService.markFailed(runId, ex.getMessage());
            metrics.runsFailed.increment();
            metrics.runsDeadLettered.increment();

            WorkflowDlqMessage dlqMsg = new WorkflowDlqMessage();
            dlqMsg.setRunId(runId);
            dlqMsg.setWorkflowId(workflowId);
            dlqMsg.setAttempt(run.getAttempt());
            dlqMsg.setError(ex.getMessage());
            dlqMsg.setFailedAt(OffsetDateTime.now());

            rabbitTemplate.convertAndSend("workflow.tasks.dlq", dlqMsg);
        }
    }
}
