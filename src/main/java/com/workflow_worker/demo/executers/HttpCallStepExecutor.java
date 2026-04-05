package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
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
    @WithSpan("step.http_call")
    public String execute(StepDefinition step, String payload) {
        Map<String, Object> config = step.getConfig();

        if (config == null || config.get("url") == null) {
            throw new RuntimeException("HTTP_CALL step missing 'url' config");
        }

        String url = String.valueOf(config.get("url")).trim();
        String method = config.get("method") == null ? "POST" : String.valueOf(config.get("method")).trim().toUpperCase();

        Span currentSpan = Span.current();
        currentSpan.setAttribute("http.url", url);
        currentSpan.setAttribute("http.method", method);

        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> req = new HttpEntity<>(payload == null ? "{}" : payload, headers);
            ResponseEntity<String> res = restTemplate.exchange(url, httpMethod, req, String.class);

            currentSpan.setAttribute("http.status_code", res.getStatusCode().value());

            if (!res.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP_CALL non-2xx status: " + res.getStatusCode().value());
            }

            return res.getBody() == null ? "HTTP_CALL_SUCCESS" : res.getBody();

        } catch (ResourceAccessException e) {
            currentSpan.recordException(e);
            currentSpan.setAttribute("error.type", "connection");
            throw new RuntimeException("HTTP_CALL connection error: " + e.getMessage(), e);
        } catch (HttpStatusCodeException e) {
            currentSpan.recordException(e);
            currentSpan.setAttribute("error.type", "http_status");
            currentSpan.setAttribute("http.status_code", e.getStatusCode().value());
            throw new RuntimeException("HTTP_CALL status error: " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            currentSpan.recordException(e);
            currentSpan.setAttribute("error.type", "unknown");
            throw new RuntimeException("HTTP_CALL failed: " + e.getMessage(), e);
        }
    }
}