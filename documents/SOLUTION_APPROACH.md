# eClaims — Electronic Insurance Claims Platform
## Solution Approach Document

**Version:** 1.0  
**Organization:** Nagarro Software Pvt. Ltd.  
**Document Date:** March 2026  
**Technology Stack:** Java 17 · Spring Boot 3.2 · React 18 · Apache Kafka · PostgreSQL · GCP

---

## Executive Summary

eClaims is a production-grade microservices platform for electronic insurance claim processing. The solution streamlines claim lifecycle management from First Notice of Loss (FNOL) through settlement, enabling multiple stakeholder portals (Customer, Adjustor, Surveyor, Partner) with real-time event-driven workflows.

**Key Achievements:**
- **7 Microservices** orchestrating 12-state claim lifecycle
- **Event-Driven Architecture** with Apache Kafka (11 topics, 3 partitions)
- **Role-Based Access Control** with OAuth2/Keycloak integration  
- **Cloud-Native Deployment** on GCP GKE with CI/CD automation
- **Production-Grade Quality** with SonarQube, OWASP, CodeQL compliance

---

## Table of Contents

1. [Technology Stack](#technology-stack)
2. [System Architecture](#system-architecture)
3. [Microservices Breakdown](#microservices-breakdown)
4. [Frontend Architecture](#frontend-architecture)
5. [Data & Integration Layer](#data--integration-layer)
6. [Security & Authentication](#security--authentication)
7. [GCP Deployment Architecture](#gcp-deployment-architecture)
8. [CI/CD Pipeline](#cicd-pipeline)
9. [Design Patterns & Best Practices](#design-patterns--best-practices)
10. [Testing Strategy](#testing-strategy)
11. [Monitoring & Observability](#monitoring--observability)
12. [Code Repository Structure](#code-repository-structure)

---

## Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Backend** | Java | 17 LTS | JVM runtime |
| **Framework** | Spring Boot | 3.2 | RESTful microservices |
| **API** | Spring Cloud Gateway | | API routing & rate limiting |
| **Process** | Camunda | 8.x | BPMN workflow orchestration |
| **Messaging** | Apache Kafka | 7.5 | Event streaming & persistence |
| **Database** | PostgreSQL | 14 | Transactional data store |
| **Storage** | MinIO / GCS | | Document & evidence storage |
| **Frontend** | React.js | 18 | Single Page Application (SPA) |
| **Frontend Build** | TypeScript | 5.x | Type-safe frontend code |
| **UI Framework** | Tailwind CSS | 3.x | Utility-first styling |
| **Identity** | Keycloak | 23.x | OAuth2/OpenID Connect |
| **Container** | Docker | 24+ | Container packaging |
| **Orchestration** | Kubernetes | 1.27+ | Container orchestration |
| **Cloud Platform** | Google Cloud Platform | | Production deployment |
| **CI/CD** | Cloud Build + GitHub Actions | | Automated pipeline |

---

## System Architecture

### High-Level Architecture Diagram
**[PLACEHOLDER: System Architecture Diagram]**
```
┌────────────────────────────────────────────────────────────────────┐
│         React.js 18 Frontend (TypeScript + Tailwind)                │
│    Customer | Adjustor | Surveyor | Partner Portals                │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTPS/JWT
                               │ (OAuth2 via Keycloak)
┌──────────────────────────────▼───────────────────────────────────────┐
│         API Gateway (Spring Cloud Gateway) :8080                      │
│    • Rate Limiting  • JWT Validation  • Cloud Armor WAF              │
│    • Request Routing  • Circuit Breaker Pattern                      │
└──┬─────┬─────┬──────┬──────┬──────────────────────────────────────────┘
   │     │     │      │      │
   ▼     ▼     ▼      ▼      ▼
 :8081 :8082 :8083  :8084  :8085
Claims Notif  Doc   Partner Workflow
Svce  Svce    Svce  Svce    (Camunda8)
   │     │     │      │      │
   └─────┴─────┴──────┴──────┘
            │
   ┌────────▼─────────┐
   │  Apache Kafka    │
   │  11 Event Topics │
   │  3 Partitions    │
   └────────────────────┘
   │        │        │
  ▼        ▼        ▼
PostgreSQL MinIO Keycloak
(Cloud SQL) (GCS) (OAuth2)
```

**Reference Code:**
- [pom.xml](../pom.xml) - Parent POM with spring-boot-starter-parent
- [api-gateway/src](../api-gateway/src) - Gateway configuration

---

## Microservices Breakdown

### 1. Claims Service (:8081)

**Purpose:** FNOL processing and 12-state claim lifecycle management

**Technology:**
- Spring Boot REST API
- Spring Data JPA with Hibernate
- PostgreSQL (Cloud SQL)
- Spring Kafka Consumer/Producer
- Testcontainers + EmbeddedKafka for integration tests

**Key Flows:**
- Claim submission & validation
- Document attachment handling
- Workflow state transitions
- Event publishing (claim.submitted, claim.validated, claim.approved, claim.closed)

**Code References:**
- [Claims Service Dockerfile](../claims-service/Dockerfile)
- [Claims Service pom.xml](../claims-service/pom.xml)
- [ClaimsController.java](../claims-service/src/main/java/com/nagarro/cdms/controller/)
- [ClaimService.java](../claims-service/src/main/java/com/nagarro/cdms/service/)
- [ClaimsServiceIntegrationTest.java](../claims-service/src/test/java/)

**Database Schema:**
```sql
-- claims_service database (Cloud SQL)
CREATE TABLE claims (
  claim_id UUID PRIMARY KEY,
  policy_id VARCHAR(50),
  claim_status VARCHAR(20), -- DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, SETTLED
  incident_date TIMESTAMP,
  incident_description TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE claim_documents (
  id UUID PRIMARY KEY,
  claim_id UUID REFERENCES claims(claim_id),
  document_type VARCHAR(50),
  storage_path VARCHAR(500),
  uploaded_at TIMESTAMP
);
```

---

### 2. Notification Service (:8082)

**Purpose:** Multi-channel notifications (email, SMS, in-app)

**Technology:**
- Spring Boot + Spring Mail
- Apache Kafka Consumer
- Mailhog (dev) / AWS SES (prod)
- Template engine (Freemarker)

**Event Consumers:**
- `claim.submitted` → Send claim confirmation email
- `claim.approved` → Notification to all stakeholders
- `repair.completed` → Customer notification

**Code References:**
- [NotificationService.java](../notification-service/src/main/java/com/nagarro/cdms/service/)
- [EmailConsumer.java](../notification-service/src/main/java/com/nagarro/cdms/listener/)
- [notification-service/application.yml](../notification-service/src/main/resources/)

---

### 3. Document Service (:8083)

**Purpose:** Document storage, retrieval, and lifecycle management

**Technology:**
- Spring Boot REST API
- MinIO / Google Cloud Storage (GCS)
- Spring Cloud Storage integration
- Apache Kafka Producer

**Operations:**
- Upload claim documents (photos, invoices, estimates)
- Retrieve signed URLs for download
- Document versioning and audit trail
- Virus scanning (ClamAV integration)

**Code References:**
- [DocumentService.java](../document-service/src/main/java/com/nagarro/cdms/service/)
- [DocumentController.java](../document-service/src/main/java/com/nagarro/cdms/controller/)
- [docker-compose.yml - MinIO config](../docker/docker-compose.yml)

---

### 4. Partner Service (:8084)

**Purpose:** Workshop/garage network management and real-time availability

**Technology:**
- Spring Boot + Spring Data
- Geospatial queries (PostgreSQL PostGIS)
- Apache Kafka Producer
- WebSocket (real-time availability updates)

**Features:**
- Workshop registration & management
- Geo-radius search (find nearest workshops)
- Real-time repair status updates
- Inventory management (parts, labor rates)

**Code References:**
- [PartnerService.java](../partner-service/src/main/java/com/nagarro/cdms/service/)
- [WorkshopController.java](../partner-service/src/main/java/com/nagarro/cdms/controller/)

---

### 5. Workflow Service (:8085 with Camunda 8)

**Purpose:** BPMN-based claim lifecycle orchestration with Zeebe

**Technology:**
- Spring Boot + Camunda SDK
- Zeebe workflow engine
- Apache Kafka Consumer
- Job workers pattern

**Workflows:**
```
CLAIM LIFECYCLE (12 states):
1. DRAFT
2. SUBMITTED
3. UNDER_REVIEW
4. ASSESSMENT_PENDING
5. SURVEYOR_ASSIGNED
6. ASSESSMENT_COMPLETED
7. APPROVED
8. REPAIR_SCHEDULED
9. REPAIR_IN_PROGRESS
10. REPAIR_COMPLETED
11. PAYMENT_PROCESSING
12. CLOSED/REJECTED
```

**Code References:**
- [WorkflowService layer](../workflow-service/src/main/java/com/nagarro/cdms/service/)
- [ClaimJobHandlers.java](../workflow-service/src/main/java/com/nagarro/cdms/handler/) - Zeebe job workers
- [WorkflowKafkaListener.java](../workflow-service/src/main/java/com/nagarro/cdms/listener/)
- [BPMN workflow definition](../workflow-service/src/main/resources/bpmn/)

---

### 6. API Gateway (:8080)

**Purpose:** Single entry point with routing, authentication, and rate limiting

**Technology:**
- Spring Cloud Gateway
- Spring Security + OAuth2
- Spring Cloud Config

**Features:**
- Intelligent request routing to microservices
- JWT token validation (via Keycloak)
- Rate limiting (100 req/min per user)
- Circuit breaker fallback
- Request/response logging
- CORS configuration

**Code References:**
- [api-gateway/pom.xml](../api-gateway/pom.xml)
- [GatewayConfig.java](../api-gateway/src/main/java/com/nagarro/cdms/config/)
- [application.yml](../api-gateway/src/main/resources/) - Route definitions

---

### 7. Shared Events Library

**Purpose:** Common Kafka event definitions used across all services

**Technology:**
- Plain Java POJOs + Lombok
- Jackson JSON serialization
- Avro schema registry (optional for prod)

**Event Types:**
```java
// Event Object Model
AssessmentSubmittedEvent
├── assessmentId: UUID
├── claimId: UUID
├── estimatedRepairAmount: BigDecimal
└── occurredAt: LocalDateTime

RepairCompletedEvent
├── claimId: UUID
├── repairAmount: BigDecimal
└── occurredAt: LocalDateTime

ClaimClosedEvent
├── claimId: UUID
├── finalAmount: BigDecimal
└── occurredAt: LocalDateTime
```

**Code References:**
- [shared-events/src/main/java/com/nagarro/cdms/event/](../shared-events/src/main/java/)

---

## Frontend Architecture

### React Architecture Diagram
**[PLACEHOLDER: Frontend Component Hierarchy Diagram]**

```
App.tsx (Context Provider)
├── AuthContext (Keycloak OAuth2)
│   ├── useAuth() hook
│   └── Token refresh logic
├── Layout (Master-detail)
│   ├── Navigation (RBAC aware)
│   └── Routes
│       ├── /customer/*
│       │   ├── Dashboard
│       │   ├── SubmitClaim
│       │   │   ├── VehicleForm
│       │   │   ├── IncidentDetails
│       │   │   └── DocumentUpload
│       │   └── ClaimHistory
│       ├── /adjustor/*
│       │   ├── AssignmentQueue
│       │   ├── ReviewClaim
│       │   └── Approval/Rejection
│       ├── /surveyor/*
│       │   ├── AssignedClaims
│       │   ├── AssessmentForm
│       │   └── ReportSubmission
│       └── /partner/*
│           ├── WorkshopDashboard
│           ├── RepairJobs
│           └── AvailabilityUpdate
```

**Technology Stack:**
- **React 18** - Component-based UI library
- **TypeScript** - Type-safe React components
- **Tailwind CSS** - Utility-first styling (no theme maintenance)
- **React Router v6** - SPA routing with lazy loading
- **Context API** - Global state management (Auth, User)
- **React Query** - Server state management & API caching
- **Axios** - HTTP client with JWT automatic injection
- **Zod** - Schema validation for forms

**Key Components:**

| Component | Path | Purpose |
|-----------|------|---------|
| AuthContext | [context/AuthContext.tsx](../frontend/src/context/AuthContext.tsx) | OAuth2 token management |
| useAuth Hook | [hooks/useAuth.ts](../frontend/src/hooks/) | Custom hook for auth state |
| SubmitClaim | [pages/customer/SubmitClaim.tsx](../frontend/src/pages/) | Multi-step claim form |
| ClaimReview | [pages/adjustor/ReviewClaim.tsx](../frontend/src/pages/) | Adjustor workflow |
| AssessmentForm | [pages/surveyor/AssessmentForm.tsx](../frontend/src/pages/) | Surveyor appraisal |
| api.ts | [services/api.ts](../frontend/src/services/) | Axios instance + interceptors |

**Code References:**
- [frontend/src/App.tsx](../frontend/src/App.tsx) - Root component
- [frontend/package.json](../frontend/package.json) - Dependencies
- [frontend/Dockerfile](../frontend/Dockerfile) - Multi-stage build for React

---

## Data & Integration Layer

### Message-Driven Architecture
**[PLACEHOLDER: Kafka Topic Flow Diagram]**

**Apache Kafka Configuration:**
- **Broker Count:** 3 (production) / 1 (dev)
- **Topics:** 11 (3 partitions, 1 replication factor)
- **Consumer Groups:** 1 per service

**Event Topics:**

| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `claim.submitted` | Claims Service | Notification, Workflow | FNOL event |
| `claim.validated` | Claims Service | Workflow | Passed initial validation |
| `claim.approved` | Workflow Service | Notification, Accounting | Approved for payout |
| `claim.rejected` | Workflow Service | Notification, Document | Rejection notice |
| `surveyor.assigned` | Workflow Service | Notification | Surveyor appointment |
| `assessment.submitted` | Workflow Service | Claims, Document | Damage assessment |
| `workshop.assigned` | Workflow Service | Notification | Repair shop selection |
| `repair.status.updated` | Partner Service | Notification | Real-time job updates |
| `repair.completed` | Partner Service | Workflow, Notification | Work completion |
| `payment.processed` | Workflow Service | Notification, Accounting | Final payout |
| `claim.closed` | Workflow Service | Audit, Analytics | Claim lifecycle end |

**Code References:**
- [shared-events/src/main/java/com/nagarro/cdms/event/](../shared-events/src/main/java/com/nagarro/cdms/event/) - Event DTOs
- [docker-compose.yml - Kafka section](../docker/docker-compose.yml) - Kafka configuration

---

### Database Schema Overview

**PostgreSQL Databases (Cloud SQL):**

```sql
-- Service per database (multi-tenancy support available)
claims_db
├── claims (12-state lifecycle)
├── claim_documents
├── claim_history (audit trail)
└── claim_notes

notification_db
├── notifications (sent/delivered/failed)
├── notification_templates
└── notification_preferences

document_db
├── documents (metadata)
├── document_versions
└── storage_indices

partner_db
├── workshops
├── parts_inventory
├── labor_rates
└── availability_slots

workflow_db
├── process_instances
├── task_assignments
└── audit_logs
```

**Code References:**
- [claims-service/src/main/resources/db/migration/](../claims-service/src/main/resources/db/) - Flyway migrations
- [ClaimsRepository.java](../claims-service/src/main/java/com/nagarro/cdms/repository/) - Data access layer

---

## Security & Authentication

### OAuth2/OIDC with Keycloak
**[PLACEHOLDER: Security Architecture Diagram]**

**Authentication Flow:**
1. User navigates to React frontend (localhost:3000)
2. Frontend redirects to Keycloak login page
3. Keycloak validates credentials
4. JWT token issued (60 min valid)
5. Refresh token stored (7 days valid)
6. Token included in all API requests via Bearer header
7. API Gateway validates JWT signature with public key

**Keycloak Configuration:**
- **Realm:** eclaims
- **Client:** eclaims-spa (frontend)
- **Client Protocols:** openid-connect
- **User Roles:** CUSTOMER, SURVEYOR, ADJUSTOR, CASE_MANAGER, ADMIN

**Code References:**
- [SecurityConfig.java](../api-gateway/src/main/java/com/nagarro/cdms/config/) - Spring Security setup
- [TestSecurityConfig.java](../claims-service/src/test/java/com/nagarro/cdms/config/) - Test JWT mock
- [frontend/src/services/keycloak.ts](../frontend/src/services/) - Keycloak adapter

**Role-Based Access Control (RBAC):**

```java
// Java - Spring Security
@GetMapping("/claims/{id}")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADJUSTOR', 'CASE_MANAGER')")
public ResponseEntity<ClaimDTO> getClaim(@PathVariable UUID id) {
    // Implementation
}
```

```typescript
// React - Custom hook
const { user } = useAuth();
if (user?.roles?.includes('ADJUSTOR')) {
  return <AdjustorDashboard />;
}
```

**Network Security:**
- Cloud Armor WAF (DDoS + Layer 7 filtering)
- TLS 1.2+ encryption
- VPC network isolation (GCP)
- Private IP for internal services
- Service accounts for pod-to-pod authentication (Workload Identity)

---

## GCP Deployment Architecture

### GCP Services Used
**[PLACEHOLDER: GCP Service Architecture Diagram]**

| GCP Service | Purpose | Configuration |
|-------------|---------|----------------|
| **GKE** | Kubernetes cluster | Autopilot, 3 nodes, e2-medium |
| **Cloud SQL** | PostgreSQL managed | 14.x, HA multi-zone, 100GB |
| **Artifact Registry** | Docker image storage | docker format, us-central1 |
| **Cloud Build** | CI/CD pipeline automation | Git trigger, 30 min timeout |
| **Cloud Run** | (Optional) Serverless | For cron jobs, event listeners |
| **Cloud Storage (GCS)** | Document storage | Multi-region, versioning enabled |
| **Pub/Sub** | (Optional) Kafka alternative | Topic-based messaging |
| **Cloud IAM** | Access management | Service accounts, RBAC |
| **Cloud Logging** | Centralized logging | JSON structured logs |
| **Cloud Monitoring** | Metrics & alerts | Prometheus export |

### GKE Deployment Configuration
**[PLACEHOLDER: K8s Cluster Topology Diagram]**

**Namespace Structure:**
```
kube-system/
├── coredns
├── kube-proxy
└── calico-system

eclaims-dev/ (1 replica per deployment)
├── claims-service-deployment
├── notification-service-deployment
├── document-service-deployment
├── partner-service-deployment
├── workflow-service-deployment
├── api-gateway-deployment
├── frontend-deployment (Nginx)
├── postgres-statefulset
├── kafka-statefulset
├── keycloak-deployment
└── ingress-nginx (LoadBalancer)
```

**Code References:**
- [k8s/base/services.yaml](../k8s/base/services.yaml) - Deployments & Services
- [k8s/overlays/dev/kustomization.yaml](../k8s/overlays/dev/kustomization.yaml) - Image substitutions
- [k8s/base/ingress-rbac.yaml](../k8s/base/ingress-rbac.yaml) - Ingress configuration

---

## CI/CD Pipeline

### GitHub Actions Workflow
**[PLACEHOLDER: CI/CD Pipeline Flow Diagram]**

**Pipeline Stages:**

```
              PR Created
                  │
                  ▼
      ┌─────────────────────┐
      │   pr-checks.yml     │
      │  (Fast feedback)    │
      ├─────────────────────┤
      │ • Compile Java      │
      │ • Run unit tests    │
      │ • Frontend lint     │
      │ • Secret scan       │
      │ • Build time: 8 min │
      └─────────────────────┘
                  │
        (Pass) │  │ (Fail)
              │  └──> PR blocked
              │
              ▼
        Developer fixes,
        re-push to PR
              │
              ├─ (Approve) ──┐
              │              │
              ▼              │
        Merge to main        │
              │              │
              └──────────────┘
                  │
                  ▼
      ┌─────────────────────┐
      │    ci-cd.yml        │
      │(Full pipeline)      │
      ├─────────────────────┤
      │ 1. build-backend    │
      │ 2. build-frontend   │
      │ 3. docker-build (×7)│
      │ 4. push-artifacts   │
      │ 5. sonarqube-scan   │
      │ 6. security-scan    │
      │ 7. deploy-dev       │
      │ 8. integration-test │
      │ 9. require-approval │
      │ 10.deploy-staging   │
      └─────────────────────┘
                  │
                  ▼
        Create Release / Tag
                  │
                  ▼ (2 approvals)
        deploy-production
```

**Automated Quality Gates:**
- ✅ **Compiler:** Maven strict compile options
- ✅ **Tests:** 70%+ code coverage (JUnit 5, Testcontainers)
- ✅ **Linting:** Checkstyle, PMD, SpotBugs
- ✅ **SAST:** SonarQube, CodeQL
- ✅ **Dependency:** OWASP DependencyCheck
- ✅ **Container:** Trivy image scanning
- ✅ **Secret:** TruffleHog secret detection

**Code References:**
- [.github/workflows/ci-cd.yml](../.github/workflows/) - Main pipeline (35+ steps)
- [.github/workflows/pr-checks.yml](../.github/workflows/) - PR validation pipeline
- [cloudbuild.yaml](../cloudbuild.yaml) - Alternative GCP Cloud Build

---

## Design Patterns & Best Practices

### 1. Microservices Patterns

| Pattern | Implementation | Code Example |
|---------|----------------|--------------|
| **Circuit Breaker** | Resilience4j | [resilience4j config](../api-gateway/src/main/resources/) |
| **Retry Logic** | Spring Retry + exponential backoff | Service layer retry annotations |
| **Timeout** | @Transactional + statement_timeout | SQL query timeouts |
| **Bulkhead** | Thread pool isolation | ThreadPoolTaskExecutor beans |
| **Event Sourcing** | Immutable event log | Kafka topic partitions |
| **SAGA Pattern** | Orchestrator-based (Camunda) | workflow-service BPMN |

### 2. Data Consistency

**Transactional Guarantees:**
- Database: ACID transactions (PostgreSQL)
- Messaging: Exactly-once delivery via Kafka offset management
- Distributed: Compensating transactions in Workflow Service

**Code References:**
- [ClaimService.java - @Transactional](../claims-service/src/main/java/com/nagarro/cdms/service/) - Transaction management
- [KafkaConfig.java](../shared-events/src/main/java/com/nagarro/cdms/config/) - Consumer offset strategy

### 3. Asynchronous Communication

**Request-Reply Pattern:**
```java
// Claims Service publishes event
kafkaTemplate.send("claim.submitted", claimSubmittedEvent);

// Notification Service consumes
@KafkaListener(topics = "claim.submitted")
public void onClaimSubmitted(ClaimSubmittedEvent event) {
    // Send confirmation email
}
```

### 4. API Design

**REST Guidelines:**
- Versioning: `/api/v1/` in all paths
- Pagination: `?page=1&size=20&sort=createdAt,desc`
- Filtering: `?status=APPROVED&createdAfter=2024-01-01`
- Errors: RFC 7807 Problem Details standard

**GraphQL (Optional for complex queries):**
```graphql
query {
  claim(id: "123") {
    id
    status
    assessments { id, estimatedAmount }
    documents { id, type }
  }
}
```

---

## Testing Strategy

### Test Pyramid
**[PLACEHOLDER: Test Pyramid Diagram]**

| Level | Framework | Count | Coverage | Time |
|-------|-----------|-------|----------|------|
| **Unit Tests** | JUnit 5, Mockito | 500+ | 70%+ | 2 min |
| **Integration** | Testcontainers, EmbeddedKafka | 150+ | 30%+ | 8 min |
| **E2E Tests** | Selenium/Cypress, RestAssured | 50+ | 20%+ | 5 min |

**Code References:**
- [ClaimsServiceIntegrationTest.java](../claims-service/src/test/java/) - Testcontainers
- [ClaimServiceTest.java](../claims-service/src/test/java/) - Unit tests with Mockito
- [frontend/src/__tests__/](../frontend/src/__tests__/) - React component tests

### Test Automation
```bash
# Run all tests locally
mvn clean verify

# Run integration tests with Docker
mvn verify -DskipUnitTests=false

# Run E2E tests in GKE
kubectl exec -it claims-service-pod -- ./gradlew testE2E
```

---

## Monitoring & Observability

### Observability Stack
**[PLACEHOLDER: Observability Architecture Diagram]**

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Logs** | ELK / Cloud Logging | Centralized log aggregation |
| **Metrics** | Prometheus + Grafana | Performance monitoring |
| **Traces** | Jaeger / Cloud Trace | Distributed tracing |
| **Alerts** | Alertmanager | Incident alerting |

**Code References:**
- [application.yml - Actuator/Micrometer config](../claims-service/src/main/resources/) - Metrics export
- [pom.xml - spring-boot-starter-actuator](../pom.xml) - Observability dependency
- [docker/prometheus/prometheus.yml](../docker/prometheus/prometheus.yml) - Prometheus config
- [docker/grafana/dashboards/](../docker/grafana/dashboards/) - Grafana JSON dashboards

**Key Metrics:**
- Request latency (p50, p95, p99)
- Error rate by service & endpoint
- Database connection pool utilization
- Kafka consumer lag per partition
- JVM memory & GC duration

---

## Code Repository Structure

```
eclaims-poc/
│
├── .github/
│   ├── workflows/
│   │   ├── ci-cd.yml                 ← Main 10-job pipeline
│   │   ├── pr-checks.yml             ← Fast PR validation
│   │   └── release.yml               ← Release automation
│   ├── GITHUB_SETUP.md               ← Setup instructions
│   └── PULL_REQUEST_TEMPLATE/
│
├── shared-events/
│   ├── pom.xml                       ← Event library POM
│   ├── src/main/java/.../event/
│   │   ├── AssessmentSubmittedEvent.java
│   │   ├── RepairCompletedEvent.java
│   │   ├── ClaimClosedEvent.java
│   │   └── ...
│   └── src/test/java/
│
├── api-gateway/
│   ├── Dockerfile                    ← Multi-stage build
│   ├── pom.xml                       ← Spring Cloud Gateway
│   ├── src/main/java/.../config/
│   │   ├── GatewayConfig.java        ← Route definitions
│   │   └── SecurityConfig.java       ← OAuth2/JWT setup
│   ├── src/main/resources/
│   │   └── application.yml           ← 7 microservices routes
│   └── src/test/java/
│
├── claims-service/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   ├── controller/ClaimsController.java
│   │   ├── service/ClaimService.java
│   │   ├── repository/ClaimsRepository.java
│   │   ├── entity/Claim.java
│   │   ├── event/listener/*.java
│   │   └── config/*.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   │       ├── V1__initial_schema.sql
│   │       └── ...
│   └── src/test/java/
│       ├── ClaimsServiceIntegrationTest.java (Testcontainers)
│       ├── ClaimServiceTest.java (Mockito)
│       └── config/TestSecurityConfig.java
│
├── notification-service/
│   ├── Similar structure as claims-service
│   ├── EmailConsumer.java             ← Kafka listener
│   └── EmailTemplates.java
│
├── document-service/
│   ├── DocumentService.java
│   └── MinIO/GCS integration
│
├── partner-service/
│   ├── WorkshopService.java
│   ├── GeoSpatialQueryService.java   ← PostGIS queries
│   └── RealtimeStatusService.java
│
├── workflow-service/
│   ├── Camunda BPMN integration
│   ├── ClaimJobHandlers.java         ← Zeebe job workers
│   ├── WorkflowKafkaListener.java	 ← Event consumers
│   └── bpmn/claim-lifecycle.bpmn
│
├── frontend/
│   ├── Dockerfile                    ← Nginx + React
│   ├── package.json                  ← React 18, TypeScript
│   ├── src/
│   │   ├── App.tsx
│   │   ├── context/
│   │   │   └── AuthContext.tsx       ← Keycloak OAuth2
│   │   ├── pages/
│   │   │   ├── customer/SubmitClaim.tsx
│   │   │   ├── adjustor/ReviewClaim.tsx
│   │   │   ├── surveyor/AssessmentForm.tsx
│   │   │   └── partner/Dashboard.tsx
│   │   ├── components/
│   │   │   ├── common/Layout.tsx
│   │   │   └── ...
│   │   ├── services/
│   │   │   ├── api.ts               ← Axios instance
│   │   │   └── keycloak.ts
│   │   ├── hooks/
│   │   │   └── useAuth.ts
│   │   └── index.tsx
│   ├── public/
│   └── tailwind.config.js
│
├── k8s/
│   ├── base/
│   │   ├── namespace.yaml
│   │   ├── services.yaml            ← All deployments & services
│   │   ├── claims-service.yaml      ← Claims StatefulSet (opt)
│   │   ├── ingress-rbac.yaml        ← Ingress + RBAC
│   │   └── kustomization.yaml
│   ├── overlays/
│   │   ├── dev/
│   │   │   ├── kustomization.yaml   ← Dev: 1 replica, latest tag
│   │   │   └── ...
│   │   ├── staging/
│   │   │   ├── kustomization.yaml   ← Staging: 2 replicas
│   │   │   └── ...
│   │   └── prod/
│   │       ├── kustomization.yaml   ← Prod: 3 replicas, HPA 2-10
│   │       └── ...
│   ├── argocd/
│   │   └── applications.yaml        ← ArgoCD Application definitions
│   └── scripts/
│       ├── deploy.sh                ← Deployment helper
│       └── validate.sh              ← Manifest validation
│
├── docker/
│   ├── docker-compose.yml           ← Full 18-service stack
│   ├── init-db.sql                  ← Database initialization
│   ├── keycloak/
│   │   └── realm-export.json        ← Keycloak realm config
│   ├── grafana/
│   │   ├── dashboards/              ← JSON dashboards
│   │   └── provisioning/
│   ├── prometheus/
│   │   └── prometheus.yml           ← Scrape configs
│   └── nginx/ (optional)
│
├── documents/
│   ├── SOLUTION_APPROACH.md         ← This document
│   ├── ClaimMyIssurance.docx        ← Executive summary
│   └── Estimates-ClaimMyInsurance_Final.xlsm
│
├── .gitignore
├── cloudbuild.yaml                  ← GCP Cloud Build pipeline
├── docker-compose.yml               ← Dev compose
├── pom.xml                          ← Parent POM
├── run-all.sh                       ← One-command dev startup
└── README.md                        ← Quick reference

```

---

## Deployment Instructions

### Local Development (Docker Compose)

```bash
# Clone repository
git clone https://github.com/manjeetrananagap/promotioncasestudy-eclaims.git
cd eclaims-poc

# Start all 18 containers
./run-all.sh start

# Access endpoints
# Frontend: http://localhost:3000
# API Gateway: http://localhost:8080
# Keycloak: http://localhost:8180 (admin/admin123)
```

### GCP Deployment (GKE)

```bash
# 1. Create GKE cluster
gcloud container clusters create-auto eclaims-cluster --region us-central1

# 2. Get credentials
gcloud container clusters get-credentials eclaims-cluster --region us-central1

# 3. Deploy via Cloud Build (automatic on git push)
git push origin main

# 4. Or deploy manually
cd k8s
kubectl apply -k overlays/dev/
kubectl get svc -n eclaims-dev
```

---

## Code Quality Standards

**All commits must pass:**
- ✅ Compiler errors (zero)
- ✅ PMD/Checkstyle violations: 0 critical, <10 minor
- ✅ SpotBugs: 0 critical
- ✅ Unit test pass rate: 100%
- ✅ Code coverage: ≥70%
- ✅ SonarQube quality gate: PASS
- ✅ Secret detection: 0 findings
- ✅ OWASP DependencyCheck: 0 critical CVEs

---

## Next Steps & Roadmap

| Phase | Timeline | Features |
|-------|----------|----------|
| **V1.0** | Current | Core claim lifecycle, BPMN workflows |
| **V1.1** | Q2 2026 | Mobile app (React Native) |
| **V1.2** | Q3 2026 | GraphQL API layer |
| **V1.3** | Q4 2026 | AI-powered claim classification |
| **V2.0** | 2027 | Multi-tenant SaaS model |

---

## References & Links

- **Repository:** https://github.com/manjeetrananagap/promotioncasestudy-eclaims
- **Documentation:** README.md, GitHub Wiki
- **Confluence Space:** [Link to internal documentation]
- **Jira Project:** ECLAIM
- **Teams Channel:** #eclaims-dev

---

**Document Version:** 1.0  
**Last Updated:** March 19, 2026  
**Maintained By:** Nagarro Engineering Team  
**Confidentiality:** Internal Use Only

