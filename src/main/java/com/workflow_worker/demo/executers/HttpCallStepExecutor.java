package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;

@Component
public class HttpCallStepExecutor implements StepExecutor {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getType() {
        return "HTTP_CALL";
    }

    @Override
    public String execute(StepDefinition step, String payload) {
        Map<String, Object> config = step.getConfig();

        if (config == null || config.get("url") == null) {
            throw new RuntimeException("HTTP_CALL step missing 'url' config");
        }

        String url = String.valueOf(config.get("url")).trim();
        String method = config.get("method") == null ? "POST" : String.valueOf(config.get("method")).trim().toUpperCase();

        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> req = new HttpEntity<>(payload == null ? "{}" : payload, headers);
            ResponseEntity<String> res = restTemplate.exchange(url, httpMethod, req, String.class);

            if (!res.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP_CALL non-2xx status: " + res.getStatusCode().value());
            }

            return res.getBody() == null ? "HTTP_CALL_SUCCESS" : res.getBody();

        } catch (ResourceAccessException e) {
            throw new RuntimeException("HTTP_CALL connection error: " + e.getMessage(), e);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("HTTP_CALL status error: " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new RuntimeException("HTTP_CALL failed: " + e.getMessage(), e);
        }
    }
}