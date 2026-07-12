package com.workflow_worker.demo.repository;

import com.workflow_worker.demo.entity.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkflowStateRepository extends JpaRepository<WorkflowState, UUID> {
}
