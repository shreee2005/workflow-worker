package com.workflow_worker.demo.dag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves variable references in step configs.
 *
 * Supported syntax:
 * - ${steps[0].output}  → Full output from step 0
 * - ${steps[0].output.fieldName}  → Nested field from step 0 output
 * - ${variables.myVar}  → Runtime variable
 *
 * Example config:
 * {
 *   "url": "https://api.example.com",
 *   "body": "${steps[0].output}"
 * }
 *
 * After resolution (if step 0 output is {"id": "123"}):
 * {
 *   "url": "https://api.example.com",
 *   "body": "{\"id\": \"123\"}"
 * }
 */
public class ContextVariableResolver {
    private static final ObjectMapper mapper = new ObjectMapper();

    // Regex patterns for different variable formats
    private static final Pattern STEPS_INDEX_PATTERN =
            Pattern.compile("\\$\\{steps\\[(\\d+)\\]\\.output(?:\\.(\\w+))?\\}");

    private static final Pattern VARIABLES_PATTERN =
            Pattern.compile("\\$\\{variables\\.(\\w+)\\}");

    /**
     * Resolve all variables in a configuration map.
     * Modifies the map in-place.
     */
    public static void resolveVariablesInConfig(
            Map<String, Object> config,
            WorkflowContext context
    ) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String resolved = resolveVariables((String) value, context);
                entry.setValue(resolved);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                resolveVariablesInConfig(nested, context);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof String) {
                        list.set(i, resolveVariables((String) item, context));
                    } else if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedItem = (Map<String, Object>) item;
                        resolveVariablesInConfig(nestedItem, context);
                    }
                }
            }
        }
    }

    /**
     * Resolve all variables in a single string.
     * Returns the resolved string with all variables replaced.
     */
    public static String resolveVariables(String input, WorkflowContext context) {
        if (input == null || !input.contains("${")) {
            return input;
        }

        String result = input;

        // Resolve ${steps[N].output} patterns
        result = resolveStepsVariables(result, context);

        // Resolve ${variables.name} patterns
        result = resolveRuntimeVariables(result, context);

        return result;
    }

    private static String resolveStepsVariables(String input, WorkflowContext context) {
        Matcher matcher = STEPS_INDEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            int stepIndex = Integer.parseInt(matcher.group(1));
            String fieldName = matcher.group(2);  // May be null

            String replacement = getStepValue(context, stepIndex, fieldName);
            if (replacement != null) {
                // Escape $ and \ for regex replacement
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                // Leave unresolved if step not found
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveRuntimeVariables(String input, WorkflowContext context) {
        Matcher matcher = VARIABLES_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.getVariable(varName);

            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String getStepValue(WorkflowContext context, int stepIndex, String fieldName) {
        if (!context.isStepCompleted(stepIndex)) {
            return null;  // Step hasn't completed yet
        }

        String output = context.getStepOutput(stepIndex);

        if (fieldName == null) {
            // Return full output
            return output;
        }

        // Extract nested field
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> outputMap = mapper.readValue(output, Map.class);
            Object fieldValue = outputMap.get(fieldName);

            if (fieldValue == null) {
                return null;
            }

            if (fieldValue instanceof String) {
                return (String) fieldValue;
            } else if (fieldValue instanceof Map || fieldValue instanceof List) {
                return mapper.writeValueAsString(fieldValue);
            } else {
                return fieldValue.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}