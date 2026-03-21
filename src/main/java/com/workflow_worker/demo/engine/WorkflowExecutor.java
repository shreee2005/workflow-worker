package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.entity.WorkflowVersion;
import com.workflow_worker.demo.repository.WorkflowVersionRepository;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class WorkflowExecutor {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowRunStepService stepService;
    private final StepDispatcher dispatcher;

    public WorkflowExecutor(
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowRunStepService stepService,
            StepDispatcher dispatcher
    ) {
        this.workflowVersionRepository = workflowVersionRepository;
        this.stepService = stepService;
        this.dispatcher = dispatcher;
    }

    public void executeRun(
            UUID runId,
            UUID workflowId,
            UUID workflowVersionId,
            String payload
    ) {

        WorkflowVersion wfVersion = workflowVersionRepository.findById(workflowVersionId)
                .orElseThrow(() -> new RuntimeException("Workflow version not found"));

        if (!wfVersion.getWorkflowId().equals(workflowId)) {
            throw new RuntimeException("Workflow version does not belong to workflow");
        }

        List<StepDefinition> steps =
                WorkflowSpecParser.parse(wfVersion.getSpec());

        int nextStepIndex =
                stepService.getNextPendingStepIndex(runId);

        if (nextStepIndex >= steps.size()) {
            return;
        }

        StepDefinition stepDef = steps.get(nextStepIndex);

        WorkflowRunStep step =
                stepService.startStep(runId, nextStepIndex, stepDef.getType());

        System.out.println("STEP TYPE = " + stepDef.getType());
        System.out.println("STEP CONFIG = " + stepDef.getConfig());

        StepExecutionResult result =
                dispatcher.dispatch(stepDef, payload);

        if (result.getStatus() == StepExecutionResult.Status.SUCCESS) {

            stepService.succeedStep(step, result.getOutput());

        } else {

            stepService.failStep(step, result.getError());

            throw new RuntimeException(result.getError());
        }
    }
}