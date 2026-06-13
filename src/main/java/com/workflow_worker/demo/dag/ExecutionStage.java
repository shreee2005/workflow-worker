package com.workflow_worker.demo.dag;

import java.util.ArrayList;
import java.util.List;

public class ExecutionStage {
    private final int stageNumber;
    private final List<Integer> stepIndices;  // Indices of steps in this stage

    public ExecutionStage(int stageNumber, List<Integer> stepIndices) {
        this.stageNumber = stageNumber;
        this.stepIndices = new ArrayList<>(stepIndices);
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public List<Integer> getStepIndices() {
        return new ArrayList<>(stepIndices);
    }

    public int getStepCount() {
        return stepIndices.size();
    }

    public boolean isParallel() {
        return stepIndices.size() > 1;
    }

    @Override
    public String toString() {
        return "Stage " + stageNumber + ": " + stepIndices +
                (isParallel() ? " (parallel)" : " (sequential)");
    }
}
