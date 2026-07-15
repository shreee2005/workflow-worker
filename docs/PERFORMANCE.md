# Performance & Scaling Strategy

This document outlines the performance benchmarks, database indexing strategies, thread management configuration, and horizontal scaling strategy of the workflow engine.

---

## 1. Database Indexing & Query Optimization

To handle high volumes of workflow queries without query degradation, we implemented composite indexes on hot paths:

### Optimizations
*   `idx_workflow_run_steps_run_step` on `workflow_run_steps(run_id, step_index)`:
    *   *Purpose*: Speeds up context rebuilding. The engine reads step outputs using `findByRunIdOrderByStepIndexAsc`. This index turns a full table scan into an index-range scan.
*   `idx_workflow_runs_workflow_started` on `workflow_runs(workflow_id, started_at DESC)`:
    *   *Purpose*: Speeds up run history lookups in the dashboard. When users view a workflow, the UI queries the most recent runs. The index allows sorting and paging runs in $O(\log N)$ time.
*   `idx_workflow_runs_status` on `workflow_runs(status)`:
    *   *Purpose*: Speeds up recovery scans and statistics counts (e.g. counting active or waiting runs).

---

## 2. Thread Pool Management

When executing DAG stages containing parallel steps, the worker uses `CompletableFuture.supplyAsync()` to execute step actions. To prevent thread exhaustion (which would crash the worker process), we configure a bounded thread pool:

### Configuration
*   **Core Pool Size**: 20 (minimum active threads).
*   **Max Pool Size**: 100 (cap on concurrent step threads).
*   **Queue Capacity**: 500 (waiting execution slots).
*   **Rejection Policy**: `CallerRunsPolicy` (if the pool is saturated, the submitting thread runs the task, providing automatic backpressure).

This prevents OOM (Out Of Memory) errors when executing massive parallel workflows with dozens of concurrent branches.

---

## 3. Horizontal Scaling Strategy (10x to 100x)

To scale the engine from 100 concurrent workflows to 10,000+ concurrent workflows, we apply three scaling patterns:

```
                  [ API Load Balancer ]
                            │
               ┌────────────┼────────────┐
               ▼            ▼            ▼
           [ API 1 ]    [ API 2 ]    [ API 3 ]
               │            │            │
               └────────────┼────────────┘
                            ▼ (Publish)
                  [ RabbitMQ Cluster ]
                            │
               ┌────────────┼────────────┐
               ▼            ▼            ▼
          [ Worker 1 ] [ Worker 2 ] [ Worker 3 ]
               │            │            │
               └────────────┼────────────┘
                            ▼
          [ DB Read/Write Split / PgBouncer ]
                            │
                     [ PostgreSQL DB ]
```

### A. Stateless Worker Scaling (Compute)
*   **Worker Pool**: Worker nodes are entirely stateless. They retrieve tasks from RabbitMQ and lock execution via Redis.
*   **Action**: To scale compute capacity, spin up more worker Docker containers. RabbitMQ will automatically balance the task message distribution via round-robin.

### B. Redis Sentinel/Cluster (Locking)
*   **Lock Store**: Redis handles distributed locks.
*   **Action**: Use Redis Sentinel or a Redis Cluster to partition lock keys and ensure high availability of the locking layer.

### C. PostgreSQL Scaling (Database)
*   **Connection Pooling**: Install **PgBouncer** in front of PostgreSQL to handle connection pooling, preventing workers from exhausting the DB's native connection limits.
*   **Read/Write Split**: Configure Spring Data JPA to direct read queries (dashboard stats, historical scans) to read replicas, reserving the primary database node exclusively for execution updates and checkpoint commits.
*   **Partitioning**: Partition the `workflow_run_steps` table by `created_at` date, allowing old logs/outputs to be archived or pruned easily without locked table scans.
