package com.workflow_worker.demo.dag;

import com.workflow_worker.demo.workflow.StepDefinition;
import com.workflow_worker.demo.workflow.WorkflowSpecParser;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for parallelism - NO Spring context required.
 * Tests DAG, ExecutionPlan, and variable resolution.
 */
class ParallelExecutionUnitTest {

    @Test
    void testSequentialExecutionPlan() {
        // Linear workflow: Step 0 -> Step 1 -> Step 2
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

        assertEquals(3, plan.getStageCount(), "Sequential workflow should have 3 stages");
        assertEquals(3, plan.getTotalSteps());
        assertEquals(false, plan.getStage(0).isParallel());
        assertEquals(false, plan.getStage(1).isParallel());
        assertEquals(false, plan.getStage(2).isParallel());

        System.out.println("✅ Sequential execution plan test passed");
        System.out.println(plan.prettyPrint());
    }

    @Test
    void testParallelExecutionPlan() {
        // Parallel workflow
        String spec = """
            {
              "steps": [
                {"type": "LOG", "config": {"message": "Step 0"}},
                {"type": "LOG", "config": {"message": "Step 1", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Step 2", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Step 3", "dependsOn": [1, 2]}}
              ]
            }
            """;

        List<StepDefinition> steps = WorkflowSpecParser.parse(spec);
        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(3, plan.getStageCount(), "Should have 3 stages");
        assertEquals(4, plan.getTotalSteps());

        // Stage 0: 1 step
        assertEquals(1, plan.getStage(0).getStepCount());
        assertFalse(plan.getStage(0).isParallel());

        // Stage 1: 2 steps (PARALLEL!)
        assertEquals(2, plan.getStage(1).getStepCount());
        assertTrue(plan.getStage(1).isParallel());
        assertTrue(plan.getStage(1).getStepIndices().contains(1));
        assertTrue(plan.getStage(1).getStepIndices().contains(2));

        // Stage 2: 1 step
        assertEquals(1, plan.getStage(2).getStepCount());
        assertFalse(plan.getStage(2).isParallel());

        System.out.println("✅ Parallel execution plan test passed");
        System.out.println(plan.prettyPrint());
    }

    @Test
    void testComplexMultiBranchDAG() {
        String spec = """
        {
          "steps": [
            {"type": "LOG", "config": {"message": "Base"}},
            {"type": "LOG", "config": {"message": "Branch A", "dependsOn": [0]}},
            {"type": "LOG", "config": {"message": "Branch B", "dependsOn": [0]}},
            {"type": "LOG", "config": {"message": "Branch B Extended", "dependsOn": [2]}},
            {"type": "LOG", "config": {"message": "Merge", "dependsOn": [1, 3]}}
          ]
        }
        """;

        List<StepDefinition> steps = WorkflowSpecParser.parse(spec);
        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(5, plan.getTotalSteps());

        // Correct stage count:
        // Stage 0 -> [0]
        // Stage 1 -> [1,2]
        // Stage 2 -> [3]
        // Stage 3 -> [4]
        assertEquals(4, plan.getStageCount());

        // Stage 0
        assertEquals(1, plan.getStage(0).getStepCount());
        assertFalse(plan.getStage(0).isParallel());

        // Stage 1 (parallel)
        assertEquals(2, plan.getStage(1).getStepCount());
        assertTrue(plan.getStage(1).isParallel());
        assertTrue(plan.getStage(1).getStepIndices().contains(1));
        assertTrue(plan.getStage(1).getStepIndices().contains(2));

        // Stage 2
        assertEquals(1, plan.getStage(2).getStepCount());
        assertFalse(plan.getStage(2).isParallel());
        assertTrue(plan.getStage(2).getStepIndices().contains(3));

        // Stage 3
        assertEquals(1, plan.getStage(3).getStepCount());
        assertFalse(plan.getStage(3).isParallel());
        assertTrue(plan.getStage(3).getStepIndices().contains(4));

        System.out.println("✅ Complex multi-branch DAG test passed");
        System.out.println(plan.prettyPrint());
    }

    @Test
    void testVariableResolutionInConfig() {
        String spec = """
            {
              "steps": [
                {
                  "type": "LOG",
                  "config": {
                    "message": "{\\"userId\\": \\"123\\", \\"email\\": \\"test@example.com\\"}"
                  }
                },
                {
                  "type": "LOG",
                  "config": {
                    "message": "User ${steps[0].output.userId}",
                    "dependsOn": [0]
                  }
                }
              ]
            }
            """;

        List<StepDefinition> steps = WorkflowSpecParser.parse(spec);
        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(2, plan.getTotalSteps());
        assertEquals(2, plan.getStageCount());

        // Verify dependency
        assertEquals(List.of(0), plan.getDependencies(1));

        System.out.println("✅ Variable resolution in config test passed");
        System.out.println(plan.prettyPrint());
    }

