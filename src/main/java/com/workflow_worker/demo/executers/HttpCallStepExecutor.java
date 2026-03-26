package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HttpCallStepExecutor implements StepExecutor {

    @Override
    public String getType() {
        return "HTTP_CALL";
    }

    @Override
    public String execute(StepDefinition step, String payload) {
        Map<String, Object> config = step.getConfig();

        if (config == null || !config.containsKey("url") || config.get("url") == null) {
            throw new RuntimeException("HTTP_CALL step missing 'url' config");
        }

        String url = String.valueOf(config.get("url")).trim();

        System.out.println("[HTTP_CALL] Calling " + url + " payload=" + payload);

        // TODO: Replace with RestTemplate/WebClient real call
        return "HTTP_CALL_SUCCESS";
    }
}