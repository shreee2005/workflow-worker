# Architectural Decision Records (ADR)

This document explains the key engineering decisions, trade-offs, and design choices made during the development of the workflow engine.

---

## 1. Message Broker (RabbitMQ) vs. Database Polling

### Context
We needed a mechanism to distribute workflow execution tasks to a pool of worker nodes. 

### Options
1.  **Database Polling**: Workers run a background cron query (e.g. `SELECT * FROM workflow_runs WHERE status = 'QUEUED' LIMIT 1 FOR UPDATE SKIP LOCKED`) every second.
2.  **Message Queue (RabbitMQ)**: API publishes a message containing `{runId}` to a RabbitMQ queue, and workers subscribe to consume messages.

### Decision
We chose **RabbitMQ**.

### Rationale
*   **Database Scalability**: Polling forces constant read/write load on PostgreSQL. As the number of concurrent workflows grows (e.g. to thousands per minute), DB polling creates massive CPU spikes and transaction locking contention. RabbitMQ keeps DB operations limited to actual state modifications.
*   **Push vs. Pull Latency**: DB polling introduces a latency delay (up to 1 second depending on cron interval). RabbitMQ delivers tasks to workers instantly via push subscriptions.
*   **Worker Backpressure**: RabbitMQ uses the `basic.qos` prefetch configuration. If a worker node is busy executing 5 parallel steps, it won't be pushed more tasks. Polling would require manual, complex worker-side thread count checks.
*   **Automatic Crash Recovery**: If a worker crashes mid-task, RabbitMQ detects the TCP connection loss and automatically requeues the message so another worker can resume execution. DB polling would require a complex "heartbeat monitor" to find and release dead runs.

---

## 2. DAG-based Workflows vs. Linear Workflows

### Context
Workflows need to execute a set of steps. 

### Options
1.  **Linear / Sequential Workflows**: Steps execute in order: Step 0 $\rightarrow$ Step 1 $\rightarrow$ Step 2 $\rightarrow$ Step 3.
2.  **DAG (Directed Acyclic Graph) Workflows**: Steps declare dependencies (`dependsOn`). The engine parses the graph and resolves which steps can run in parallel (e.g. Step 1 and 2 run simultaneously once Step 0 finishes).

### Decision
We chose **DAG Workflows**.

### Rationale
*   **Execution Speed (Parallelism)**: Many real-world automation tasks (like sending a Slack message, calling an external API, and uploading files to S3) do not depend on each other. Running them in parallel via `CompletableFuture.allOf()` reduces the total execution time of the stage to the duration of the slowest step, rather than the sum of all steps.
*   **SaaS Extensibility**: Modern enterprise platforms (like Zapier or GitHub Actions) require complex branching paths, not just simple line-by-line scripts. Starting with a DAG resolver (`DagParser.java`) prevents architectural dead-ends.

---

## 3. Checkpointing vs. Event Replay (Event Sourcing)

### Context
When a worker crashes, the engine needs to recover the state of active runs without losing context variables or re-executing completed operations.

### Options
1.  **Event Replay (Re-run from Scratch)**: Start execution from Step 0 and re-execute everything.
2.  **Checkpointing**: Snapshot the serialized context variables and last completed step index into `workflow_states` after each stage. On recovery, load the snapshot and skip completed steps.

### Decision
We chose **Checkpointing**.

### Rationale
*   **Side-Effect Mitigation**: Re-running steps from scratch would re-trigger external API requests (e.g. charging a card, posting to Slack, uploading files to S3). Re-executing non-idempotent steps creates duplicate side-effects.
*   **Performance**: If a workflow fails at Step 99 of 100, replaying 98 steps wastes computational time and network bandwidth. Checkpointing allows resuming directly from Step 99.

---

## 4. Redis Distributed Locking vs. Database Optimistic Locking

### Context
When multiple workers consume messages, we must guarantee that two workers never execute the same run ID simultaneously.

### Options
1.  **Optimistic DB Locking**: Use a `@Version` column in JPA. If two workers write updates simultaneously, one transaction fails with an `OptimisticLockException`.
2.  **Redis Distributed Locking**: Workers must acquire a TTL-backed key `workflow:lock:{runId}` in Redis before executing.

### Decision
We chose **Redis Distributed Locking**.

### Rationale
*   **Eager Failure vs. Delayed Conflict**: Optimistic locking only detects conflicts at commit time—meaning a worker might waste 30 seconds running expensive steps before discovering it was a duplicate run and failing to commit. Redis locks block duplicate workers *before* any execution begins.
*   **Lock Expiry**: If a worker crashes while holding the lock, Redis automatically expires the lock key (10-minute TTL), allowing the requeued message to be picked up and resumed by another worker without manual DBA intervention.
