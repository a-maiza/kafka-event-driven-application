# Implementation Tasks — Event-Driven Kafka Microservices

> Derived from [REQUIREMENTS.md](REQUIREMENTS.md)
> Stack: Java 21 / Spring Boot 4.x / Maven / Kafka / Kafka Streams / Kafka Connect / Avro + Schema Registry
> CI/CD: GitHub Actions → Kubernetes

---

## Phase 1 — Project Structure & Foundation

### Task 1.1: Convert to Multi-Module Maven Project
- [ ] Restructure root `pom.xml` as parent POM (`<packaging>pom</packaging>`)
- [ ] Define shared `<dependencyManagement>` for Spring Boot 4.x, Java 21, Kafka, Avro, Testcontainers
- [ ] Create sub-modules:
  - `common` (shared schemas, utilities)
  - `order-service`
  - `payment-service`
  - `inventory-service`
  - `status-service`
  - `query-service`
  - `streams-analytics-service`
- [ ] Remove current monolithic `src/main/java` source tree (replaced by per-service modules)
- **Acceptance**: `mvn clean compile` succeeds across all modules

### Task 1.2: Create `common` Module — Avro Schemas & Shared Utilities
- [x] Define Avro schemas (`.avsc`) for all events:
  - `EventEnvelope.avsc` — wrapper with: `eventId`, `type`, `version`, `occurredAt`, `producer`, `correlationId`, `payload`
  - `OrderCreated.avsc` — `id`, `customerId`, `lines[]` (sku, qty), `total`, `status`, `createdAt`
  - `OrderCancelled.avsc` (optional)
  - `PaymentAuthorized.avsc` — `orderId`, `amount`, `authorizedAt`
  - `PaymentFailed.avsc` — `orderId`, `reason`, `failedAt`
  - `StockReserved.avsc` — `orderId`, `lines[]` (sku, qty), `reservedAt`
  - `StockRejected.avsc` — `orderId`, `reason`, `rejectedAt`
  - `OrderStatusChanged.avsc` — `orderId`, `paymentStatus`, `inventoryStatus`, `finalStatus`, `updatedAt`
- [x] Configure `avro-maven-plugin` to generate Java classes from `.avsc` files
- [x] Add Schema Registry dependency (`io.confluent:kafka-avro-serializer`)
- [x] Implement shared utilities:
  - `TopicNames.java` — constants: `orders.v1`, `payments.v1`, `inventory.v1`, `order-status.v1`, `dead-letter.v1`
  - `CorrelationIdUtils.java` — generate UUID, get/set from Kafka headers, MDC integration
  - `EventEnvelopeBuilder.java` — factory for wrapping payloads in the envelope
- **Acceptance**: `mvn clean compile` generates Avro classes; utilities compile with no errors

### Task 1.3: Update Docker Compose for Full Local Infrastructure
- [x] Kafka brokers (3 ZooKeeper + 3 Kafka brokers, Confluent 7.4.1) with dual listeners:
  - Internal: `broker-N:2909N` (for containers)
  - External: `localhost:909N` (for host applications)
- [x] Confluent Schema Registry (`confluentinc/cp-schema-registry`) on port `8081`
- [x] Kafka UI (`provectuslabs/kafka-ui`) on port `8080`
- [x] Kafka Connect (`confluentinc/cp-kafka-connect`) with Avro converters on port `8083`
- [x] PostgreSQL (for query-service materialized view and JDBC Sink) on port `5432`
- [x] Shared Docker network for all services
- **Acceptance**: `docker-compose up -d` starts all containers; Schema Registry responds on `http://localhost:8081/subjects`

---

## Phase 2 — Core Microservices (Event Choreography)

### Task 2.1: Implement `order-service`
- [x] Spring Boot application with its own `application.yml`
- [x] REST API:
  - `POST /orders` — body: `{ customerId, lines:[{sku, qty}], total }` → response 201: `{ orderId, status }`
  - `GET /orders/{id}` — response 200: `{ id, customerId, lines, total, status, createdAt }`
