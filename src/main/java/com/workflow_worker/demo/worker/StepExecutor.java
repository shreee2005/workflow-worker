package com.workflow_worker.demo.worker;


import com.workflow_worker.demo.workflow.StepDefinition;

public interface StepExecutor {
    String execute(StepDefinition step, String payload) throws Exception;
}
