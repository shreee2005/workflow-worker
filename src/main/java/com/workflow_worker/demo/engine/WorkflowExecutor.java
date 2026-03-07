package com.workflow_worker.demo.engine;
import com.workflow_worker.demo.entity.Workflow;
import com.workflow_worker.demo.entity.WorkflowRunStep;
import com.workflow_worker.demo.repository.WorkflowRepository;
import com.workflow_worker.demo.service.WorkflowRunStepService;
import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import java.util.List;
import java.util.UUID;

public class WorkflowExecutor {
    private final WorkflowRepository workflowRepository;
    private final WorkflowRunStepService stepService;
    private final StepDispatcher dispatcher;

    public WorkflowExecutor(WorkflowRepository workflowRepository, WorkflowRunStepService stepService, StepDispatcher dispatcher) {
        this.workflowRepository = workflowRepository;
        this.stepService = stepService;

        this.dispatcher = dispatcher;
    }

    public void executeRun(
            UUID runId,
            UUID workflowId,
            String payload
    ) throws Exception{
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow Not Found"));

        List<StepDefinition> steps =
                WorkflowSpecParser.parse(wf.getSpec());

        for (int i = 0; i < steps.size(); i++) {

            StepDefinition stepDef = steps.get(i);

            WorkflowRunStep step =
                    stepService.startStep(runId, i, stepDef.getType());

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
}
