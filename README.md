# ⚙️ DAG-based Distributed Workflow Engine

A production-ready, highly resilient, and extensible DAG (Directed Acyclic Graph) workflow orchestration engine built with **Java 17**, **Spring Boot 4**, **PostgreSQL**, **RabbitMQ**, and **Redis**.

It allows users to design complex workflows visually via a drag-and-drop React dashboard, resolve step dependencies to execute tasks in parallel, automatically recover from worker crashes, and dynamically extend capabilities with a custom Plugin SDK.

---

## 🚀 Key Features

*   **DAG Dependency Resolution**: Parses step configurations, builds dependency graphs, and groups tasks into sequential stages where independent steps run in parallel (using `CompletableFuture`).
*   **Crash Resilience & Recovery**: Checkpoints execution context variables and intermediate outputs to PostgreSQL after each stage. If a worker crashes mid-task, RabbitMQ requeues the run, and the next worker automatically resumes from the latest checkpoint.
*   **Distributed Locking**: Uses Redis locks to guarantee single-consumer execution and prevent duplicate run processing.
*   **Plug-and-Play Executor SDK**: Allows adding new step capabilities (like sending emails, executing SQL, or handling files) simply by creating a class with a `@PluginExecutor` annotation.
*   **Self-Describing Schema Forms**: Plugins programmatically declare their expected inputs and outputs. The frontend reads these schemas to auto-generate form fields dynamically.
*   **Interactive UI Dashboard**: Includes a visual canvas designer (React Flow), Gantt timeline execution monitor, and plugin catalog browser.

---

## 🏗️ System Architecture

```
[ UI Dashboard ] ──(HTTP)──► [ API Gateway ] ──(Jobs)──► [ RabbitMQ Queue ]
                                   │                           │
                                   ▼ (Read/Write)              ▼ (Consume)
                            [ PostgreSQL DB ] ◄────────── [ Worker Nodes ]
                                                               │
                                                               ▼ (Locks)
                                                         [ Redis Lock Cache ]
```

*   **API Gateway**: Exposes endpoints to trigger runs, manage workflows, and query schemas.
*   **Worker Pool**: Stateless Java nodes consuming messages from RabbitMQ and running executor lifecycles.
*   **PostgreSQL**: Schema store for definitions, history, logs, and checkpoints (`workflow_states`).
*   **Redis**: Key-value store for distributed lock leases.

---

## 📂 Detailed Documentation Links

To understand the core design choices, schemas, and performance targets, please review the detailed guides under `/docs`:

1.  **[Architecture Documentation](docs/ARCHITECTURE.md)**: Diagrams showing system component connections, PostgreSQL entity relationship schemas, and execution sequence flows (happy path, callback, recovery).
2.  **[Architectural Decision Records (ADR)](docs/DECISIONS.md)**: Engineering rationales behind selecting RabbitMQ over DB polling, DAG support, Checkpointing over event sourcing, and Redis locks over DB version checks.
3.  **[Lessons Learned & Failures Solved](docs/LESSONS-LEARNED.md)**: Real challenges faced and resolved, including thread-safety under parallel load (using `ConcurrentHashMap`) and backpressure retry storms.
4.  **[Performance & Scaling Strategy](docs/PERFORMANCE.md)**: Thread pool configuration parameters, database index tuning, and horizontal scaling strategies (10x to 100x scaling).
5.  **[REST API Endpoints Guide](docs/API.md)**: Path parameters and payload JSON formats for workflows, runs, callbacks, and plugin catalog endpoints.

---

## ⚡ Quick Start (5 Minutes)

### Prerequisites
*   Docker & Docker Compose
*   Maven 3.9+ (or use `./mvnw`)
*   Java 17

### 1. Start the External Stack
Launch PostgreSQL, RabbitMQ, Redis, and Zipkin tracing using docker-compose:
```bash
docker-compose -f docker/docker-compose.yml up -d
```

### 2. Run the Worker Application
Compile the code and launch the Spring Boot worker process:
```bash
./mvnw clean compile spring-boot:run
```

### 3. Trigger a Sample Workflow Run
Examine the database migrations, launch a run via curl, and monitor the terminal trace console. You can verify all unit and integration tests compile and execute successfully using:
```bash
./mvnw test
```

---

## 📝 Example JSON Workflow Spec (DAG Diamond Pattern)

Below is an example of a 4-step workflow. **Step 1** and **Step 2** execute in parallel once **Step 0** finishes. **Step 3** executes once both Step 1 and Step 2 complete:

```json
{
  "steps": [
    {
      "type": "LOG",
      "name": "start_pipeline",
      "config": { "message": "Starting DAG processing" }
    },
    {
      "type": "HTTP_CALL",
      "name": "fetch_user_data",
      "dependsOn": [0],
      "config": {
        "url": "https://api.example.com/users",
        "method": "GET"
      }
    },
    {
      "type": "DATABASE_QUERY",
      "name": "fetch_meta",
      "dependsOn": [0],
      "config": {
        "jdbcUrl": "jdbc:postgresql://localhost:5432/db",
        "username": "user",
        "password": "password",
        "sql": "SELECT count(*) FROM user_metadata"
      }
    },
    {
      "type": "SLACK_NOTIFIER",
      "name": "notify_results",
      "dependsOn": [1, 2],
      "config": {
        "webhookUrl": "https://hooks.slack.com/services/XYZ",
        "message": "User Data Fetch completed. Total users processed: ${steps[2].output.count}"
      }
    }
  ]
}
```
