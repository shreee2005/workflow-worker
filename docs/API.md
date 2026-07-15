# REST API Documentation

This document describes the HTTP REST API endpoints exposed by the API Gateway service for workflow creation, run triggering, status monitoring, and plugin catalog discovery.

---

## 1. Workflows

### POST `/workflows`
Create a new workflow definition spec.
*   **Request Body**:
    ```json
    {
      "name": "Data Processing Pipeline",
      "spec": {
        "steps": [
          {
            "type": "LOG",
            "name": "start_log",
            "config": {
              "message": "Starting data fetch"
            }
          },
          {
            "type": "HTTP_CALL",
            "name": "fetch_api",
            "dependsOn": [0],
            "config": {
              "url": "https://api.example.com/data",
              "method": "GET"
            }
          }
        ]
      }
    }
    ```
*   **Response (201 Created)**:
    ```json
    {
      "id": "a4d8c6b7-a3f2-49df-8b1e-cc2f5e08b1a4",
      "name": "Data Processing Pipeline",
      "active": true,
      "createdAt": "2026-07-15T10:00:00Z"
    }
    ```

---

## 2. Execution Runs

### POST `/workflows/{id}/run`
Trigger a new run execution for a workflow.
*   **Request Body** (Optional custom payload passed to executors):
    ```json
    {
      "userId": "12345",
      "environment": "production"
    }
    ```
*   **Response (202 Accepted)**:
    ```json
    {
      "runId": "6f9b8c3d-2c1a-4d3b-8a5e-9f0e1a2b3c4d",
      "workflowId": "a4d8c6b7-a3f2-49df-8b1e-cc2f5e08b1a4",
      "status": "QUEUED",
      "attempt": 0,
      "startedAt": null
    }
    ```

### GET `/runs/{runId}`
Retrieve the full real-time status and logs of a workflow run.
*   **Response (200 OK)**:
    ```json
    {
      "runId": "6f9b8c3d-2c1a-4d3b-8a5e-9f0e1a2b3c4d",
      "status": "RUNNING",
      "attempt": 0,
      "startedAt": "2026-07-15T10:01:05Z",
      "finishedAt": null,
      "steps": [
        {
          "stepIndex": 0,
          "stepType": "LOG",
          "status": "SUCCEEDED",
          "logs": "Starting data fetch",
          "output": "Starting data fetch",
          "startedAt": "2026-07-15T10:01:06Z",
          "finishedAt": "2026-07-15T10:01:07Z"
        },
        {
          "stepIndex": 1,
          "stepType": "HTTP_CALL",
          "status": "RUNNING",
          "logs": null,
          "output": null,
          "startedAt": "2026-07-15T10:01:08Z",
          "finishedAt": null
        }
      ]
    }
    ```

---

## 3. Plugin Catalog

### GET `/plugins`
Retrieve a list of all available step executors along with their input/output schemas. Used by the UI dashboard to dynamically render node input forms.
*   **Response (200 OK)**:
    ```json
    [
      {
        "type": "SLACK_NOTIFIER",
        "name": "Slack Notifier",
        "version": "1.0.0",
        "inputSchema": {
          "webhookUrl": "Slack incoming webhook integration URL (required)",
          "message": "Text content of the notification message (required)"
        },
        "outputSchema": {
          "status": "Result status of the Slack post (e.g. SUCCESS)"
        }
      },
      {
        "type": "EMAIL_SEND",
        "name": "Email Notifier",
        "version": "1.0.0",
        "inputSchema": {
          "to": "Recipient email address (required)",
          "subject": "Email subject line (required)",
          "body": "Email plain text body content (required)",
          "smtpHost": "Optional: Custom SMTP Host to override system settings",
          "smtpPort": "Optional: Custom SMTP Port",
          "username": "Optional: Custom SMTP Username",
          "password": "Optional: Custom SMTP Password"
        },
        "outputSchema": {
          "status": "Status of email dispatch (e.g. SENT)"
        }
      }
    ]
    ```

---

## 4. Callbacks

### POST `/callback`
Resume a waiting run step (e.g. `WAIT_FOR_CALLBACK`). Called by external systems.
*   **Request Body**:
    ```json
    {
      "correlationId": "6f9b8c3d-2c1a-4d3b-8a5e-9f0e1a2b3c4d:3",
      "payload": "{\"approved\": true, \"reviewer\": \"admin\"}"
    }
    ```
*   **Response (200 OK)**:
    ```json
    {
      "success": true,
      "message": "Callback received. Workflow run resumed."
    }
    ```
