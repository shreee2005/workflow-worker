package com.workflow_worker.demo.executers;

import com.workflow_worker.demo.worker.PluginExecutor;
import com.workflow_worker.demo.worker.WorkflowPlugin;
import com.workflow_worker.demo.workflow.StepDefinition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@PluginExecutor("S3_ACTION")
public class S3Executor implements WorkflowPlugin {

    private S3Client s3Client;

    @Override
    public String getName() {
        return "AWS S3 File Handler";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Map<String, String> getInputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("accessKey", "AWS access key credential (required)");
        schema.put("secretKey", "AWS secret key credential (required)");
        schema.put("region", "AWS region, e.g. us-east-1 (required)");
        schema.put("bucket", "Target S3 bucket name (required)");
        schema.put("key", "S3 object key path (required)");
        schema.put("action", "Operation to perform: UPLOAD or DOWNLOAD (required)");
        schema.put("content", "String content to upload (required if action is UPLOAD and localPath is empty)");
        schema.put("localPath", "Local file system path to upload from or download to (optional)");
        schema.put("endpoint", "Alternative S3 endpoint URI for local testing or MinIO (optional)");
        return schema;
    }

    @Override
    public Map<String, String> getOutputSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("result", "Contains operation success details or downloaded file content summary");
        return schema;
    }

    @Override
    public void validate(StepDefinition step) throws Exception {
        Map<String, Object> config = step.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("S3Executor missing configuration");
        }
        if (config.get("accessKey") == null || String.valueOf(config.get("accessKey")).isBlank()) {
            throw new IllegalArgumentException("S3Executor missing 'accessKey' configuration");
        }
        if (config.get("secretKey") == null || String.valueOf(config.get("secretKey")).isBlank()) {
            throw new IllegalArgumentException("S3Executor missing 'secretKey' configuration");
        }
        if (config.get("region") == null || String.valueOf(config.get("region")).isBlank()) {
            throw new IllegalArgumentException("S3Executor missing 'region' configuration");
        }
        if (config.get("bucket") == null || String.valueOf(config.get("bucket")).isBlank()) {
            throw new IllegalArgumentException("S3Executor missing 'bucket' configuration");
        }
        if (config.get("key") == null || String.valueOf(config.get("key")).isBlank()) {
            throw new IllegalArgumentException("S3Executor missing 'key' configuration");
        }
        String action = config.get("action") == null ? "" : String.valueOf(config.get("action")).trim().toUpperCase();
        if (!"UPLOAD".equals(action) && !"DOWNLOAD".equals(action)) {
            throw new IllegalArgumentException("S3Executor 'action' must be either UPLOAD or DOWNLOAD");
        }
        if ("UPLOAD".equals(action)) {
            boolean hasContent = config.get("content") != null && !String.valueOf(config.get("content")).isBlank();
            boolean hasLocalPath = config.get("localPath") != null && !String.valueOf(config.get("localPath")).isBlank();
            if (!hasContent && !hasLocalPath) {
                throw new IllegalArgumentException("S3Executor UPLOAD action requires either 'content' or 'localPath' configuration");
            }
        }
    }

    @Override
    public void init() throws Exception {
        // Will build the client inside execute since credentials reside in step configuration
    }

    @Override
    public String execute(StepDefinition step, String payload) throws Exception {
        Map<String, Object> config = step.getConfig();
        String accessKey = String.valueOf(config.get("accessKey")).trim();
        String secretKey = String.valueOf(config.get("secretKey")).trim();
        String regionStr = String.valueOf(config.get("region")).trim();
        String bucket = String.valueOf(config.get("bucket")).trim();
        String key = String.valueOf(config.get("key")).trim();
        String action = String.valueOf(config.get("action")).trim().toUpperCase();
        String endpoint = config.get("endpoint") != null ? String.valueOf(config.get("endpoint")).trim() : null;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(regionStr));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        s3Client = builder.build();

        if ("UPLOAD".equals(action)) {
            String content = config.get("content") != null ? String.valueOf(config.get("content")) : "";
            String localPath = config.get("localPath") != null ? String.valueOf(config.get("localPath")).trim() : null;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            if (localPath != null && !localPath.isBlank()) {
                s3Client.putObject(putRequest, RequestBody.fromFile(Paths.get(localPath)));
            } else {
                s3Client.putObject(putRequest, RequestBody.fromString(content, StandardCharsets.UTF_8));
            }

            return "{\"success\": true, \"message\": \"File uploaded successfully to S3 bucket " + bucket + "\"}";
        } else {
            String localPath = config.get("localPath") != null ? String.valueOf(config.get("localPath")).trim() : null;

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            if (localPath != null && !localPath.isBlank()) {
                s3Client.getObject(getRequest, ResponseTransformer.toFile(Paths.get(localPath)));
                return "{\"success\": true, \"message\": \"File downloaded successfully from S3 to " + localPath + "\"}";
            } else {
                String content = s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asUtf8String();
                Map<String, String> output = new HashMap<>();
                output.put("content", content);
                output.put("sizeBytes", String.valueOf(content.getBytes(StandardCharsets.UTF_8).length));
                return new ObjectMapper().writeValueAsString(output);
            }
        }
    }

    @Override
    public void cleanup() throws Exception {
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
