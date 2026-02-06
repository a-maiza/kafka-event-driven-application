# Requirements — Designing Event-Driven Applications (Kafka Ecosystem)
# Spring Boot / Maven / Java 21 / Kafka / Kafka Streams / Kafka Connect / Avro + Schema Registry
# CI/CD GitHub Actions → Kubernetes

## 1. Objective
Build a production-ready, event-driven microservices application (EDA) using the Apache Kafka ecosystem:
- Ingestion via REST APIs
- Event publishing/consumption with Kafka
- Real-time processing with Kafka Streams
- External integrations via Kafka Connect
- Strong data contracts with Avro + Schema Registry
- Automated testing (unit + integration) and CI/CD to Kubernetes

---

## 2. Functional Scope (MVP)
Example business domain (minimal e-commerce, adaptable):
1) Create an order via REST (Order Service)
2) Publish `OrderCreated` to Kafka
3) Payment authorization (Payment Service) → `PaymentAuthorized` or `PaymentFailed`
4) Stock reservation (Inventory Service) → `StockReserved` or `StockRejected`
5) Build read projections (Query Service) fed by events (CQRS read side)
6) Compute real-time KPIs (Kafka Streams) over order statuses
7) Integrate external systems (Kafka Connect):
   - Export events to a database using a JDBC Sink connector
   - Optionally ingest CDC events (Debezium) into Kafka

Out of scope (MVP):
- Complex saga orchestration with distributed transactions
- Full IAM / advanced security (can be added as MVP+)

---

## 3. Technical Constraints
- Language: Java 21
- Build: Maven
- Framework: Spring Boot 4.x
- Kafka
- Packaging: Docker images for each service
- Local runtime: Docker Compose (Kafka + UI + Schema Registry + Connect + optional Postgres)

### 3.1 Serialization & Contract (Mandatory)
- **Avro is mandatory for all inter-service Kafka communication.**
  - All Kafka topics must use Avro-encoded values (and keys if needed).
  - A **Schema Registry is required** (Confluent Schema Registry or Apicurio Registry).
  - Compatibility policy: **BACKWARD** (default) with explicit evolution rules.
  - Schema naming strategy: `TopicNameStrategy` (recommended) or `RecordNameStrategy` (must be consistent across all services).
  - Breaking changes must not happen within the same topic version (`.v1`). Use `.v2` for breaking changes.

### 3.2 Ordering, Idempotency, Delivery
- Kafka message key MUST be `orderId` for all order-related topics to guarantee ordering per order.
- Consumers MUST be idempotent:
  - Deduplicate by `eventId` (preferred) stored in a local store/DB or cache with TTL
  - OR implement natural idempotency (upserts, state checks) where possible
- Retries must be bounded; unrecoverable errors must go to DLQ.

### 3.3 Observability (Mandatory)
- Spring Boot Actuator enabled per service:
  - `/actuator/health`, `/actuator/info`, `/actuator/metrics` (as needed)
- Structured logging and correlation id propagation:
  - incoming HTTP request generates/propagates `correlationId`
  - Kafka headers include `correlationId`
- Metrics via Micrometer; exportable to Prometheus (MVP+).

---

## 4. Non-Functional Requirements
- Resilience:
  - Controlled retries with backoff
  - DLQ for non-recoverable failures
- Traceability:
  - End-to-end correlation id from REST → Kafka → downstream services
- Performance:
  - Handle at least N events/sec in dev baseline (define target later)
- Security (MVP+):
  - OAuth2/JWT for REST endpoints
  - Kafka TLS/SASL and ACLs (cluster-dependent)
- CI/CD (Mandatory):
  - GitHub Actions pipelines for build/test, container build/push, and Kubernetes deploy.

---

## 5. Domain Model & Events

### 5.1 Domain Model (MVP)
Order:
- id (string/uuid)
- customerId (string)
- lines: [{ sku (string), qty (int) }]
- total (decimal)
- status (enum)
- createdAt (ISO-8601 string)

### 5.2 Topics (Versioned)
- `orders.v1`:
  - OrderCreated
  - OrderCancelled (optional)
- `payments.v1`:
  - PaymentAuthorized
  - PaymentFailed
- `inventory.v1`:
  - StockReserved
  - StockRejected
- `order-status.v1`:
  - OrderStatusChanged (composed/aggregated event)
- `dead-letter.v1`:
  - Dead Letter topic (either shared or per-service, e.g. `order-service.dlq.v1`)

### 5.3 Event Envelope (Recommended, Avro Record)
Every event SHOULD use a consistent envelope record:
- eventId (string UUID)
- type (string)
- version (int)
- occurredAt (string ISO-8601)
- producer (string service name)
- correlationId (string)
- payload (specific Avro record OR union)

