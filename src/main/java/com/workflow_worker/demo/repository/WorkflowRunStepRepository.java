package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowRunStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowRunStepRepository
        extends JpaRepository<WorkflowRunStep, UUID> {

    List<WorkflowRunStep> findByRunIdOrderByStepIndexAsc(UUID runId);

    List<WorkflowRunStep> findByRunId(UUID runId);
}