- [x] Domain model: `Order` entity with `id` (UUID), `customerId`, `lines`, `total`, `status` (enum), `createdAt`
- [x] In-memory order store (`ConcurrentHashMap` for MVP)
- [x] Kafka producer: publish `OrderCreated` (Avro) to `orders.v1` with key = `orderId`
- [x] Auto-create topics in dev profile via `KafkaAdmin` + `NewTopic` beans
- [x] HTTP filter to generate/propagate `correlationId` + inject into Kafka headers
- [x] Spring Boot Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- **Acceptance**: POST creates order and publishes Avro event to `orders.v1`; GET returns stored order

### Task 2.2: Implement `payment-service`
- [ ] Kafka consumer: consume `OrderCreated` from `orders.v1` (Avro deserialization)
- [ ] Business logic: simulate payment authorization
  - Approve if `total < 1000` → produce `PaymentAuthorized`
  - Reject otherwise → produce `PaymentFailed`
- [ ] Kafka producer: publish to `payments.v1` with key = `orderId`
- [ ] Idempotency: deduplicate by `eventId` (in-memory set with TTL or bounded cache)
- [ ] Error handling: `DefaultErrorHandler` with bounded retries (3x exponential backoff) + DLQ
- [ ] Correlation ID: extract from Kafka headers → MDC → forward to produced events
- **Acceptance**: consuming an `OrderCreated` event produces the correct payment outcome to `payments.v1`

### Task 2.3: Implement `inventory-service`
- [ ] Kafka consumer: consume `OrderCreated` from `orders.v1` (Avro deserialization)
- [ ] Business logic: simulate stock reservation
  - Reserve if all SKUs available → produce `StockReserved`
  - Reject if any SKU unavailable → produce `StockRejected`
- [ ] Kafka producer: publish to `inventory.v1` with key = `orderId`
- [ ] Idempotency: deduplicate by `eventId`
- [ ] Error handling: bounded retries + DLQ
- [ ] Correlation ID propagation
- **Acceptance**: consuming an `OrderCreated` event produces the correct inventory outcome to `inventory.v1`

### Task 2.4: Implement `status-service` (Aggregator)
- [ ] Kafka consumer: consume from `payments.v1` AND `inventory.v1`
- [ ] Aggregation logic per `orderId`:
  - Track partial outcomes (payment result + inventory result) in `ConcurrentHashMap`
  - When both received → determine `finalStatus` and produce `OrderStatusChanged`
- [ ] Kafka producer: publish to `order-status.v1` with key = `orderId`
- [ ] Error handling: bounded retries + DLQ
- [ ] Correlation ID propagation
- **Acceptance**: after both payment and inventory events arrive for an order, `OrderStatusChanged` is published to `order-status.v1`

---

## Phase 3 — Read Model & Real-Time Analytics

### Task 3.1: Implement `query-service` (CQRS Read Side)
- [ ] Kafka consumer: consume `OrderCreated` from `orders.v1` and `OrderStatusChanged` from `order-status.v1`
- [ ] Materialized view: in-memory store combining order data + aggregated status
- [ ] REST API: `GET /orders/{id}` → `{ order, paymentStatus, inventoryStatus, finalStatus }`
- [ ] Idempotency handling for event replay
- [ ] Spring Boot Actuator enabled
- **Acceptance**: after the full event flow, `GET /orders/{id}` returns the complete order view with status

### Task 3.2: Implement `streams-analytics-service` (Kafka Streams)
- [ ] Kafka Streams topology:
  - Source: `order-status.v1`
  - Aggregate: count events by `finalStatus` (groupBy status → count)
  - Materialized state store: `status-counts-store`
