package com.workflow_worker.demo.worker;


import com.workflow_worker.demo.workflow.StepDefinition;

public interface StepExecutor {
    void execute(StepDefinition step, String payload) throws Exception;
}
