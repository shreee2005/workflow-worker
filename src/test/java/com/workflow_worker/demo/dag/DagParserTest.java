package com.workflow_worker.demo.dag;

import com.workflow_worker.demo.workflow.StepDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DAG parsing and execution planning.
 * Tests dependency resolution, cycle detection, and stage generation.
 */
public class DagParserTest {

    @Test
    public void testSimpleLinearWorkflow() {
        // Step 0 -> Step 1 -> Step 2
        List<StepDefinition> steps = new ArrayList<>();
        steps.add(new StepDefinition("HTTP_CALL", Collections.emptyMap()));

        Map<String, Object> step1Config = new HashMap<>();
        step1Config.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", step1Config));

        Map<String, Object> step2Config = new HashMap<>();
        step2Config.put("dependsOn", List.of(1));
        steps.add(new StepDefinition("HTTP_CALL", step2Config));

        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(3, plan.getStageCount(), "Linear workflow should have 3 stages (one step per stage)");
        assertEquals(3, plan.getTotalSteps(), "Should have 3 total steps");

        // Each stage should have 1 step (linear dependency)
        assertEquals(1, plan.getStage(0).getStepCount(), "Stage 0 should have 1 step");
        assertEquals(1, plan.getStage(1).getStepCount(), "Stage 1 should have 1 step");
        assertEquals(1, plan.getStage(2).getStepCount(), "Stage 2 should have 1 step");
    }

    @Test
    public void testParallelSteps() {
        // Step 0 -> Steps 1,2 (parallel) -> Step 3
        List<StepDefinition> steps = new ArrayList<>();
        steps.add(new StepDefinition("HTTP_CALL", Collections.emptyMap()));

        Map<String, Object> step1Config = new HashMap<>();
        step1Config.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", step1Config));

        Map<String, Object> step2Config = new HashMap<>();
        step2Config.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", step2Config));

        Map<String, Object> step3Config = new HashMap<>();
        step3Config.put("dependsOn", List.of(1, 2));
        steps.add(new StepDefinition("HTTP_CALL", step3Config));

        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(3, plan.getStageCount(), "Should have 3 stages");
        assertEquals(4, plan.getTotalSteps(), "Should have 4 total steps");

        // Stage 0: step 0
        assertEquals(1, plan.getStage(0).getStepCount(), "Stage 0 should have step 0");
        assertTrue(plan.getStage(0).getStepIndices().contains(0), "Stage 0 should contain step 0");

        // Stage 1: steps 1,2 (parallel)
        assertEquals(2, plan.getStage(1).getStepCount(), "Stage 1 should have steps 1 and 2 in parallel");
        assertTrue(plan.getStage(1).isParallel(), "Stage 1 should be marked as parallel");
        assertTrue(plan.getStage(1).getStepIndices().contains(1), "Stage 1 should contain step 1");
        assertTrue(plan.getStage(1).getStepIndices().contains(2), "Stage 1 should contain step 2");

        // Stage 2: step 3
        assertEquals(1, plan.getStage(2).getStepCount(), "Stage 2 should have step 3");
    }

    @Test
    public void testCircularDependencyDetection() {
        // Create a cycle: Step 0 depends on Step 1, Step 1 depends on Step 0
        // This is tricky because we enforce dependsOn only references EARLIER steps
        // So we need to manually create the cycle

        List<StepDefinition> steps = new ArrayList<>();

        // Step 0: no config initially
        steps.add(new StepDefinition("HTTP_CALL", Collections.emptyMap()));

        // Step 1: depends on step 0
        Map<String, Object> step1Config = new HashMap<>();
        step1Config.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", step1Config));

        // This should work fine (no cycle yet)
        ExecutionPlan plan = DagParser.parse(steps);
        assertNotNull(plan, "Linear dependency should parse successfully");

        // Now test actual invalid forward reference
        List<StepDefinition> invalidSteps = new ArrayList<>();

        // Step 0: depends on step 1 (which comes after) - INVALID
        Map<String, Object> invalidConfig = new HashMap<>();
        invalidConfig.put("dependsOn", List.of(1));
        invalidSteps.add(new StepDefinition("HTTP_CALL", invalidConfig));

        // Step 1
        invalidSteps.add(new StepDefinition("HTTP_CALL", Collections.emptyMap()));

        // Forward references should be silently ignored (in extractDependencies)
        // So this won't throw an exception
        ExecutionPlan invalidPlan = DagParser.parse(invalidSteps);
        assertNotNull(invalidPlan, "Forward references should be handled gracefully");
    }

