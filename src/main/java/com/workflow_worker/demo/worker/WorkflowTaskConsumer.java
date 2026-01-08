package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.entity.Workflow;
import com.workflow_worker.demo.repository.WorkflowRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class WorkflowTaskConsumer {

    private final WorkflowRepository workflowRepository;

    public WorkflowTaskConsumer(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @RabbitListener(queues = "workflow.tasks")
    public void handleTask(Map<String, Object> message) {
        UUID workflowId = UUID.fromString((String) message.get("workflowId"));
        String payload = (String) message.get("payload");

        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found for job"));

        System.out.println("Executing workflow " + wf.getName() + " with payload " + payload);

        // TODO:
        // - parse wf.getSpec()
        // - execute each step sequentially
        // - write run + step logs
    }
}
