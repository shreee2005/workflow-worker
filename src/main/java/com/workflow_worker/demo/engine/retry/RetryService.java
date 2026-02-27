package com.workflow_worker.demo.engine.retry;

import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.messaging.WorkflowDlqMessage;
import com.workflow_worker.demo.messaging.WorkflowJobMessage;
import com.workflow_worker.demo.service.WorkflowRunService;
import com.workflow_worker.demo.worker.WorkflowMetrics;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RetryService {

    private final WorkflowRunService workflowRunService;
    private final RabbitTemplate rabbitTemplate;
    private final WorkflowMetrics metrics;

    public RetryService(
            WorkflowRunService workflowRunService,
            RabbitTemplate rabbitTemplate,
            WorkflowMetrics metrics
    ) {
        this.workflowRunService = workflowRunService;
        this.rabbitTemplate = rabbitTemplate;
        this.metrics = metrics;
    }

    public void handleFailure(
            UUID runId,
            UUID workflowId,
            Object payload,
            Exception ex
    ) {

        WorkflowRun run = workflowRunService.getRun(runId);

        if (workflowRunService.canRetry(run)) {

            workflowRunService.incrementAttempt(runId);
            workflowRunService.transition(runId , WorkflowRun.Status.RETRYING);

            metrics.runsRetried.increment();

            WorkflowJobMessage retryMsg = new WorkflowJobMessage();
            retryMsg.setRunId(runId);
            retryMsg.setWorkflowId(workflowId);
            retryMsg.setPayload((String) payload);
            retryMsg.setAttempt(run.getAttempt());

            rabbitTemplate.convertAndSend("workflow.tasks", retryMsg);
            return;
        }

        workflowRunService.transition(runId, WorkflowRun.Status.FAILED);
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