package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@PluginExecutor("SLACK_NOTIFIER")
public class SlackNotifier implements WorkflowPlugin {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "Slack Notifier";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<String, String> getInputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("webhookUrl", "Slack incoming webhook integration URL (required)");
        schema.put("message", "Text content of the notification message (required)");
        return schema;
    }

    @Override
    public Map<String, String> getOutputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("status", "Result status of the Slack post (e.g. SUCCESS)");
        return schema;
    }

    @Override
    public void validate(StepDefinition step) throws Exception {
        Map<String, Object> config = step.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("SlackNotifier missing configuration");
        }
        if (config.get("webhookUrl") == null || String.valueOf(config.get("webhookUrl")).isBlank()) {
            throw new IllegalArgumentException("SlackNotifier missing 'webhookUrl' configuration");
        }
        if (config.get("message") == null || String.valueOf(config.get("message")).isBlank()) {
            throw new IllegalArgumentException("SlackNotifier missing 'message' configuration");
        }
    }

    @Override
    public String execute(StepDefinition step, String payload) throws Exception {
        Map<String, Object> config = step.getConfig();
        String webhookUrl = String.valueOf(config.get("webhookUrl")).trim();
        String messageText = String.valueOf(config.get("message"));

        // Format body as standard Slack incoming webhook payload: {"text": "..."}
        Map<String, String> slackPayload = new HashMap<>();
        slackPayload.put("text", messageText);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(slackPayload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Slack notification failed with HTTP status: " + response.getStatusCode().value());
        }

        return "{\"status\": \"SUCCESS\"}";
    }
}
