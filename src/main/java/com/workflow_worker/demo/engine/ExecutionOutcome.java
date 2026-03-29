package com.workflow_worker.demo.engine;

public enum ExecutionOutcome {
    COMPLETED,
    WAITING,
    FAILED_RETRYABLE,
    FAILED_NON_RETRYABLE
}
