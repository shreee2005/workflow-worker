package com.workflow_worker.demo.dag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime execution context for a workflow run.
 * Tracks step outputs, variables, and runtime state for variable interpolation.
 *
 * Usage:
 * context.setStepOutput(0, "{\"result\": \"value\"}");
 * String resolved = context.resolveVariables("${steps[0].output}");
 */
public class WorkflowContext {
    private final UUID runId;
    // ConcurrentHashMap: parallel steps in the same stage write their outputs
    // from different threads simultaneously. A plain HashMap would cause silent
    // data corruption or ConcurrentModificationException under real parallel load.
    private final Map<Integer, String> stepOutputs;  // stepIndex -> JSON string
    private final Map<String, Object> variables;
    private static final ObjectMapper mapper = new ObjectMapper();

    public WorkflowContext(UUID runId) {
        this.runId = runId;
        this.stepOutputs = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
    }

    public UUID getRunId() {
        return runId;
    }

    /**
     * Store output from a completed step.
     */
    public void setStepOutput(int stepIndex, String jsonOutput) {
        if (jsonOutput != null && !jsonOutput.isBlank()) {
            stepOutputs.put(stepIndex, jsonOutput);
        }
    }

    /**
     * Get output from a step (as JSON string).
     */
    public String getStepOutput(int stepIndex) {
        return stepOutputs.getOrDefault(stepIndex, null);
    }

    /**
     * Get output parsed as Map (convenience method).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStepOutputAsMap(int stepIndex) {
        String output = getStepOutput(stepIndex);
        if (output == null) {
            return new HashMap<>();
        }
        try {
            Object parsed = mapper.readValue(output, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Set a runtime variable.
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * Get a runtime variable.
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Check if a step has completed (has output).
     */
    public boolean isStepCompleted(int stepIndex) {
        return stepOutputs.containsKey(stepIndex);
    }

    /**
     * Get all completed step indices.
     */
    public Set<Integer> getCompletedStepIndices() {
        return new HashSet<>(stepOutputs.keySet());
    }

    /**
     * Clear (reset) all outputs and variables.
     */
    public void clear() {
        stepOutputs.clear();
        variables.clear();
    }

    public Map<Integer, String> getStepOutputs() {
        return stepOutputs;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    @SuppressWarnings("unchecked")
    public static WorkflowContext deserialize(UUID runId, String json) {
        WorkflowContext context = new WorkflowContext(runId);
        if (json == null || json.isBlank()) {
            return context;
        }
        try {
            Map<String, Object> map = mapper.readValue(json, Map.class);
            if (map.containsKey("stepOutputs")) {
                Map<?, ?> outputs = (Map<?, ?>) map.get("stepOutputs");
                for (Map.Entry<?, ?> entry : outputs.entrySet()) {
                    int key = Integer.parseInt(entry.getKey().toString());
                    String val = (String) entry.getValue();
                    context.setStepOutput(key, val);
                }
            }
            if (map.containsKey("variables")) {
                Map<?, ?> vars = (Map<?, ?>) map.get("variables");
                for (Map.Entry<?, ?> entry : vars.entrySet()) {
                    String key = (String) entry.getKey();
                    context.setVariable(key, entry.getValue());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize WorkflowContext: " + e.getMessage(), e);
        }
        return context;
    }
}