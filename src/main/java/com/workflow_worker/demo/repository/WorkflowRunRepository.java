package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowRunRepository
        extends JpaRepository<WorkflowRun, UUID> {
}

