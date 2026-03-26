package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowWaitState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowWaitStateRepository extends JpaRepository<WorkflowWaitState, UUID> {
    Optional<WorkflowWaitState> findByRunId(UUID runId);
}