# Production Deployment Guide

This guide details the step-by-step process to package, configure, secure, and deploy the workflow engine project to a production cloud environment, focusing on **AWS (Amazon Web Services)**.

---

## 1. Secrets Management & Environment Isolation

### A. Environment Variables
Never hardcode credentials (DB passwords, SMTP tokens, API keys) in `application.properties`. Spring Boot automatically binds environment variables to properties.

Ensure the following variables are injected into your containers in production:

| Spring Property Key | Environment Variable Override | Description |
| :--- | :--- | :--- |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Production PostgreSQL JDBC URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | Production DB Username |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Production DB Password |
| `spring.rabbitmq.host` | `SPRING_RABBITMQ_HOST` | Production RabbitMQ Endpoint host |
| `spring.rabbitmq.username` | `SPRING_RABBITMQ_USERNAME` | Production RabbitMQ Username |
| `spring.rabbitmq.password` | `SPRING_RABBITMQ_PASSWORD` | Production RabbitMQ Password |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | Production Redis Endpoint host |

---

## 2. Containerization & Registries

### A. Local Build & Test
Verify the Docker build works locally:
```bash
# Build the Docker image using the multi-stage Dockerfile
docker build -f docker/Dockerfile -t workflow-worker:1.0.0 .
```

### B. Push to AWS ECR (Elastic Container Registry)
Create a private ECR repository on AWS, log in, and push the image:
```bash
# Log in to AWS ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Tag the image
docker tag workflow-worker:1.0.0 <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/workflow-worker:1.0.0

# Push the image
docker push <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/workflow-worker:1.0.0
```

---

## 3. AWS Production Architecture Design

For a scalable, highly available enterprise deployment, we deploy using AWS managed services:

```
                      [ Route 53 (DNS) ]
                              │
                              ▼
            [ Application Load Balancer (ALB) ]
                              │
               ┌──────────────┴──────────────┐ (HTTPS Routing)
               ▼                             ▼
   [ ECS Fargate API Service ]   [ ECS Fargate Worker Service ]
   (Auto-scaling task pool)      (Auto-scaling consumer pool)
               │                             │
        ┌──────┴──────┬──────────────┬───────┴──────┐
        ▼             ▼              ▼              ▼
  [ Amazon RDS ] [ Amazon MQ ] [ ElastiCache ] [ Amazon S3 ]
  (PostgreSQL)   (RabbitMQ)    (Redis Cache)   (File Uploads)
```

### Managed AWS Component Stack:
1.  **AWS ECS on Fargate (Compute)**: 
    *   Runs stateless API and Worker containers serverlessly. 
    *   Configure **Auto-Scaling Policies** based on CPU/Memory usage (for APIs) and RabbitMQ queue message count (for Workers).
2.  **Amazon RDS for PostgreSQL (Database)**: 
    *   A managed database with Multi-AZ replication enabled for automatic failover.
3.  **Amazon ElastiCache for Redis (Cache & Lock Store)**:
    *   Provides high-throughput locks and caching.
4.  **Amazon MQ for RabbitMQ (Message Broker)**:
    *   Fully managed RabbitMQ cluster. AWS handles OS patching, clustering, and broker replication.
5.  **AWS ALB (Application Load Balancer)**:
    *   Acts as the public gateway, routing traffic to API services and managing AWS Certificate Manager SSL/TLS certificates.

---

## 4. CI/CD Pipeline (GitHub Actions)

Create a GitHub Actions workflow under `.github/workflows/deploy.yml` in your repository. This pipeline automatically compiles code, runs tests, builds the container image, pushes it to ECR, and triggers an ECS Fargate deployment on every push to the `main` branch.

### `.github/workflows/deploy.yml` (For Interviews)
```yaml
name: Production CI/CD Pipeline

on:
  push:
    branches: [ main ]

permissions:
  id-token: write
  contents: read

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v3

      - name: Setup Java 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Compile and Test
        run: mvn clean test

  deploy-to-aws:
    needs: build-and-test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v3

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::<AWS_ACCOUNT_ID>:role/github-actions-ecs-deploy-role
          aws-region: us-east-1

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1

      - name: Build, Tag, and Push Image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          ECR_REPOSITORY: workflow-worker
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -f docker/Dockerfile -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          echo "::set-output name=image::$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG"

      - name: Download Task Definition
        run: |
          aws ecs describe-task-definition --task-definition workflow-worker --query taskDefinition > task-definition.json

      - name: Fill in New Image URI
        id: render-task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: task-definition.json
          container-name: worker
          image: ${{ steps.login-ecr.outputs.registry }}/workflow-worker:${{ github.sha }}

      - name: Deploy Task to ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: ${{ steps.render-task-def.outputs.task-definition }}
          service: workflow-worker-service
          cluster: workflow-production-cluster
          wait-for-service-stability: true
```
