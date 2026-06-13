package com.workflow_worker.demo.dag;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.hibernate.event.internal.EvictVisitor;

import java.util.*;
import java.util.stream.Collectors;
public class DagParser {
    public static ExecutionPlan parse(List<StepDefinition> steps){
        validateSteps(steps);

        Map<Integer , DagNode> nodes = new HashMap<>();
        for(int i = 0 ; i < steps.size() ; i++){
            StepDefinition step = steps.get(i);
            List<Integer> dependsOn = extractDependencies(step , i);
            nodes.put(i , new DagNode(i , dependsOn));
        }

        for(Map.Entry<Integer , DagNode> entry : nodes.entrySet()){
            DagNode node = entry.getValue();
            for(int depIndex : node.getDependsOnIndices()){
                DagNode depNode = nodes.get(depIndex);
                depNode.addDependent(entry.getKey());
            }
        }

        detectCycles(nodes);
        List<ExecutionStage> stages = buildExecutionStages(nodes, steps);

        return new ExecutionPlan(stages, nodes);

    }

    @SuppressWarnings("unchecked")
    private static List<Integer> extractDependencies(StepDefinition step , int currentIndex) {
        List<Integer> deps = new ArrayList<>();

        if (step.getConfig() == null) {
            return deps;
        }
        Object dependsOnObj = step.getConfig().get("dependsOn");

        if (dependsOnObj == null) {
            return deps;
        }
        if (dependsOnObj instanceof List) {
            List<?> depList = (List<?>) dependsOnObj;
            for (Object item : depList) {
                if (item instanceof Integer) {
                    int depIndex = (Integer) item;
                    if (depIndex < currentIndex && !deps.contains(depIndex)) {
                        deps.add(depIndex);
                    }
                } else if (item instanceof String) {
                    // Named dependency - resolve via name field (future enhancement)
                    String depName = (String) item;
                    // For now, log a warning
                    System.out.println("[DAG] Named dependency '" + depName + "' not yet supported");
                }
            }
        }
        return deps;
    }
    private static void detectCycles(Map<Integer, DagNode> nodes) {
        Set<Integer> visited = new HashSet<>();
        Set<Integer> recursionStack = new HashSet<>();

        for(Integer nodeIndex : nodes.keySet()){
            if(!visited.contains(nodeIndex)){
                if(dfsHasCycle(nodeIndex , nodes , visited , recursionStack)){
                    throw new IllegalArgumentException("Circular dependency detected in workflow");
                }
            }
        }
    }

    private static boolean dfsHasCycle(Integer nodeIndex,
                                       Map<Integer, DagNode> nodes,
                                       Set<Integer> visited,
                                       Set<Integer> recursionStack
    ) {
        visited.add(nodeIndex);
        recursionStack.add(nodeIndex);

        DagNode node = nodes.get(nodeIndex);
        for(int depIndex : node.getDependentIndices()){
            if(!visited.contains(depIndex)){
                if(dfsHasCycle(depIndex , nodes , visited , recursionStack)){
                    return true;
                }
            }
            else if(recursionStack.contains(depIndex)){
                return true;
            }
        }
        recursionStack.remove(nodeIndex);
        return false;
    }
    private static List<ExecutionStage> buildExecutionStages(
            Map<Integer, DagNode> nodes,
            List<StepDefinition> steps
    ) {
        List<ExecutionStage> stages = new ArrayList<>();
        Set<Integer> executed = new HashSet<>();

        while (executed.size() < steps.size()) {
            // Find all nodes with all dependencies satisfied
            List<Integer> readySteps = new ArrayList<>();

            for (Map.Entry<Integer, DagNode> entry : nodes.entrySet()) {
                int stepIndex = entry.getKey();
                DagNode node = entry.getValue();

                if (executed.contains(stepIndex)) {
                    continue;
                }

                // Check if all dependencies are executed
                boolean allDepsSatisfied = node.getDependsOnIndices().stream()
                        .allMatch(executed::contains);

                if (allDepsSatisfied) {
                    readySteps.add(stepIndex);
                }
            }

            if (readySteps.isEmpty()) {
                throw new IllegalStateException(
                        "Deadlock: No steps ready to execute. " +
                                "Check for unsatisfied dependencies in steps: " +
                                executed
                );
            }

            // Create stage with ready steps
            ExecutionStage stage = new ExecutionStage(stages.size(), readySteps);
            stages.add(stage);
            executed.addAll(readySteps);
        }

        return stages;
    }
    private static void validateSteps(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }
    }
}
