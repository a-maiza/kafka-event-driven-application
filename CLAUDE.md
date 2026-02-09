# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# IMPORTANT: Default Java is 25; this project requires Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Build all modules
mvn clean compile

# Run all tests
mvn test

# Run integration tests (requires Docker for Testcontainers)
mvn verify

# Build a single module
mvn clean compile -pl order-service -am    # -am builds dependent modules (common)

# Run tests for a single module
mvn test -pl payment-service

# Run a single test class
mvn test -pl order-service -Dtest=OrderControllerTest

# Start local infrastructure
docker compose up -d

# Check Schema Registry
curl http://localhost:8081/subjects
```

## Architecture

This is an **event-driven microservices application** using Apache Kafka with an e-commerce domain (orders, payments, inventory).

### Multi-Module Maven Structure

- **Parent POM** (`pom.xml`): `<packaging>pom</packaging>`, defines shared dependencies and version properties
- **common**: Shared Avro schemas (`.avsc`), event envelope, utilities (TopicNames, CorrelationIdUtils). Plain JAR, no Spring Boot plugin.
- **order-service** (8081): REST API (`POST/GET /orders`), produces `OrderCreated` to `orders.v1`
- **payment-service** (8082): Consumes `orders.v1`, produces `PaymentAuthorized`/`PaymentFailed` to `payments.v1`
- **inventory-service** (8083): Consumes `orders.v1`, produces `StockReserved`/`StockRejected` to `inventory.v1`
- **status-service** (8084): Consumes `payments.v1` + `inventory.v1`, aggregates into `OrderStatusChanged` on `order-status.v1`
- **query-service** (8085): CQRS read model, consumes `orders.v1` + `order-status.v1`, exposes `GET /orders/{id}`
- **streams-analytics-service** (8086): Kafka Streams KPI aggregation, exposes `GET /kpis/status-counts`

All service modules depend on `common` for Avro-generated classes. The event flow is: order-service → payment-service + inventory-service → status-service → query-service / streams-analytics-service.

### Kafka Topic Convention

All topics are versioned (e.g., `orders.v1`). Message key is always `orderId` for ordering guarantees. Every event uses an Avro envelope with `eventId`, `correlationId`, and typed payload.

### Serialization

Avro is mandatory for all inter-service Kafka communication. Schema Registry compatibility mode is BACKWARD. Confluent dependencies are hosted at `https://packages.confluent.io/maven/` (configured in parent POM).

## Key Version Properties (parent pom.xml)

| Property | Value |
|----------|-------|
| `java.version` | 21 |
| `avro.version` | 1.12.1 |
| `confluent.version` | 7.8.0 |
| `testcontainers.version` | 2.0.3 |
| Spring Boot | 4.0.2 |

## Local Infrastructure (docker-compose.yml)

3 ZooKeeper nodes + 3 Kafka brokers (Confluent 7.4.1), Schema Registry (:8081), Kafka Connect (:8083, Avro converters), REST Proxy (:8082), Kafka UI (:8080), PostgreSQL (:5432, db=orders, user=app, pw=app).

Kafka internal listeners: `broker-N:2909N`. External listeners: `localhost:909N`.

## Shell Quoting Note

Avoid single quotes containing apostrophes in `git commit -m`. Use `git commit -F <file>` for multi-line or complex messages.

## Implementation Roadmap

See [Tasks.md](Tasks.md) for the full 8-phase, 24-task implementation plan with dependencies. See [REQUIREMENTS.md](REQUIREMENTS.md) for the complete technical specification.

## Git workflow

## 1 Branch Creation

For **each task**, a dedicated branch **must** be created.

### 1.1 Branch Naming Convention
`<type-branch>/<task-number>-<brief-description`>

### 1.2 Allowed Branch Types

The `type-branch` must be one of the following:

- `feature` — new functionality
- `fix` — non-critical fixes
- `bug` — bug fixes
- `doc` — documentation only
- `test` — adding or updating tests
- `refactor` — refactoring without functional changes


---

## 2 Pull Requests (PR)

Each branch **must** be merged through a Pull Request.

### 2.1 Required PR Content

Every Pull Request **must include** the following sections:

#### 1. Summary of Changes
- A clear and concise description of what was changed
- Explain the purpose of the changes

#### 2. Testing Notes
- Describe tests performed (manual, automated, edge cases)
- Explicitly state if no testing was required

#### 3. Tasks Update
- Update the corresponding checkboxes in `Tasks.md`
- Only mark tasks as completed when they are fully done

---

## General Rules

- ❌ No direct commits to the `main` branch
- ✅ One task = one branch = one Pull Request
- 🧹 Branch names must be clear, readable, and written in English
- 📌 The task number must match the one defined in `Tasks.md`

---
