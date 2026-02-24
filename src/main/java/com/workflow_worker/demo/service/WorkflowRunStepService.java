package com.workflow_worker.demo.service;

import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.repository.WorkflowRunStepRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
}
