package com.workflow_worker.demo.worker;

import org.springframework.stereotype.Component;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface PluginExecutor {
    /**
     * The type name identifying this plugin in workflow specifications (e.g. "SLACK_NOTIFIER").
     */
    String value();
}
