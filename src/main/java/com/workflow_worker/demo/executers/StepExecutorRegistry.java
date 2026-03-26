package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StepExecutorRegistry {

    private final Map<String, StepExecutor> executors = new HashMap<>();

    public StepExecutorRegistry(List<StepExecutor> executorList) {
        for (StepExecutor executor : executorList) {
            String key = normalize(executor.getType());

            if (key.isBlank()) {
                throw new RuntimeException("StepExecutor returned blank type: " + executor.getClass().getName());
            }

            if (executors.containsKey(key)) {
                throw new RuntimeException("Duplicate StepExecutor type registration: " + key);
            }

            executors.put(key, executor);
        }
    }

    public StepExecutor get(String type) {
        String key = normalize(type);
        StepExecutor executor = executors.get(key);

        if (executor == null) {
            throw new RuntimeException("No StepExecutor registered for type: " + key);
        }

        return executor;
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }
}