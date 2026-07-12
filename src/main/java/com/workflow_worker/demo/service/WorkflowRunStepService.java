package com.workflow_worker.demo.service;

import com.workflow_worker.demo.dag.WorkflowContext;
import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.repository.WorkflowRunStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        succeedStep(step, logs, null);
    }

    public void succeedStep(WorkflowRunStep step, String logs, String output) {
        transition(step, WorkflowRunStep.Status.SUCCEEDED);
        step.setFinishedAt(OffsetDateTime.now());
        step.setLogs(logs);
        if (output != null) {
            step.setOutput(output);
        }
        repo.save(step);
    }

    public void failStep(WorkflowRunStep step, String error) {

        transition(step, WorkflowRunStep.Status.FAILED);

        step.setFinishedAt(OffsetDateTime.now());
        step.setErrorMessage(error);

        repo.save(step);
    }

    public WorkflowRunStep markStepWaiting(UUID runId, int stepIndex, String stepType, String logs) {
        WorkflowRunStep step = new WorkflowRunStep();
        step.setId(UUID.randomUUID());
        step.setRunId(runId);
        step.setStepIndex(stepIndex);
        step.setStepType(stepType);
        step.setStatus(WorkflowRunStep.Status.WAITING);
        step.setStartedAt(OffsetDateTime.now());
        step.setFinishedAt(OffsetDateTime.now());
        step.setLogs(logs);
        return repo.save(step);
    }

    public boolean hasFailedStep(UUID runId) {
        return repo.findByRunId(runId).stream()
                .anyMatch(s -> s.getStatus() == WorkflowRunStep.Status.FAILED);
    }

    @Transactional
    public void resetFailedStepsForRetry(UUID runId) {
        List<WorkflowRunStep> steps = repo.findByRunId(runId);
        for (WorkflowRunStep step : steps) {
            if (step.getStatus() == WorkflowRunStep.Status.FAILED) {
                repo.delete(step);
            }
        }
        repo.flush();
    }

    @Transactional
    public void resetRunningAndFailedStepsForRecovery(UUID runId) {
        List<WorkflowRunStep> steps = repo.findByRunId(runId);
        for (WorkflowRunStep step : steps) {
            if (step.getStatus() == WorkflowRunStep.Status.RUNNING || step.getStatus() == WorkflowRunStep.Status.FAILED) {
                repo.delete(step);
            }
        }
        repo.flush();
    }

    public WorkflowContext buildContextFromSteps(UUID runId) {
        WorkflowContext context = new WorkflowContext(runId);

        List<WorkflowRunStep> steps = repo.findByRunIdOrderByStepIndexAsc(runId);
        for (WorkflowRunStep step : steps) {
            if (step.getStatus() == WorkflowRunStep.Status.SUCCEEDED && step.getOutput() != null) {
                context.setStepOutput(step.getStepIndex(), step.getOutput());
            }
        }

        return context;
    }

    public WorkflowRunStep getStepByRunAndIndex(UUID runId, int stepIndex) {
        return repo.findByRunId(runId).stream()
                .filter(s -> s.getStepIndex() == stepIndex)
                .findFirst()
                .orElse(null);
    }

    public List<WorkflowRunStep> getStepsForRun(UUID runId) {
        return repo.findByRunIdOrderByStepIndexAsc(runId);
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
                    WorkflowRunStep.Status.WAITING,
                    Set.of(
                            WorkflowRunStep.Status.RUNNING
                    ),
                    WorkflowRunStep.Status.SUCCEEDED,
                    Set.of(),

                    WorkflowRunStep.Status.FAILED,
                    Set.of()
            );

    public void save(WorkflowRunStep step) {
        repo.save(step);
    }
}