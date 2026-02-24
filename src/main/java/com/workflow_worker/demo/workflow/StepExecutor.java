package com.workflow_worker.demo.workflow;

public interface StepExecutor {
    void execute(StepDefinition step, String payload) throws Exception;
}

