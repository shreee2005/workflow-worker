package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.executers.PluginRegistry;
import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

@Component
public class StepDispatcher {

    private final StepExecutorRegistry stepExecutorRegistry;
    private final PluginRegistry pluginRegistry;

    public StepDispatcher(StepExecutorRegistry stepExecutorRegistry, PluginRegistry pluginRegistry) {
        this.stepExecutorRegistry = stepExecutorRegistry;
        this.pluginRegistry = pluginRegistry;
    }

    public StepExecutionResult dispatch(StepDefinition step, String payload) {
        String type = normalize(step.getType());
        WorkflowPlugin plugin = pluginRegistry.get(type);

        if (plugin != null) {
            try {
                plugin.init();
                plugin.validate(step);
                String output = plugin.execute(step, payload);
                return StepExecutionResult.success(output);
            } catch (Exception ex) {
                return StepExecutionResult.failure(ex.getMessage());
            } finally {
                try {
                    plugin.cleanup();
                } catch (Exception ignored) {
                    // Suppress cleanup failures so they do not shadow execution outcome
                }
            }
        }

        // Fallback to legacy executors
        try {
            StepExecutor executor = stepExecutorRegistry.get(type);
            String output = executor.execute(step, payload);
            return StepExecutionResult.success(output);
        } catch (Exception ex) {
            return StepExecutionResult.failure(ex.getMessage());
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}