package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.messaging.WorkflowDlqMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDlqConsumer {

    @RabbitListener(queues = "workflow.tasks.dlq")
    public void handleDlq(WorkflowDlqMessage msg) {
        System.err.println(
                "[DLQ] runId=" + msg.getRunId()
                        + " attempt=" + msg.getAttempt()
                        + " error=" + msg.getError()
        );
    }
}
