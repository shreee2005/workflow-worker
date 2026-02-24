package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.StepExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StepExecutorRegistry {

    private static final Map<String, StepExecutor> executors = new HashMap<>();

    public StepExecutorRegistry(List<StepExecutor> executorList) {
        for (StepExecutor ex : executorList) {
            executors.put(
                    ex.getClass()
                            .getSimpleName()
                            .replace("StepExecutor", "")
                            .toUpperCase(),
                    ex
            );
        }
    }

    public static StepExecutor get(String type) {
        StepExecutor ex = executors.get(type.toUpperCase());
        if (ex == null) {
            throw new RuntimeException("Unknown step type: " + type);
        }
        return ex;
    }
}