- [ ] REST API: `GET /kpis/status-counts` → `{ AUTHORIZED: 10, FAILED: 2, RESERVED: 8, ... }`
- [ ] Interactive queries to expose state store via REST
- [ ] Avro Serde configuration for Kafka Streams
- **Acceptance**: after multiple status events, the KPI endpoint returns correct aggregated counts

---

## Phase 4 — Kafka Connect Integration

### Task 4.1: Configure JDBC Sink Connector
- [ ] Connector JSON configuration for JDBC Sink:
  - Export `order-status.v1` topic to PostgreSQL table
  - Use Avro converter with Schema Registry
  - Auto-create destination table
- [ ] Add JDBC driver and connector plugin to Kafka Connect Docker image
- [ ] Deployment script/instructions via Connect REST API (`POST /connectors`)
- **Acceptance**: events on `order-status.v1` are automatically written to a Postgres table

### Task 4.2: (Optional) Configure Debezium Source Connector
- [ ] Debezium PostgreSQL connector configuration for CDC ingestion
- [ ] Publish database changes to a Kafka topic
- **Acceptance**: inserts/updates in Postgres generate corresponding Kafka events

---

## Phase 5 — Testing

### Task 5.1: Unit Tests (All Services) — JUnit 5 + Mockito
- [ ] `order-service`:
  - Order validation rules (required fields, positive total)
  - REST DTO ↔ Avro event mapping
  - Controller layer tests (MockMvc)
- [ ] `payment-service`:
  - Payment authorization decision logic (threshold-based)
  - Event processing and outcome determination
- [ ] `inventory-service`:
  - Stock reservation logic (SKU availability)
  - Event processing and outcome determination
- [ ] `status-service`:
  - Aggregation logic (partial outcome tracking, final status computation)
- [ ] `query-service`:
  - Materialized view update logic
- [ ] `streams-analytics-service`:
  - **TopologyTestDriver** tests for Kafka Streams topology (mandatory)
  - Verify correct aggregation counts, edge cases (empty input, duplicate events)
- **Acceptance**: `mvn test` passes with >80% coverage on business logic

### Task 5.2: Integration Tests — Testcontainers
- [ ] Add `testcontainers` and `testcontainers-kafka` dependencies to parent POM
- [ ] Integration tests per service:
  - Producer serialization with Avro + Schema Registry container
  - Consumer deserialization with Avro
  - DLQ behavior: force exceptions → verify messages land in DLQ topic
  - End-to-end flow: publish `OrderCreated` → assert downstream events on `payments.v1`, `inventory.v1`, `order-status.v1`
- [ ] Optional: Testcontainers PostgreSQL for `query-service` materialized view
- **Acceptance**: `mvn verify` passes all integration tests with Testcontainers

### Task 5.3: Kafka Streams Tests
- [ ] TopologyTestDriver-based tests for `streams-analytics-service` (mandatory)
- [ ] Verify: aggregation correctness, windowed counts, state store queries
- [ ] Optional: integration test with Kafka broker for full pipeline verification
- **Acceptance**: all streams topology tests pass

---

## Phase 6 — Observability & Cross-Cutting Concerns

### Task 6.1: Structured Logging & Correlation ID Propagation
- [ ] HTTP servlet filter in each REST service:
  - Generate `correlationId` (UUID) if not present in incoming `X-Correlation-Id` header
  - Set in SLF4J MDC for structured log output
- [ ] Kafka producer interceptor/header enrichment: add `correlationId` to Kafka record headers
- [ ] Kafka consumer: extract `correlationId` from headers → set in MDC → forward downstream
- [ ] Configure structured JSON logging (Logback/Log4j2)
- **Acceptance**: a single request's `correlationId` is traceable across REST → Kafka → all downstream consumers in logs

### Task 6.2: Spring Boot Actuator & Metrics
- [ ] Enable `/actuator/health`, `/actuator/info`, `/actuator/metrics` per service
- [ ] Kafka consumer/producer metrics exposed via Micrometer
- [ ] Optional (MVP+): Prometheus metrics endpoint (`/actuator/prometheus`)
- **Acceptance**: all Actuator endpoints respond; Kafka metrics visible in `/actuator/metrics`

