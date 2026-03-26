package com.workflow_worker.demo.worker;


import com.workflow_worker.demo.workflow.StepDefinition;

public interface StepExecutor {
    String getType();

    String execute(StepDefinition step, String payload) throws Exception;
}