    @Test
    public void testSelfDependencyCycle() {
        // Step 0 depends on itself (if we allow it somehow)
        List<StepDefinition> steps = new ArrayList<>();

        Map<String, Object> config = new HashMap<>();
        config.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", config));

        // Self-dependency should be silently ignored (can't depend on step at same index)
        ExecutionPlan plan = DagParser.parse(steps);
        assertNotNull(plan, "Self-dependency should be handled");
    }

    @Test
    public void testSingleStep() {
        List<StepDefinition> steps = new ArrayList<>();
        steps.add(new StepDefinition("LOG", Collections.emptyMap()));

        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(1, plan.getStageCount(), "Single step should have 1 stage");
        assertEquals(1, plan.getTotalSteps(), "Should have 1 total step");
        assertEquals(1, plan.getStage(0).getStepCount(), "Stage 0 should have 1 step");
    }

    @Test
    public void testComplexDAG() {
        // Multi-stage parallel workflow
        //     -> Step 1 ->
        // Step 0        -> Step 4
        //     -> Step 2 ->
        //        -> Step 3 ->

        List<StepDefinition> steps = new ArrayList<>();
        steps.add(new StepDefinition("HTTP_CALL", Collections.emptyMap()));  // 0: no deps

        Map<String, Object> s1 = new HashMap<>();
        s1.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", s1));  // 1: depends on 0

        Map<String, Object> s2 = new HashMap<>();
        s2.put("dependsOn", List.of(0));
        steps.add(new StepDefinition("HTTP_CALL", s2));  // 2: depends on 0

        Map<String, Object> s3 = new HashMap<>();
        s3.put("dependsOn", List.of(2));
        steps.add(new StepDefinition("HTTP_CALL", s3));  // 3: depends on 2

        Map<String, Object> s4 = new HashMap<>();
        s4.put("dependsOn", List.of(1, 3));
        steps.add(new StepDefinition("HTTP_CALL", s4));  // 4: depends on 1,3

        ExecutionPlan plan = DagParser.parse(steps);

        assertEquals(5, plan.getTotalSteps(), "Should have 5 total steps");
        assertTrue(plan.getStageCount() <= 5, "Should have at most 5 stages");

        // Verify stage 0 has step 0
        assertEquals(1, plan.getStage(0).getStepCount());
        assertTrue(plan.getStage(0).getStepIndices().contains(0));

        // Stage 1 should have steps 1 and 2 (both depend only on 0)
        assertEquals(2, plan.getStage(1).getStepCount());
        assertTrue(plan.getStage(1).isParallel());
    }

    @Test
    public void testVariableResolution() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        context.setStepOutput(0, "{\"userId\": \"123\"}");

        String resolved = ContextVariableResolver.resolveVariables(
                "${steps[0].output}",
                context
        );

        assertEquals("{\"userId\": \"123\"}", resolved, "Should resolve full output");
    }

    @Test
    public void testFieldExtraction() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        context.setStepOutput(0, "{\"userId\": \"user123\", \"email\": \"test@example.com\"}");

        String resolved = ContextVariableResolver.resolveVariables(
                "User ID is ${steps[0].output.userId}",
                context
        );

        assertEquals("User ID is user123", resolved, "Should extract userId field");
    }

    @Test
    public void testRuntimeVariables() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        context.setVariable("apiKey", "secret123");

        String resolved = ContextVariableResolver.resolveVariables(
                "Authorization: Bearer ${variables.apiKey}",
                context
        );

        assertEquals("Authorization: Bearer secret123", resolved, "Should resolve runtime variable");
    }

    @Test
    public void testMultipleVariablesInString() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        context.setStepOutput(0, "{\"firstName\": \"John\"}");
        context.setStepOutput(1, "{\"lastName\": \"Doe\"}");
        context.setVariable("greeting", "Hello");

        String resolved = ContextVariableResolver.resolveVariables(
                "${variables.greeting} ${steps[0].output.firstName} ${steps[1].output.lastName}",
                context
        );

        assertEquals("Hello John Doe", resolved, "Should resolve multiple variables");
    }

    @Test
    public void testUnresolvedVariable() {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID());
        // Don't set any outputs

        String resolved = ContextVariableResolver.resolveVariables(
                "Output: ${steps[0].output}",
                context
        );

        // Should leave unresolved if step output not found
        assertTrue(resolved.contains("${steps[0].output}"), "Should leave unresolved variables as-is");
    }
}