package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PluginRegistry {

    private final Map<String, WorkflowPlugin> plugins = new HashMap<>();

    public PluginRegistry(List<WorkflowPlugin> pluginList) {
        for (WorkflowPlugin plugin : pluginList) {
            PluginExecutor annotation = plugin.getClass().getAnnotation(PluginExecutor.class);
            if (annotation != null) {
                String key = normalize(annotation.value());
                if (plugins.containsKey(key)) {
                    throw new RuntimeException("Duplicate PluginExecutor registration: " + key);
                }
                plugins.put(key, plugin);
            }
        }
    }

    public WorkflowPlugin get(String type) {
        return plugins.get(normalize(type));
    }

    public Collection<WorkflowPlugin> getAll() {
        return plugins.values();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
