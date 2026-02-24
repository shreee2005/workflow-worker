package com.workflow_worker.demo.service;

import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.repository.WorkflowRunRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class WorkflowRunService {

    private final WorkflowRunRepository repo;

    public WorkflowRunService(WorkflowRunRepository repo) {
        this.repo = repo;
    }

    public WorkflowRun getRun(UUID runId) {
        return repo.findById(runId).orElseThrow();
    }

    /**
     * Idempotency gate: only QUEUED → RUNNING is allowed.
     * Called at the top of the worker.
     */
    public void markRunning(UUID runId) {
        WorkflowRun run = getRun(runId);

        if (run.getStatus() != WorkflowRun.Status.QUEUED) {
            throw new IllegalStateException(
                    "Run already processed: " + run.getStatus()
            );
        }

        run.setStatus(WorkflowRun.Status.RUNNING);
        run.setStartedAt(OffsetDateTime.now());
        repo.save(run);
    }

    /**
     * Can this run be retried again?
     * We allow both RUNNING and QUEUED here so the same run
     * can be retried after being put back in the queue.
     */
    public boolean canRetry(WorkflowRun run) {
        return (run.getStatus() == WorkflowRun.Status.RUNNING
                || run.getStatus() == WorkflowRun.Status.QUEUED)
                && run.getAttempt() < run.getMaxAttempts();
    }

    public void incrementAttempt(UUID runId) {
        WorkflowRun run = getRun(runId);
        run.setAttempt(run.getAttempt() + 1);
        repo.save(run);
    }

    public void markSucceeded(UUID runId) {
        WorkflowRun run = getRun(runId);

        if (run.getStatus() != WorkflowRun.Status.RUNNING) {
            throw new IllegalStateException("Invalid success transition");
        }

        run.setStatus(WorkflowRun.Status.SUCCEEDED);
        run.setFinishedAt(OffsetDateTime.now());
        repo.save(run);
    }

    public void markFailed(UUID runId, String error) {
        WorkflowRun run = getRun(runId);

        if (run.getStatus() != WorkflowRun.Status.RUNNING) {
            throw new IllegalStateException("Invalid failure transition");
        }

        run.setStatus(WorkflowRun.Status.FAILED);
        run.setDeadLettered(true);
        run.setFinishedAt(OffsetDateTime.now());
        run.setErrorMessage(error);
        repo.save(run);
    }

    /**
     * Called only from the worker retry logic after incrementAttempt.
     */
    public void markQueuedForRetry(UUID runId) {
        WorkflowRun run = getRun(runId);

        if (run.getStatus() != WorkflowRun.Status.RUNNING) {
            throw new IllegalStateException("Invalid retry transition");
        }

        run.setStatus(WorkflowRun.Status.QUEUED);
        repo.save(run);
    }
}
