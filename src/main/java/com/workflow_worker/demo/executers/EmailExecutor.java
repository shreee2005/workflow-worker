package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@PluginExecutor("EMAIL_SEND")
public class EmailExecutor implements WorkflowPlugin {

    private final JavaMailSender defaultMailSender;

    public EmailExecutor(JavaMailSender defaultMailSender) {
        this.defaultMailSender = defaultMailSender;
    }

    @Override
    public String getName() {
        return "Email Notifier";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<String, String> getInputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("to", "Recipient email address (required)");
        schema.put("subject", "Email subject line (required)");
        schema.put("body", "Email plain text body content (required)");
        schema.put("smtpHost", "Optional: Custom SMTP Host to override system settings");
        schema.put("smtpPort", "Optional: Custom SMTP Port");
        schema.put("username", "Optional: Custom SMTP Username");
        schema.put("password", "Optional: Custom SMTP Password");
        return schema;
    }

    @Override
    public Map<String, String> getOutputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("status", "Status of email dispatch (e.g. SENT)");
        return schema;
    }

    @Override
    public void validate(StepDefinition step) throws Exception {
        Map<String, Object> config = step.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("EmailExecutor missing configuration");
        }
        if (config.get("to") == null || String.valueOf(config.get("to")).isBlank()) {
            throw new IllegalArgumentException("EmailExecutor missing 'to' configuration");
        }
        if (config.get("subject") == null || String.valueOf(config.get("subject")).isBlank()) {
            throw new IllegalArgumentException("EmailExecutor missing 'subject' configuration");
        }
        if (config.get("body") == null || String.valueOf(config.get("body")).isBlank()) {
            throw new IllegalArgumentException("EmailExecutor missing 'body' configuration");
        }
    }

    @Override
    public String execute(StepDefinition step, String payload) throws Exception {
        Map<String, Object> config = step.getConfig();
        String to = String.valueOf(config.get("to")).trim();
        String subject = String.valueOf(config.get("subject"));
        String body = String.valueOf(config.get("body"));

        JavaMailSender senderToUse = defaultMailSender;

        // Check if custom SMTP configuration is provided
        String customHost = config.get("smtpHost") != null ? String.valueOf(config.get("smtpHost")).trim() : null;
        if (customHost != null && !customHost.isEmpty()) {
            JavaMailSenderImpl customSender = new JavaMailSenderImpl();
            customSender.setHost(customHost);

            int port = 587;
            if (config.get("smtpPort") != null) {
                try {
                    port = Integer.parseInt(String.valueOf(config.get("smtpPort")).trim());
                } catch (NumberFormatException ignored) {}
            }
            customSender.setPort(port);

            String username = config.get("username") != null ? String.valueOf(config.get("username")).trim() : null;
            String password = config.get("password") != null ? String.valueOf(config.get("password")) : null;
            if (username != null && !username.isEmpty()) {
                customSender.setUsername(username);
            }
            if (password != null && !password.isEmpty()) {
                customSender.setPassword(password);
            }

            Properties props = customSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            senderToUse = customSender;
        }

        if (senderToUse == null) {
            throw new IllegalStateException("SMTP Mail Sender is not configured. Configure Spring SMTP globally or supply custom smtpHost configuration in the step.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        senderToUse.send(message);

        return "{\"status\": \"SENT\"}";
    }
}
