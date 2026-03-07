package com.workflow_worker.demo.engine;

public class StepExecutionResult {
    public enum Status{
        SUCCESS,
        FAILED
    }

    private Status status;
    private String output;

    public Status getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    private String error;

    public static StepExecutionResult success(String output){
        StepExecutionResult r = new StepExecutionResult();
        r.status = Status.SUCCESS;
        r.output = output;
        return r;
    }

    public static StepExecutionResult failure(String error){
        StepExecutionResult r = new StepExecutionResult();
        r.status = Status.FAILED;
        r.error = error;
        return r;
    }


}
