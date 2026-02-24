package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

@Component
public class HttpCallStepExecutor implements StepExecutor {

    @Override
    public void execute(StepDefinition step, String payload) {
        String url = (String) step.getConfig().get("url");
        System.out.println("[HTTP CALL] Calling " + url);
        // real HTTP later
    }
}

