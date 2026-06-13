package com.workflow_worker.demo.dag;

import java.util.ArrayList;
import java.util.List;

public class DagNode {
    private final int stepIndex;
    private final List<Integer> dependsOnIndices;
    private final List<Integer> dependentIndices;

    public DagNode(int stepIndex, List<Integer> dependsOnIndices) {
        this.stepIndex = stepIndex;
        this.dependsOnIndices = dependsOnIndices;
        this.dependentIndices = new ArrayList<>();
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public List<Integer> getDependsOnIndices() {
        return new ArrayList<>(dependsOnIndices);
    }

    public List<Integer> getDependentIndices() {
        return dependentIndices;
    }

    public void addDependent(int stepIndex){
        if(!dependentIndices.contains(stepIndex)){
            dependentIndices.add(stepIndex);
        }
    }

    public boolean hasNoDependencies(){
        return dependsOnIndices.isEmpty();
    }

    public boolean isDependentOn(int stepIndex){
        return dependentIndices.contains(stepIndex);
    }

    @Override
    public String toString() {
        return "DagNode{" +
                "stepIndex=" + stepIndex +
                ", dependsOn=" + dependsOnIndices +
                ", dependents=" + dependentIndices +
                '}';
    }

}
