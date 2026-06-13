package com.workflow_worker.demo.workflow;

import java.util.Map;

public class StepDefinition {

    private String type;
    private String name;
    private Map<String, Object> config;
    public StepDefinition() {}

    public StepDefinition(String type, Map<String, Object> config) {
        this.type = type;
        this.config = config;
    }

    public StepDefinition(String type, String name, Map<String, Object> config) {
        this.type = type;
        this.name = name;
        this.config = config;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "StepDefinition{" +
                "type='" + type + '\'' +
                "name='" + name + '\'' +
                '}';
    }

}
