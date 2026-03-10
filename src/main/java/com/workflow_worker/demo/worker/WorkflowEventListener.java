package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.engine.events.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventListener {

    private final WorkflowMetrics metrics;

    public WorkflowEventListener(WorkflowMetrics metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        metrics.runsStarted.increment();
    }

    @EventListener
    public void onWorkflowSuccess(WorkflowSucceededEvent event) {
        metrics.runsSucceeded.increment();
    }

    @EventListener
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        metrics.runsFailed.increment();
    }
}