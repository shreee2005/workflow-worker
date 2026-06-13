package com.workflow_worker.demo.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionPlan {
    private final List<ExecutionStage> stages;
    private final Map<Integer, DagNode> dagNodes;

    public ExecutionPlan(List<ExecutionStage> stages, Map<Integer, DagNode> dagNodes) {
        this.stages = new ArrayList<>(stages);
        this.dagNodes = new HashMap<>(dagNodes);
    }

    /**
     * Get all execution stages in order.
     */
    public List<ExecutionStage> getStages() {
        return new ArrayList<>(stages);
    }

    /**
     * Get a specific stage by number.
     */
    public ExecutionStage getStage(int stageNumber) {
        if (stageNumber < 0 || stageNumber >= stages.size()) {
            return null;
        }
        return stages.get(stageNumber);
    }

    /**
     * Get total number of stages.
     */
    public int getStageCount() {
        return stages.size();
    }

    /**
     * Get total number of steps across all stages.
     */
    public int getTotalSteps() {
        return dagNodes.size();
    }

    /**
     * Check if a step is parallelizable (part of a stage with >1 step).
     */
    public boolean isStepParallelizable(int stepIndex) {
        for (ExecutionStage stage : stages) {
            if (stage.getStepIndices().contains(stepIndex)) {
                return stage.isParallel();
            }
        }
        return false;
    }

    /**
     * Get dependencies for a specific step.
     */
    public List<Integer> getDependencies(int stepIndex) {
        DagNode node = dagNodes.get(stepIndex);
        return node != null ? node.getDependsOnIndices() : new ArrayList<>();
    }

    /**
     * Get all steps that depend on a given step.
     */
    public List<Integer> getDependents(int stepIndex) {
        DagNode node = dagNodes.get(stepIndex);
        return node != null ? node.getDependentIndices() : new ArrayList<>();
    }

    /**
     * Pretty print the execution plan.
     */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("ExecutionPlan (").append(getTotalSteps()).append(" steps, ")
                .append(getStageCount()).append(" stages)\n");
        for (ExecutionStage stage : stages) {
            sb.append("  ").append(stage).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return prettyPrint();
    }
}
