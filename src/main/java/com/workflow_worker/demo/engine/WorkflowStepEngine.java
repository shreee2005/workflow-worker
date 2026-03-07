package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.entity.Workflow;
import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.repository.WorkflowRepository;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class WorkflowStepEngine {

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunStepService stepService;

    public WorkflowStepEngine(
            WorkflowRepository workflowRepository,
            WorkflowRunStepService stepService
    ) {
        this.workflowRepository = workflowRepository;
        this.stepService = stepService;
    }

    public void executeSteps(
            UUID runId,
            UUID workflowId,
            Object payload
    ) throws Exception {

        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        List<StepDefinition> steps =
                WorkflowSpecParser.parse(wf.getSpec());

        for (int i = 0; i < steps.size(); i++) {

            StepDefinition stepDef = steps.get(i);

            WorkflowRunStep step =
                    stepService.startStep(runId, i, stepDef.getType());

            try {
                StepExecutor executor =
                        StepExecutorRegistry.get(stepDef.getType());

                System.out.println("Executing step: " + stepDef.getType());

                executor.execute(stepDef, (String) payload);

                stepService.succeedStep(step, "OK");

            } catch (Exception ex) {
                System.out.println("STEP FAILED: " + ex.getMessage());
                stepService.failStep(step, ex.getMessage());
                throw ex;
            }
        }
    }
}