    @Test
    void testWorkflowContextVariableStorage() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());

        // Store step outputs
        context.setStepOutput(0, "{\"userId\": \"123\", \"name\": \"John\"}");
        context.setStepOutput(1, "{\"postCount\": \"5\"}");

        // Store runtime variables
        context.setVariable("apiKey", "secret123");
        context.setVariable("maxRetries", 3);

        // Verify storage
        assertEquals("{\"userId\": \"123\", \"name\": \"John\"}", context.getStepOutput(0));
        assertEquals("{\"postCount\": \"5\"}", context.getStepOutput(1));
        assertEquals("secret123", context.getVariable("apiKey"));
        assertEquals(3, context.getVariable("maxRetries"));

        // Verify completion tracking
        assertTrue(context.isStepCompleted(0));
        assertTrue(context.isStepCompleted(1));
        assertFalse(context.isStepCompleted(2));

        System.out.println("✅ WorkflowContext variable storage test passed");
    }

    @Test
    void testContextVariableResolution() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        context.setStepOutput(0, "{\"userId\": \"user123\", \"email\": \"test@example.com\"}");
        context.setStepOutput(1, "{\"postsCount\": \"42\"}");
        context.setVariable("apiKey", "secret789");

        // Test full output resolution
        String resolved1 = ContextVariableResolver.resolveVariables(
                "${steps[0].output}",
                context
        );
        assertEquals("{\"userId\": \"user123\", \"email\": \"test@example.com\"}", resolved1);

        // Test field extraction
        String resolved2 = ContextVariableResolver.resolveVariables(
                "User ID: ${steps[0].output.userId}",
                context
        );
        assertEquals("User ID: user123", resolved2);

        // Test runtime variable
        String resolved3 = ContextVariableResolver.resolveVariables(
                "Authorization: ${variables.apiKey}",
                context
        );
        assertEquals("Authorization: secret789", resolved3);

        // Test multiple variables
        String resolved4 = ContextVariableResolver.resolveVariables(
                "${steps[0].output.userId} has ${steps[1].output.postsCount} posts",
                context
        );
        assertEquals("user123 has 42 posts", resolved4);

        System.out.println("✅ Context variable resolution test passed");
    }

    @Test
    void testUnresolvedVariables() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        // Don't set any outputs

        String resolved = ContextVariableResolver.resolveVariables(
                "Output: ${steps[0].output}",
                context
        );

        // Should leave unresolved if step output not found
        assertTrue(resolved.contains("${steps[0].output}"));

        System.out.println("✅ Unresolved variables test passed");
    }

    @Test
    void testParallelismEfficiency() {
        // 10 steps, all depending on base step 0
        List<StepDefinition> steps = new ArrayList<>();
        steps.add(new StepDefinition("LOG", Collections.emptyMap()));

        for (int i = 1; i < 10; i++) {
            Map<String, Object> config = new HashMap<>();
            config.put("dependsOn", List.of(0));
            steps.add(new StepDefinition("LOG", config));
        }

        long start = System.currentTimeMillis();
        ExecutionPlan plan = DagParser.parse(steps);
        long elapsed = System.currentTimeMillis() - start;

        // With parallelism: 2 stages (base + parallel)
        // Without parallelism: 10 stages (sequential)
        assertEquals(2, plan.getStageCount(), "Should have 2 stages with parallelism");
        assertTrue(plan.getStage(1).isParallel(), "Stage 1 should have 9 parallel steps");
        assertEquals(9, plan.getStage(1).getStepCount());

        System.out.println("✅ Parallelism efficiency test passed");
        System.out.println("  10 steps parsed in " + elapsed + "ms");
        System.out.println("  Stage 0: " + plan.getStage(0).getStepIndices());
        System.out.println("  Stage 1: " + plan.getStage(1).getStepIndices() + " (PARALLEL)");
    }

    @Test
    void testDAGWithDiamondDependency() {
        // Diamond pattern:
        //     -> Step 1 ->
        // Step 0        -> Step 3
        //     -> Step 2 ->

        String spec = """
            {
              "steps": [
                {"type": "LOG", "config": {"message": "Base"}},
                {"type": "LOG", "config": {"message": "Left", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Right", "dependsOn": [0]}},
                {"type": "LOG", "config": {"message": "Merge", "dependsOn": [1, 2]}}
              ]
            }
            """;

        List<StepDefinition> steps = WorkflowSpecParser.parse(spec);
        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(4, plan.getTotalSteps());
        assertEquals(3, plan.getStageCount());

        // Stage 1 should have steps 1 and 2 in parallel
        assertTrue(plan.getStage(1).isParallel());
        assertEquals(2, plan.getStage(1).getStepCount());

        System.out.println("✅ Diamond dependency test passed");
        System.out.println(plan.prettyPrint());
    }
}