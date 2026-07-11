package com.workflow_worker.demo.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class WorkflowSpecParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<StepDefinition> parse(String specJson) {

        try {
            JsonNode root = mapper.readTree(specJson);
            JsonNode stepsNode = root.get("steps");

            if (stepsNode == null || !stepsNode.isArray()) {
                throw new IllegalArgumentException("Invalid workflow spec: steps missing");
            }

            List<StepDefinition> steps = new ArrayList<>();

            for (JsonNode node : stepsNode) {
                StepDefinition def = new StepDefinition();

                def.setType(node.get("type").asText());

                if (node.has("name") && !node.get("name").isNull()) {
                    def.setName(node.get("name").asText());
                }

                // Parse executor config — a clean map the executor actually needs.
                JsonNode configNode = node.get("config");
                Map<String, Object> config;
                if (configNode == null || configNode.isNull()) {
                    config = new HashMap<>();
                } else {
                    config = mapper.convertValue(
                            configNode,
                            new TypeReference<Map<String, Object>>() {}
                    );
                }

                // --- dependsOn resolution (order matters) ---
                // 1. Preferred: step-level field  {"type":..., "dependsOn": [0,1], "config":{...}}
                // 2. Fallback:  legacy position inside config  {"config": {"dependsOn": [0], ...}}
                //    The fallback keeps existing stored specs working without a migration.
                //    In both cases dependsOn is stripped from config so executors never see it.
                List<Integer> dependsOn = parseDependsOn(node.get("dependsOn"));
                if (dependsOn.isEmpty()) {
                    dependsOn = parseDependsOn(
                            config.containsKey("dependsOn")
                                    ? mapper.convertValue(config.get("dependsOn"), JsonNode.class)
                                    : null
                    );
                }
                config.remove("dependsOn");  // never leak control fields into executor config
                def.setDependsOn(dependsOn);

                def.setConfig(config);
                steps.add(def);
            }

            return steps;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse workflow spec", e);
        }
    }

    /**
     * Parse a JSON array node into a list of integer step indices.
     * Returns an empty list if the node is null, missing, or contains no integers.
     */
    private static List<Integer> parseDependsOn(JsonNode node) {
        List<Integer> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item.isInt()) {
                result.add(item.intValue());
            }
            // String (named) dependencies are intentionally skipped here;
            // name-based resolution is a future enhancement in DagParser.
        }
        return result;
    }
}