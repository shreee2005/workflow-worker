# workflow-worker

A Spring Boot 4 / Java 17 DAG-based workflow engine with distributed locking, retry logic, and step executors.

## Stack
- **Language:** Java 17
- **Framework:** Spring Boot 4.0.1
- **Build:** Maven (./mvnw)
- **Database:** PostgreSQL (via Spring Data JPA + Flyway migrations)
- **Messaging:** RabbitMQ (Spring AMQP)
- **Observability:** OpenTelemetry (Zipkin traces), Micrometer Prometheus metrics

## Key source layout
```
src/main/java/com/workflow_worker/demo/
├── dag/           # DAG parsing, execution planning, context resolution
├── engine/        # WorkflowExecutor, StepDispatcher, retry, distributed lock, events
├── executers/     # Step executor implementations (HTTP, Log, registry)
├── worker/        # RabbitMQ consumers, event listeners, metrics
├── service/       # WorkflowRunService, WorkflowRunStepService
├── entity/        # JPA entities (Workflow, WorkflowRun, WorkflowRunStep, …)
├── repository/    # Spring Data repositories
├── config/        # RabbitConfig, OpenTelemetryConfig
└── messaging/     # Message types (WorkflowJobMessage, WorkflowDlqMessage)
```

## External dependencies (required to run)
- PostgreSQL at `postgres:5432`, database `workflow_dev`, user `wf_user`
- RabbitMQ at `rabbitmq:5672`, user `wf_rabbit`
- (Optional) Zipkin at `zipkin:9411` for distributed tracing

## Configuration
`src/main/resources/application.properties` — datasource, RabbitMQ, actuator, and OTel settings.

DB migrations live in `src/main/resources/db/migration/`.

## User preferences
