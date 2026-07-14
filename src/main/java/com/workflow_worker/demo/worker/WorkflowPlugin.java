package com.workflow_worker.demo.worker;

import com.workflow_worker.demo.workflow.StepDefinition;
import java.util.Map;

public interface WorkflowPlugin extends StepExecutor {

    /**
     * Human-readable name of the plugin (e.g. "Slack Notifier").
     */
    String getName();

    /**
     * Semantic version of the plugin (e.g. "1.0.0").
     */
    String getVersion();

    /**
     * Map of configuration keys to their descriptions/types that this plugin requires.
     */
    Map<String, String> getInputSchema();

    /**
     * Map of output keys to their descriptions/types that this plugin returns.
     */
    Map<String, String> getOutputSchema();

    /**
     * Lifecycle initialization: called before validate and execute.
     */
    default void init() throws Exception {}

    /**
     * Lifecycle validation: validates step configuration parameters.
     */
    default void validate(StepDefinition step) throws Exception {}

    /**
     * Lifecycle cleanup: called after execute finishes (even on failure) to release resources.
     */
    default void cleanup() throws Exception {}

    @Override
    default String getType() {
        PluginExecutor annotation = this.getClass().getAnnotation(PluginExecutor.class);
        return annotation != null ? annotation.value() : this.getClass().getSimpleName();
    }
}
