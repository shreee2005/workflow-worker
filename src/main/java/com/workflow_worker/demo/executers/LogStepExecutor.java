package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LogStepExecutor implements StepExecutor {

    @Override
    public String execute(
            StepDefinition step,
            String payload
    ) {

        Map<String, Object> config = step.getConfig();

        String message = "LOG";

        if (config != null && config.containsKey("message")) {
            message = config.get("message").toString();
        }

        String logOutput =
                "[LOG STEP] message=" + message +
                        " payload=" + payload;

        System.out.println(logOutput);

        /*
        Used for retry testing.
        If message == "fail" the step throws an error
        and RetryService will schedule retry.
        */
        if ("fail".equalsIgnoreCase(message)) {
            throw new RuntimeException(
                    "Intentional failure for retry test"
            );
        }

        return logOutput;
    }
}