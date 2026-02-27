package com.workflow_worker.demo.service;

import com.workflow_worker.demo.entity.WorkflowRun;
import com.workflow_worker.demo.repository.WorkflowRunRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
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

    public boolean canRetry(WorkflowRun run) {
        return run.getStatus() == WorkflowRun.Status.RUNNING
                && run.getAttempt() < run.getMaxAttempts();
    }

    public void incrementAttempt(UUID runId) {
        WorkflowRun run = getRun(runId);
        run.setAttempt(run.getAttempt() + 1);
        repo.save(run);
    }

    public void transition(UUID runId , WorkflowRun.Status target){
        WorkflowRun run = getRun(runId);
        WorkflowRun.Status current = run.getStatus();

        if(!ALLOWED_TRANSITIONS
                .getOrDefault(current , Set.of())
                .contains(target)){
            throw new IllegalStateException("Invalid Transition" + current + " -> " + target);
        }
        run.setStatus(target);

        if(target == WorkflowRun.Status.RUNNING){
            run.setStartedAt(OffsetDateTime.now());
        }
        if(target == WorkflowRun.Status.SUCCEEDED || target == WorkflowRun.Status.FAILED){
            run.setFinishedAt(OffsetDateTime.now());
        }

        repo.save(run);
    }

    private static final Map<WorkflowRun.Status, Set<WorkflowRun.Status>> ALLOWED_TRANSITIONS =
            Map.of(
                    WorkflowRun.Status.CREATED, Set.of(WorkflowRun.Status.QUEUED),

                    WorkflowRun.Status.QUEUED,
                    Set.of(WorkflowRun.Status.RUNNING),

                    WorkflowRun.Status.RUNNING,
                    Set.of(
                            WorkflowRun.Status.SUCCEEDED,
                            WorkflowRun.Status.RETRYING,
                            WorkflowRun.Status.FAILED
                    ),

                    WorkflowRun.Status.RETRYING,
                    Set.of(WorkflowRun.Status.QUEUED),

                    WorkflowRun.Status.SUCCEEDED,
                    Set.of(),

                    WorkflowRun.Status.FAILED,
                    Set.of()
            );
}
