package com.workflow_worker.demo.dag;

import com.workflow_worker.demo.engine.StepDispatcher;
import com.workflow_worker.demo.engine.StepExecutionResult;
import com.workflow_worker.demo.executers.DatabaseExecutor;
import com.workflow_worker.demo.executers.HttpExecutor;
import com.workflow_worker.demo.executers.SlackNotifier;
import com.workflow_worker.demo.executers.EmailExecutor;
import com.workflow_worker.demo.executers.StepExecutorRegistry;
import org.springframework.mail.javamail.JavaMailSender;
import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PluginLifecycleTest {

    private StepExecutorRegistry stepExecutorRegistry;
    private com.workflow_worker.demo.executers.PluginRegistry pluginRegistry;
    private StepDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        stepExecutorRegistry = mock(StepExecutorRegistry.class);
    }

    @Test
    void testPluginLifecycleExecutionOrder() throws Exception {
        UUID runId = UUID.randomUUID();
        WorkflowPlugin mockPlugin = mock(WorkflowPlugin.class);
        when(mockPlugin.getType()).thenReturn("TEST_PLUGIN");
        when(mockPlugin.execute(any(), any())).thenReturn("TEST_OUTPUT");

        // Set up registry manually with mock
        pluginRegistry = new com.workflow_worker.demo.executers.PluginRegistry(List.of(mockPlugin));

        // Stub class annotation behavior since mockito doesn't mock annotations by default
        // We will directly instantiate a test class or use registry get mock
        WorkflowPlugin spyPlugin = spy(new WorkflowPlugin() {
            @Override
            public String getName() { return "Spy Plugin"; }
            @Override
            public String getVersion() { return "1.0.0"; }
            @Override
            public Map<String, String> getInputSchema() { return Collections.emptyMap(); }
            @Override
            public Map<String, String> getOutputSchema() { return Collections.emptyMap(); }
            @Override
            public String execute(StepDefinition step, String payload) { return "SPY_OUT"; }
        });

        // Set up registry with custom plugin
        com.workflow_worker.demo.executers.PluginRegistry customRegistry = mock(com.workflow_worker.demo.executers.PluginRegistry.class);
        when(customRegistry.get("SPY")).thenReturn(spyPlugin);

        dispatcher = new StepDispatcher(stepExecutorRegistry, customRegistry);

        StepDefinition step = new StepDefinition("SPY", Collections.emptyMap());
        StepExecutionResult result = dispatcher.dispatch(step, "payload");

        assertEquals(StepExecutionResult.Status.SUCCESS, result.getStatus());
        assertEquals("SPY_OUT", result.getOutput());

        // Verify lifecycle order: init -> validate -> execute -> cleanup
        InOrder inOrder = inOrder(spyPlugin);
        inOrder.verify(spyPlugin).init();
        inOrder.verify(spyPlugin).validate(step);
        inOrder.verify(spyPlugin).execute(step, "payload");
        inOrder.verify(spyPlugin).cleanup();
    }

    @Test
    void testPluginLifecycleCleanupOnFailure() throws Exception {
        WorkflowPlugin spyPlugin = spy(new WorkflowPlugin() {
            @Override
            public String getName() { return "Fail Plugin"; }
            @Override
            public String getVersion() { return "1.0.0"; }
            @Override
            public Map<String, String> getInputSchema() { return Collections.emptyMap(); }
            @Override
            public Map<String, String> getOutputSchema() { return Collections.emptyMap(); }
            @Override
            public String execute(StepDefinition step, String payload) throws Exception {
                throw new RuntimeException("Execution Error");
            }
        });

        com.workflow_worker.demo.executers.PluginRegistry customRegistry = mock(com.workflow_worker.demo.executers.PluginRegistry.class);
        when(customRegistry.get("FAIL")).thenReturn(spyPlugin);

        dispatcher = new StepDispatcher(stepExecutorRegistry, customRegistry);

        StepDefinition step = new StepDefinition("FAIL", Collections.emptyMap());
        StepExecutionResult result = dispatcher.dispatch(step, "payload");

        assertEquals(StepExecutionResult.Status.FAILED, result.getStatus());
        assertEquals("Execution Error", result.getError());

        // Verify cleanup was still run even though execute threw exception
        verify(spyPlugin, times(1)).cleanup();
    }

    @Test
    void testHttpPluginValidation() {
        HttpExecutor executor = new HttpExecutor();
        
        // Missing URL
        StepDefinition invalidStep1 = new StepDefinition("HTTP_CALL", Collections.emptyMap());
        assertThrows(IllegalArgumentException.class, () -> executor.validate(invalidStep1));

        // Invalid Headers JSON
        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com");
        config.put("headers", "{invalid-json}");
        StepDefinition invalidStep2 = new StepDefinition("HTTP_CALL", config);
        assertThrows(IllegalArgumentException.class, () -> executor.validate(invalidStep2));

        // Valid setup
        Map<String, Object> validConfig = new HashMap<>();
        validConfig.put("url", "http://example.com");
        validConfig.put("headers", "{\"Authorization\": \"Bearer 123\"}");
        StepDefinition validStep = new StepDefinition("HTTP_CALL", validConfig);
        assertDoesNotThrow(() -> executor.validate(validStep));
    }

    @Test
    void testSlackPluginValidation() {
        SlackNotifier executor = new SlackNotifier();

        // Missing message
        Map<String, Object> config1 = new HashMap<>();
        config1.put("webhookUrl", "http://slack.webhook");
        StepDefinition invalidStep1 = new StepDefinition("SLACK_NOTIFIER", config1);
        assertThrows(IllegalArgumentException.class, () -> executor.validate(invalidStep1));

        // Valid
        Map<String, Object> config2 = new HashMap<>();
        config2.put("webhookUrl", "http://slack.webhook");
        config2.put("message", "hello slack");
        StepDefinition validStep = new StepDefinition("SLACK_NOTIFIER", config2);
        assertDoesNotThrow(() -> executor.validate(validStep));
    }

    @Test
    void testDatabasePluginValidation() {
        DatabaseExecutor executor = new DatabaseExecutor();

        Map<String, Object> config = new HashMap<>();
        config.put("jdbcUrl", "jdbc:postgresql://localhost:5432/db");
        config.put("username", "user");
        config.put("password", "pass");
        // Missing SQL
        StepDefinition invalidStep = new StepDefinition("DATABASE_QUERY", config);
        assertThrows(IllegalArgumentException.class, () -> executor.validate(invalidStep));

        // Valid
        config.put("sql", "SELECT 1");
        StepDefinition validStep = new StepDefinition("DATABASE_QUERY", config);
        assertDoesNotThrow(() -> executor.validate(validStep));
    }

    @Test
    void testEmailPluginValidation() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailExecutor executor = new EmailExecutor(mailSender);

        // Missing fields
        StepDefinition invalidStep = new StepDefinition("EMAIL_SEND", Collections.emptyMap());
        assertThrows(IllegalArgumentException.class, () -> executor.validate(invalidStep));

        // Valid
        Map<String, Object> config = new HashMap<>();
        config.put("to", "recipient@example.com");
        config.put("subject", "Test Subject");
        config.put("body", "Test Body");
        StepDefinition validStep = new StepDefinition("EMAIL_SEND", config);
        assertDoesNotThrow(() -> executor.validate(validStep));
    }

    @Test
    void testEmailPluginExecution() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailExecutor executor = new EmailExecutor(mailSender);

        Map<String, Object> config = new HashMap<>();
        config.put("to", "recipient@example.com");
        config.put("subject", "Test Subject");
        config.put("body", "Test Body");
        StepDefinition step = new StepDefinition("EMAIL_SEND", config);

        String result = executor.execute(step, "payload");

        assertTrue(result.contains("\"status\": \"SENT\""));
        verify(mailSender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
