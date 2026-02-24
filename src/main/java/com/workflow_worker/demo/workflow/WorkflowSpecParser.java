package com.workflow_worker.demo.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

            for (Iterator<JsonNode> it = stepsNode.elements(); it.hasNext(); ) {
                JsonNode node = it.next();

                StepDefinition def = new StepDefinition();
                def.setType(node.get("type").asText());
                def.setConfig(
                        mapper.convertValue(node, Map.class)
                );

                steps.add(def);
            }

            return steps;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse workflow spec", e);
        }
    }
}