### Task 6.3: Error Handling — Retries & DLQ
- [ ] Configure `DefaultErrorHandler` per consumer service:
  - Bounded retries (e.g., 3 attempts with exponential backoff)
  - `DeadLetterPublishingRecoverer` for non-recoverable failures
- [ ] DLQ topics: `<service>.dlq.v1` or shared `dead-letter.v1`
- [ ] DLQ messages preserve original headers (`correlationId`, `eventId`) + error metadata
- **Acceptance**: poisoned messages are retried N times then land in DLQ with full context

---

## Phase 7 — Containerization, Kubernetes & CI/CD

### Task 7.1: Dockerfiles for Each Microservice
- [ ] Multi-stage Dockerfile per service:
  - Stage 1: Maven build (`maven:3-eclipse-temurin-21`)
  - Stage 2: Runtime (`eclipse-temurin:21-jre-alpine`)
- [ ] Expose service port and Actuator health check
- [ ] Image naming convention: `ghcr.io/<org>/<service-name>:<git-sha>` (+ `:latest` for non-prod)
- **Acceptance**: `docker build` succeeds for each service; container starts and `/actuator/health` returns UP

### Task 7.2: Kubernetes Manifests (Helm Charts)
- [ ] Per microservice:
  - `Deployment` with liveness probe (`/actuator/health/liveness`) and readiness probe (`/actuator/health/readiness`)
  - `Service` (ClusterIP)
  - `ConfigMap` for non-sensitive config (`SPRING_PROFILES_ACTIVE`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SCHEMA_REGISTRY_URL`)
  - `Secret` for sensitive config (Kafka SASL credentials, DB passwords)
  - Resource `requests` and `limits`
- [ ] Environment overlays: `dev`, `staging`, `prod`
- [ ] Ingress + TLS configuration for production
- [ ] Optional (MVP+): `HorizontalPodAutoscaler`, `PodDisruptionBudget`, `NetworkPolicies`
- **Acceptance**: `helm install` or `kubectl apply` deploys services; pods reach Ready state

### Task 7.3: GitHub Actions — CI Pipeline (`ci.yml`)
- [ ] Triggers: PR opened/updated, push to `main`
- [ ] Steps:
  1. Checkout code
  2. Setup Java 21 + Maven cache
  3. Run unit tests (`mvn test`)
  4. Run integration tests with Testcontainers (`mvn verify`)
  5. Validate Avro schema compatibility against Schema Registry (or baseline)
- **Acceptance**: CI pipeline runs on PRs and reports pass/fail

### Task 7.4: GitHub Actions — CD Pipeline (`cd.yml`)
- [ ] Triggers:
  - Push to `main` → deploy to `dev`/`staging`
  - Release tag → deploy to `prod` (with manual approval gate)
- [ ] Steps:
  1. Checkout
  2. Setup Java 21 + Maven cache
  3. Build + test
  4. Build Docker images for each service
  5. Authenticate to container registry (GHCR)
  6. Push images tagged by commit SHA
  7. Deploy to Kubernetes via Helm/kubectl
  8. Optional smoke test: `curl /actuator/health` for deployed services
- [ ] GitHub Environments: `dev`, `staging`, `prod`
- **Acceptance**: merge to `main` triggers automatic deployment to dev; release tag triggers gated prod deploy

---

## Phase 8 — Hardening & Governance

### Task 8.1: Schema Registry Governance
- [ ] Set compatibility mode to `BACKWARD` on Schema Registry
- [ ] Naming strategy: `TopicNameStrategy` consistently across all services
- [ ] CI step to validate schema compatibility before merge
- [ ] Fail-fast behavior if Schema Registry unreachable (non-dev profiles)
- **Acceptance**: incompatible schema changes are rejected in CI; services fail fast without Schema Registry in staging/prod

### Task 8.2: Secrets Management
- [ ] Zero secrets committed in repository (`.gitignore` for `.env`, credentials files)
- [ ] GitHub Encrypted Secrets for CI/CD pipelines
- [ ] Kubernetes Secret templates (values injected at deploy time)
- [ ] Optional: External Secrets Operator (Vault, AWS Secrets Manager, etc.)
- **Acceptance**: `git log` shows no committed secrets; CI/CD uses only encrypted secrets

### Task 8.3: Rollback Strategy
- [ ] Helm: rollback via `helm rollback <release> <revision>`
- [ ] Kubernetes: `kubectl rollout undo deployment/<service>`
- [ ] Keep deployment history (Helm default: 10 revisions)
- [ ] Document rollback triggers and procedure
- **Acceptance**: rollback procedure documented and tested in dev environment

---

## Task Dependency & Execution Order

| #    | Task                                   | Depends On          | Parallelizable With |
|------|----------------------------------------|---------------------|---------------------|
| 1.1  | Multi-module Maven structure           | —                   | 1.3                 |
| 1.2  | Common module (Avro + utilities)       | 1.1                 | 1.3                 |
| 1.3  | Docker Compose (full infra)            | —                   | 1.1                 |
| 2.1  | order-service                          | 1.2, 1.3            | —                   |
| 2.2  | payment-service                        | 1.2, 2.1            | 2.3                 |
| 2.3  | inventory-service                      | 1.2, 2.1            | 2.2                 |
| 2.4  | status-service                         | 2.2, 2.3            | —                   |
| 3.1  | query-service                          | 2.1, 2.4            | 3.2                 |
| 3.2  | streams-analytics-service              | 2.4                 | 3.1                 |
| 4.1  | JDBC Sink connector                    | 1.3, 2.4            | —                   |
| 4.2  | Debezium Source (optional)             | 4.1                 | —                   |
| 5.1  | Unit tests                             | Phases 2–3          | 6.x                 |
| 5.2  | Integration tests (Testcontainers)     | 5.1                 | —                   |
| 5.3  | Kafka Streams tests                    | 3.2                 | 5.1                 |
| 6.1  | Correlation ID propagation             | 1.2                 | Phase 2             |
| 6.2  | Actuator & metrics                     | Phases 2–3          | 6.1, 6.3            |
| 6.3  | Error handling (retries/DLQ)           | Phases 2–3          | 6.1, 6.2            |
| 7.1  | Dockerfiles                            | Phases 2–3          | —                   |
| 7.2  | Kubernetes manifests (Helm)            | 7.1                 | —                   |
| 7.3  | CI pipeline (GitHub Actions)           | 5.x                 | 7.1                 |
| 7.4  | CD pipeline (GitHub Actions)           | 7.1, 7.2, 7.3       | —                   |
| 8.1  | Schema governance                      | 1.2, 7.3            | 8.2, 8.3            |
| 8.2  | Secrets management                     | 7.2, 7.4            | 8.1, 8.3            |
| 8.3  | Rollback strategy                      | 7.2                 | 8.1, 8.2            |

---

## Summary

| Phase | Description                        | Tasks | Priority   |
|-------|------------------------------------|-------|------------|
| 1     | Project Structure & Foundation     | 3     | Critical   |
| 2     | Core Microservices                 | 4     | Critical   |
| 3     | Read Model & Analytics             | 2     | High       |
| 4     | Kafka Connect                      | 2     | Medium     |
| 5     | Testing                            | 3     | Critical   |
| 6     | Observability & Cross-Cutting      | 3     | High       |
| 7     | Containerization, K8s & CI/CD      | 4     | Critical   |
| 8     | Hardening & Governance             | 3     | Medium     |
| **Total** |                              | **24** |            |