### 5.4 Schema Registry & Avro Rules (Mandatory)
- Each event payload is defined by an Avro schema registered in the Schema Registry.
- Subjects follow the chosen naming strategy (e.g., `<topic>-value` for TopicNameStrategy).
- Compatibility rules (BACKWARD):
  - Add new fields as optional with defaults
  - Never remove/rename required fields within the same topic version
  - Prefer adding fields over changing semantics
  - Use a new topic version (`.v2`) for breaking changes
- CI MUST validate Avro schemas and enforce compatibility (against registry or baseline).

---

## 6. Microservices Architecture

### 6.1 Services
1) **order-service**
   - REST: `POST /orders`, `GET /orders/{id}`
   - Produces to `orders.v1` (`OrderCreated`)
   - Stores order state (in-memory for MVP dev, Postgres for realistic env)

2) **payment-service**
   - Consumes `orders.v1` (`OrderCreated`)
   - Produces to `payments.v1`

3) **inventory-service**
   - Consumes `orders.v1` (`OrderCreated`)
   - Produces to `inventory.v1`

4) **status-service** (aggregator / choreography helper)
   - Consumes `payments.v1` and `inventory.v1`
   - Aggregates partial outcomes per `orderId`
   - Produces `OrderStatusChanged` to `order-status.v1`

5) **query-service** (CQRS read model)
   - Consumes `orders.v1` and `order-status.v1`
   - Maintains a materialized view (in-memory MVP, DB recommended)
   - REST: `GET /orders/{id}` for read model

6) **streams-analytics-service** (Kafka Streams)
   - Consumes `order-status.v1`
   - Aggregates KPIs (counts by status, windowed metrics)
   - REST: `GET /kpis/status-counts`

7) **connect** (Kafka Connect)
   - Runs as infrastructure (Docker/Kubernetes)
   - Connectors:
     - JDBC Sink to export selected topics to Postgres
     - Optional Debezium Source for CDC ingestion

### 6.2 Topic Keys & Partitioning
- Key = `orderId` everywhere for order-related topics
- Partition count determined by expected throughput; must be configurable per environment

---

## 7. REST API (Minimal)

### 7.1 order-service
- `POST /orders`
  - body: `{ customerId, lines:[{sku, qty}], total }`
  - response 201: `{ orderId, status }`

- `GET /orders/{id}`
  - response 200: `{ id, customerId, lines, total, status, createdAt }`

### 7.2 query-service
- `GET /orders/{id}`
  - response 200: `{ order, paymentStatus, inventoryStatus, finalStatus }`

### 7.3 streams-analytics-service
- `GET /kpis/status-counts`
  - response 200: `{ AUTHORIZED: 10, FAILED: 2, RESERVED: 8, ... }`

---

## 8. Patterns & Guarantees
- Event choreography: services react to events; no strict central orchestrator required.
- Status aggregation: `status-service` composes outcomes for a clean `order-status.v1`.
- Error handling:
  - Retries (bounded)
  - DLQ for poison messages
  - Optional retry topics (MVP+)
- Schema governance:
  - Schemas are versioned and validated in CI
  - Services fail fast if Schema Registry is unreachable or misconfigured (non-dev)

---

## 9. Testing Requirements

### 9.1 Unit Tests (Mandatory)
- JUnit 5 + Mockito
- Focus:
  - Validation rules
  - Mapping between REST DTOs and events
  - Business decisions (payment/stock rules)
  - Kafka Streams topology logic (TopologyTestDriver recommended)

### 9.2 Integration Tests (Mandatory)
- Testcontainers Kafka for end-to-end messaging tests:
  - publish to topic, assert consumer behavior, assert produced events
- Integration tests must cover:
  - producer serialization with Avro
  - consumer deserialization with Avro
  - DLQ behavior (force exceptions to validate)
- Optional: Testcontainers for Postgres (for query-service materialized view)

### 9.3 Streams Tests
- TopologyTestDriver tests are mandatory
- Optional: integration tests with Kafka broker for full pipeline verification

---

## 10. Deliverables
- Maven multi-module repository:
  - `common/` (shared event envelope + generated Avro classes + utilities)
  - `order-service/`, `payment-service/`, `inventory-service/`, `status-service/`, `query-service/`, `streams-analytics-service/`
- Schemas:
  - Avro schemas module/folder (e.g., `schemas/` or `common-schema/`)
  - Maven build generates Java classes from Avro schemas
- Infrastructure:
  - Docker Compose for local development:
    - Kafka (KRaft)
    - Schema Registry
    - Kafka UI (recommended)
    - Kafka Connect
    - Optional Postgres
- CI/CD:
  - GitHub Actions workflows:
    - `ci.yml` (build + unit tests + integration tests)
    - `cd.yml` (build images + push + deploy to Kubernetes)
- Kubernetes:
  - Helm charts (recommended) OR Kustomize manifests
  - Environment overlays: dev/staging/prod
  - ConfigMaps/Secrets templates
  - Ingress + TLS (prod)
  - Optional: HPA and PodDisruptionBudgets (MVP+)

