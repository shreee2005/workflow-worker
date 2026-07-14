package com.workflow_worker.demo.executers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@PluginExecutor("HTTP_CALL")
public class HttpExecutor implements WorkflowPlugin {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "HTTP Call Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<String, String> getInputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("url", "HTTP request destination URL (required)");
        schema.put("method", "HTTP method: GET, POST, PUT, DELETE (default: POST)");
        schema.put("body", "Request body (optional)");
        schema.put("headers", "JSON string representing custom headers (optional)");
        schema.put("authType", "Authentication type: NONE, BASIC, BEARER (default: NONE)");
        schema.put("authUsername", "Username for BASIC authentication");
        schema.put("authPassword", "Password for BASIC authentication");
        schema.put("authToken", "Token for BEARER authentication");
        return schema;
    }

    @Override
    public Map<String, String> getOutputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("response", "The response body returned by the HTTP call");
        return schema;
    }

    @Override
    public void validate(StepDefinition step) throws Exception {
        Map<String, Object> config = step.getConfig();
        if (config == null || config.get("url") == null || String.valueOf(config.get("url")).isBlank()) {
            throw new IllegalArgumentException("HTTP_CALL step missing 'url' config");
        }
        String method = config.get("method") == null ? "POST" : String.valueOf(config.get("method")).trim().toUpperCase();
        try {
            HttpMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        // Validate JSON headers if provided
        Object headersObj = config.get("headers");
        if (headersObj != null && !String.valueOf(headersObj).isBlank()) {
            try {
                objectMapper.readValue(String.valueOf(headersObj), new TypeReference<Map<String, String>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Headers config is not a valid JSON object map: " + e.getMessage());
            }
        }
    }

    @Override
    public String execute(StepDefinition step, String payload) throws Exception {
        Map<String, Object> config = step.getConfig();
        String url = String.valueOf(config.get("url")).trim();
        String method = config.get("method") == null ? "POST" : String.valueOf(config.get("method")).trim().toUpperCase();
        String body = config.get("body") != null ? String.valueOf(config.get("body")) : payload;

        HttpMethod httpMethod = HttpMethod.valueOf(method);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Process custom headers
        Object headersObj = config.get("headers");
        if (headersObj != null && !String.valueOf(headersObj).isBlank()) {
            Map<String, String> customHeaders = objectMapper.readValue(
                    String.valueOf(headersObj), new TypeReference<Map<String, String>>() {}
            );
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                headers.set(entry.getKey(), entry.getValue());
            }
        }

        // 2. Process authorization
        String authType = config.get("authType") == null ? "NONE" : String.valueOf(config.get("authType")).trim().toUpperCase();
        if ("BASIC".equals(authType)) {
            String username = config.get("authUsername") != null ? String.valueOf(config.get("authUsername")) : "";
            String password = config.get("authPassword") != null ? String.valueOf(config.get("authPassword")) : "";
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.US_ASCII));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        } else if ("BEARER".equals(authType)) {
            String token = config.get("authToken") != null ? String.valueOf(config.get("authToken")) : "";
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        try {
            HttpEntity<String> req = new HttpEntity<>(body == null ? "" : body, headers);
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
