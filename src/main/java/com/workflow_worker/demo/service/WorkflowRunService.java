package com.workflow_worker.demo.service;

import com.workflow_worker.demo.engine.events.*;
import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.repository.WorkflowRunRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowRunService {

    private final WorkflowRunRepository repo;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowRunService(
            WorkflowRunRepository repo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
    }

    public WorkflowRun getRun(UUID runId) {
        return repo.findById(runId).orElseThrow();
    }

    public boolean canRetry(WorkflowRun run) {
        return run.getAttempt() < run.getMaxAttempts();
    }

    public void incrementAttempt(UUID runId) {
        WorkflowRun run = getRun(runId);
        run.setAttempt(run.getAttempt() + 1);
        repo.save(run);
    }

    public void transition(UUID runId, WorkflowRun.Status target) {

        WorkflowRun run = getRun(runId);
        WorkflowRun.Status current = run.getStatus();

        if (!ALLOWED_TRANSITIONS
                .getOrDefault(current, Set.of())
                .contains(target)) {

            throw new IllegalStateException(
                    "Invalid Transition " + current + " -> " + target
            );
        }

        run.setStatus(target);

        if (target == WorkflowRun.Status.RUNNING) {

            run.setStartedAt(OffsetDateTime.now());

            eventPublisher.publishEvent(
                    new WorkflowStartedEvent(runId, run.getWorkflowId())
            );
        }

        if (target == WorkflowRun.Status.RETRYING) {

            eventPublisher.publishEvent(
                    new WorkflowRetryEvent(runId, run.getAttempt())
            );
        }

        if (target == WorkflowRun.Status.SUCCEEDED) {

            run.setFinishedAt(OffsetDateTime.now());

            eventPublisher.publishEvent(
                    new WorkflowSucceededEvent(runId)
            );
        }

        if (target == WorkflowRun.Status.FAILED) {

            run.setFinishedAt(OffsetDateTime.now());

            eventPublisher.publishEvent(
                    new WorkflowFailedEvent(runId, run.getErrorMessage())
            );
        }

        repo.save(run);
    }

    private static final Map<WorkflowRun.Status, Set<WorkflowRun.Status>> ALLOWED_TRANSITIONS =
            Map.of(
                    WorkflowRun.Status.CREATED,
                    Set.of(WorkflowRun.Status.QUEUED),

                    WorkflowRun.Status.QUEUED,
                    Set.of(WorkflowRun.Status.RUNNING),

                    WorkflowRun.Status.RUNNING,
                    Set.of(
                            WorkflowRun.Status.RETRYING,
                            WorkflowRun.Status.SUCCEEDED,
                            WorkflowRun.Status.FAILED
                    ),

                    WorkflowRun.Status.RETRYING,
                    Set.of(
                            WorkflowRun.Status.RUNNING,
                            WorkflowRun.Status.FAILED
                    ),

                    WorkflowRun.Status.SUCCEEDED,
                    Set.of(),

                    WorkflowRun.Status.FAILED,
                    Set.of()
            );
}