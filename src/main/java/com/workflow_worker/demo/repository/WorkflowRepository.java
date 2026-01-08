package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
}
