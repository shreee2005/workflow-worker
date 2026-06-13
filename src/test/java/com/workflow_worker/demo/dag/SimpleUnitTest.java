package com.workflow_worker.demo.dag;

import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIMPLE UNIT TEST - No database, no Spring Boot needed!
 * Just pure Java code testing.
 */
public class SimpleUnitTest {

    /**
     * TEST 1: Can we identify parallel steps?
     *
     * Workflow:
     * Step 0 (start)
     *   ├─ Step 1 (parallel)
     *   └─ Step 2 (parallel)
     * Step 3 (merge)
     */
    @Test
    public void test_parallelStepsIdentified() {
        // 1. Create workflow specification (JSON string)
        String workflowSpec = """
            {
              "steps": [
                {"type": "LOG", "config": {"message": "Step 0"}},
                {"type": "LOG", "config": {"message": "Step 1", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Step 2", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Step 3", "dependsOn": [1, 2]}}
              ]
            }
            """;

        // 2. Parse JSON string into StepDefinition objects
        List<StepDefinition> steps = WorkflowSpecParser.parse(workflowSpec);
        System.out.println("📋 Parsed " + steps.size() + " steps");

        // 3. Build execution plan (this creates stages)
        ExecutionPlan plan = DagParser.parse(steps);
        System.out.println("📊 Created " + plan.getStageCount() + " stages");

        // 4. CHECK: Did it identify parallel steps correctly?
        ExecutionStage stage1 = plan.getStage(1);
        System.out.println("✅ Stage 1: " + stage1.getStepIndices());

        // 5. ASSERT (make sure answer is correct)
        assertEquals(2, stage1.getStepCount(), "Stage 1 should have 2 steps");
        assertTrue(stage1.isParallel(), "Stage 1 should be marked as parallel");
        assertTrue(stage1.getStepIndices().contains(1), "Stage 1 should have step 1");
        assertTrue(stage1.getStepIndices().contains(2), "Stage 1 should have step 2");

        System.out.println("🎉 TEST PASSED: Parallel steps correctly identified!");
        System.out.println(plan.prettyPrint());
    }

    /**
     * TEST 2: Can we pass data between steps?
     */
    @Test
    public void test_variableResolution() {
        // Create context (like a memory storage)
        WorkflowContext context = new WorkflowContext(java.util.UUID.randomUUID());

        // Step 0 produced this output
        context.setStepOutput(0, "{\"userId\": \"123\", \"name\": \"John\"}");

        // Step 1 wants to use Step 0's output
        String config = "User ${steps[0].output.userId} named ${steps[0].output.name}";

        // Resolve the variables
        String resolved = ContextVariableResolver.resolveVariables(config, context);
        System.out.println("Original: " + config);
        System.out.println("Resolved: " + resolved);

        // CHECK: Did it replace correctly?
        assertEquals("User 123 named John", resolved);

        System.out.println("🎉 TEST PASSED: Variables correctly resolved!");
    }

    /**
     * TEST 3: Can we handle sequential workflows?
     */
    @Test
    public void test_sequentialWorkflow() {
        String spec = """
            {
              "steps": [
                {"type": "LOG", "config": {"message": "Step 0"}},
                {"type": "LOG", "config": {"message": "Step 1", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Step 2", "dependsOn": [1]}}
              ]
            }
            """;

        List<StepDefinition> steps = WorkflowSpecParser.parse(spec);
        ExecutionPlan plan = DagParser.parse(steps);

        // In sequential: each step is its own stage
        assertEquals(3, plan.getStageCount(), "Sequential should have 3 stages");

        for (int i = 0; i < 3; i++) {
            ExecutionStage stage = plan.getStage(i);
            assertFalse(stage.isParallel(), "Stage " + i + " should NOT be parallel");
            assertEquals(1, stage.getStepCount(), "Stage " + i + " should have 1 step");
        }

        System.out.println("🎉 TEST PASSED: Sequential workflow handled correctly!");
        System.out.println(plan.prettyPrint());
    }
}