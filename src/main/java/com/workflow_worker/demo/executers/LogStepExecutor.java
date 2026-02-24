package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

@Component
public class LogStepExecutor implements StepExecutor {

    @Override
    public void execute(StepDefinition step, String payload) {
        Object msg = step.getConfig().get("message");
        System.out.println("[LOG STEP] " + msg + " payload=" + payload);
    }
}

