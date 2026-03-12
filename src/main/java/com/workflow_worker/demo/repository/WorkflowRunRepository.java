package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface WorkflowRunRepository
        extends JpaRepository<WorkflowRun, UUID> {
    @Modifying
    @Transactional
    @Query("UPDATE WorkflowRun r SET r.deadLettered = true WHERE r.id = :runId")
    void markDeadLettered(@Param("runId")UUID runId);
}