---

## 11. Local Infrastructure (Docker Compose)
Local environment MUST include:
- Kafka (KRaft)
- Schema Registry
- Kafka UI (optional but recommended)
- Kafka Connect (required for integration phase; optional in strict MVP)
- Optional Postgres for projections / sinks

Networking principle (recommended):
- Kafka internal listener for containers: `kafka:9092`
- Kafka external listener for host apps: `localhost:29092`

---

## 12. Automatic Kafka Setup (Development)
- Topic creation:
  - In dev profile, services may auto-create topics at startup using Kafka AdminClient (`KafkaAdmin` + `NewTopic`)
  - In staging/prod, topics should be managed externally (IaC), and auto-create should be disabled.
- DLQ:
  - Default error handler configured in each consumer service to publish failed records to DLQ topic.
- Correlation:
  - HTTP filter creates/propagates `correlationId`
  - Kafka producer adds `correlationId` to headers
  - Consumers log and propagate it downstream

---

## 13. Deployment to Kubernetes (GitHub Actions)

### 13.1 Containerization
- Each microservice must provide:
  - a `Dockerfile`
  - health endpoints via Actuator: `/actuator/health`, `/actuator/info`
- Image naming:
  - `<registry>/<org>/<service-name>:<git-sha>`
  - optional tag `:latest` for non-prod

### 13.2 Kubernetes Packaging (Mandatory)
Use one of:
- Helm charts (recommended), OR
- Kustomize overlays

Each microservice deployment MUST include:
- Deployment + Service
- ConfigMap (non-sensitive config)
- Secret (sensitive config)
- Liveness/readiness probes using Actuator endpoints
- Resource requests/limits

Optional (MVP+):
- HorizontalPodAutoscaler
- PodDisruptionBudget
- NetworkPolicies

### 13.3 Kubernetes Configuration (Required Variables)
Example env vars (may be mapped to Spring properties):
- `SPRING_PROFILES_ACTIVE=dev|staging|prod`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=<kafka:9092>`
- `SCHEMA_REGISTRY_URL=<http(s)://schema-registry:8081>`
- Security if applicable:
  - `KAFKA_SECURITY_PROTOCOL`
  - `KAFKA_SASL_MECHANISM`
  - `KAFKA_SASL_JAAS_CONFIG`
- Optional observability:
  - `OTEL_EXPORTER_OTLP_ENDPOINT`

### 13.4 GitHub Actions Pipeline Requirements (Mandatory)
CD pipeline must:
1) Checkout
2) Setup Java 21 + Maven cache
3) Run unit tests
4) Run integration tests (Testcontainers) (policy-based on PR/main)
5) Build Docker images
6) Authenticate to container registry (GHCR/ECR/GCR/ACR)
7) Push images tagged by commit SHA
8) Deploy to Kubernetes (Helm or kubectl/kustomize)
9) Optional smoke test:
   - call `/actuator/health` for deployed services

### 13.5 Environments & Approvals
- Branch strategy:
  - PR: CI only
  - main: deploy dev/staging
  - release tag: deploy production with manual approval
- Use GitHub Environments:
  - `dev`, `staging`, `prod`

### 13.6 Secrets Management
- No secrets committed in the repository.
- Use GitHub Encrypted Secrets and/or external secret manager:
  - Vault / AWS Secrets Manager / GCP Secret Manager / Azure Key Vault
  - Optional: External Secrets Operator in cluster

### 13.7 Rollback Strategy
- Helm: rollback via release revisions
- Kubernetes: rollback via `kubectl rollout undo`
- Keep deployment history and define rollback triggers (optional)

---

## 14. Implementation Steps (Recommended Order)
1) Initialize multi-module Maven repo (Java 21, Spring Boot 3.x)
2) Create `common` module:
   - Avro schemas + generated classes
   - Event envelope and shared utilities (correlation id)
3) Setup Docker Compose:
   - Kafka (KRaft), Schema Registry, Kafka UI, Kafka Connect, optional Postgres
4) Implement `order-service`:
   - REST `POST /orders`
   - produce `OrderCreated` (Avro) to `orders.v1`
   - unit + integration tests (Testcontainers)
5) Implement `payment-service` and `inventory-service`:
   - consume `orders.v1`, produce outcome events
   - DLQ and retries
   - integration tests verifying outputs
6) Implement `status-service`:
   - consume payment + inventory topics
   - aggregate and publish `order-status.v1`
7) Implement `query-service`:
   - consume order and status events
   - expose read API
8) Implement `streams-analytics-service`:
   - Kafka Streams aggregations + REST endpoint
   - TopologyTestDriver tests
9) Implement Kafka Connect connectors (JDBC sink, optional Debezium)
10) Add observability, harden configuration, prepare Kubernetes manifests
11) Implement GitHub Actions workflows for CI/CD
12) Deploy to Kubernetes dev/staging/prod

---
