package com.workflow_worker.demo.worker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMetrics {

    public final Counter runsStarted;
    public final Counter runsSucceeded;
    public final Counter runsFailed;
    public final Counter runsRetried;
    public final Counter runsDeadLettered;

    public WorkflowMetrics(MeterRegistry registry) {
        this.runsStarted = registry.counter("workflow.runs.started");
        this.runsSucceeded = registry.counter("workflow.runs.succeeded");
        this.runsFailed = registry.counter("workflow.runs.failed");
        this.runsRetried = registry.counter("workflow.runs.retried");
        this.runsDeadLettered = registry.counter("workflow.runs.dlq");
    }
}

