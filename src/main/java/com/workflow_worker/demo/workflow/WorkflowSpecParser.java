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

                JsonNode configNode = node.get("config");

                Map<String,Object> config;

                if (configNode == null || configNode.isNull()) {
                    config = new HashMap<>();
                } else {
                    config = mapper.convertValue(
                            configNode,
                            new TypeReference<Map<String,Object>>() {}
                    );
                }

                def.setConfig(config);

                steps.add(def);
            }

            return steps;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse workflow spec", e);
        }
    }
}