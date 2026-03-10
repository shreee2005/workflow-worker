package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HttpCallStepExecutor implements StepExecutor {

    @Override
    public String execute(
            StepDefinition step,
            String payload
    ) {

        Map<String, Object> config = step.getConfig();

        if (config == null || !config.containsKey("url")) {
            throw new RuntimeException("HTTP step missing 'url' config");
        }

        String url = config.get("url").toString();

        System.out.println("[HTTP CALL] Calling " + url);

        // future: RestTemplate / WebClient

        return "HTTP_CALL_SUCCESS";
    }
}