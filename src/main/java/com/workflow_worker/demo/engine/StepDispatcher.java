package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

@Component
public class StepDispatcher {

    private final StepExecutorRegistry registry;

    public StepDispatcher(StepExecutorRegistry registry) {
        this.registry = registry;
    }

    public StepExecutionResult dispatch(
            StepDefinition step,
            String payload
    ) {

        try {

            StepExecutor executor =
                    registry.get(step.getType());

            String output =
                    executor.execute(step, payload);

            return StepExecutionResult.success(output);

        } catch (Exception ex) {

            return StepExecutionResult.failure(
                    ex.getMessage()
            );
        }
    }
}