package com.workflow_worker.demo.service;

import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.repository.WorkflowRunStepRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

import java.util.UUID;

@Service
public class WorkflowRunStepService {

    private final WorkflowRunStepRepository repo;

    public WorkflowRunStepService(WorkflowRunStepRepository repo) {
        this.repo = repo;
    }

    public WorkflowRunStep startStep(UUID runId, int index, String type) {

        WorkflowRunStep step = new WorkflowRunStep();
        step.setId(UUID.randomUUID());
        step.setRunId(runId);
        step.setStepIndex(index);
        step.setStepType(type);

        step.setStatus(WorkflowRunStep.Status.RUNNING);
        step.setStartedAt(OffsetDateTime.now());

        return repo.save(step);
    }

    public void succeedStep(WorkflowRunStep step, String logs) {

        transition(step, WorkflowRunStep.Status.SUCCEEDED);

        step.setFinishedAt(OffsetDateTime.now());
        step.setLogs(logs);

        repo.save(step);
    }

    public void failStep(WorkflowRunStep step, String error) {

        transition(step, WorkflowRunStep.Status.FAILED);

        step.setFinishedAt(OffsetDateTime.now());
        step.setErrorMessage(error);

        repo.save(step);
    }

    /**
     * Determines which step should run next.
     *
     * Logic:
     * 1. If a step FAILED → retry that step
     * 2. Otherwise run the next index after the last succeeded step
     */
    public int getNextPendingStepIndex(UUID runId) {

        List<WorkflowRunStep> steps =
                repo.findByRunIdOrderByStepIndexAsc(runId);

        if (steps.isEmpty()) {
            return 0;
        }

        int lastSucceededIndex = -1;

        for (WorkflowRunStep step : steps) {

            if (step.getStatus() == WorkflowRunStep.Status.FAILED) {
                return step.getStepIndex();
            }

            if (step.getStatus() == WorkflowRunStep.Status.SUCCEEDED) {
                lastSucceededIndex = step.getStepIndex();
            }
        }

        return lastSucceededIndex + 1;
    }

    private void transition(
            WorkflowRunStep step,
            WorkflowRunStep.Status target
    ) {

        WorkflowRunStep.Status current = step.getStatus();

        if (!ALLOWED_TRANSITIONS
                .getOrDefault(current, Set.of())
                .contains(target)) {

            throw new IllegalStateException(
                    "Invalid step transition: " + current + " → " + target
            );
        }

        step.setStatus(target);
    }

    private static final Map<WorkflowRunStep.Status, Set<WorkflowRunStep.Status>> ALLOWED_TRANSITIONS =
            Map.of(
                    WorkflowRunStep.Status.PENDING,
                    Set.of(WorkflowRunStep.Status.RUNNING),

                    WorkflowRunStep.Status.RUNNING,
                    Set.of(
                            WorkflowRunStep.Status.SUCCEEDED,
                            WorkflowRunStep.Status.FAILED
                    ),

                    WorkflowRunStep.Status.SUCCEEDED,
                    Set.of(),

                    WorkflowRunStep.Status.FAILED,
                    Set.of()
            );
}