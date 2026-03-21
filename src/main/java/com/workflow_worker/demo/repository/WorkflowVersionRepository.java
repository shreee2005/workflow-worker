package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {
}