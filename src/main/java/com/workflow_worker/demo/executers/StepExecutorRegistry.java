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

            String key = resolveKey(executor);

            executors.put(key, executor);
        }
    }

    public StepExecutor get(String type) {

        StepExecutor executor = executors.get(type);

        if (executor == null) {
            throw new RuntimeException("No StepExecutor registered for type: " + type);
        }

        return executor;
    }

    private String resolveKey(StepExecutor executor) {

        if (executor instanceof HttpCallStepExecutor) {
            return "http";
        }

        if (executor instanceof LogStepExecutor) {
            return "log";
        }

        throw new RuntimeException("Unknown StepExecutor type: " + executor.getClass());
    }
}