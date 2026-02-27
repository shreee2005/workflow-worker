package com.workflow_worker.demo.service;

import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.repository.WorkflowRunStepRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
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
        step.setStatus(WorkflowRunStep.Status.SUCCEEDED);
        step.setFinishedAt(OffsetDateTime.now());
        step.setLogs(logs);
        repo.save(step);
    }

    public void failStep(WorkflowRunStep step, String error) {
        step.setStatus(WorkflowRunStep.Status.FAILED);
        step.setFinishedAt(OffsetDateTime.now());
        step.setErrorMessage(error);
        repo.save(step);
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
