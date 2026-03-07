package com.workflow_worker.demo.engine;

import com.workflow_worker.demo.executers.StepExecutorRegistry;
import com.workflow_worker.demo.worker.StepExecutor;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.stereotype.Component;

@Component
public class StepDispatcher {
    public StepExecutionResult dispatch(
            StepDefinition step ,
            String payload
    ){
        try {
            StepExecutor executor =
                    StepExecutorRegistry.get(step.getType());

            executor.execute(step , payload);
            return  StepExecutionResult.success("OK");
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
