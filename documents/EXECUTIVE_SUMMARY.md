# eClaims Platform — Executive Summary & Deployment Guide

**Document Title:** ClaimMyInsurance Solution Approach  
**Version:** 1.0  
**Date:** March 19, 2026  
**Organization:** Nagarro Software Pvt. Ltd.  
**Technology Stack:** Java 17 · Spring Boot 3.2 · React 18 · Apache Kafka · GCP

---

## 1. Project Overview

### Vision
Transform insurance claim processing through a cloud-native, event-driven microservices platform enabling seamless multichannel access for customers, adjustors, surveyors, and workshop partners.

### Key Metrics
- **Microservices:** 7 independent services
- **Event Streams:** 11 Kafka topics, 3 partitions each
- **Claim States:** 12-state workflow orchestration via Camunda BPMN
- **Stakeholder Portals:** 4 role-based React SPAs
- **Deployment:** GCP GKE clusters (Dev/Staging/Prod)
- **CI/CD:** GitHub Actions (35+ automated gates)

---

## 2. Technology Architecture

### Backend Services (Java 17 + Spring Boot 3.2)
```
┌─────────────────────────────────────────────────────┐
│              7 Microservices                         │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Claims (:8081)   → 12-state claim lifecycle        │
│  Notification (:8082) → Email/SMS consumers         │
│  Document (:8083) → MinIO/GCS document storage      │
│  Partner (:8084)  → Workshop network + geo-search   │
│  Workflow (:8085) → Camunda BPMN + Zeebe workers   │
│  API Gateway (:8080) → Single entry point           │
│  shared-events → Kafka event definitions            │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**Database:** PostgreSQL 14 (Cloud SQL)  
**Messaging:** Apache Kafka 7.5 (11 event topics)  
**Storage:** MinIO / Google Cloud Storage (GCS)  
**Identity:** Keycloak 23.x (OAuth2/OIDC)

### Frontend (React 18 + TypeScript)
```
┌─────────────────────────────────────────────────────┐
│         React Single Page Application                │
├─────────────────────────────────────────────────────┤
│                                                      │
│  🧑 Customer Portal    → Claim submission           │
│  💼 Adjustor Portal   → Claim review & approval     │
│  🔍 Surveyor Portal   → Damage assessment           │
│  🔧 Partner Portal    → Workshop job management     │
│                                                      │
│  Auth: Keycloak OAuth2  │  Styling: Tailwind CSS   │
│  Routing: React Router  │  State: Context API       │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 3. Claim Lifecycle (12 States)

```
DRAFT → SUBMITTED → UNDER_REVIEW → ASSESSMENT_PENDING
  ↓         ↓           ↓              ↓
[Stored]  [Event]    [Review]      [Assign Surveyor]
              ↓                        ↓
         SURVEYOR_ASSIGNED → ASSESSMENT_COMPLETED
              ↓                     ↓
         APPROVED ← [Approval Gate]
             ↓
    REPAIR_SCHEDULED → REPAIR_IN_PROGRESS → REPAIR_COMPLETED
             ↓                                   ↓
    PAYMENT_PROCESSING ←─────────────────────────┘
             ↓
    CLOSED (Success) or REJECTED (Failed)
```

**Event Publishing at Each State:**
- `claim.submitted` → Notification Service
- `claim.validated` → Workflow Service  
- `surveyor.assigned` → Notification Service
- `assessment.submitted` → Document Service, Claims Service
- `workshop.assigned` → Notification Service
- `repair.completed` → Workflow Service, Notifications
- `payment.processed` → Accounting System
- `claim.closed` → Audit, Analytics

---

## 4. Microservices Details

### Claims Service (:8081)
**Responsibility:** FNOL processing, 12-state lifecycle  
**Tech Stack:** Spring Boot REST, Spring Data JPA, PostgreSQL  
**Key Endpoints:**
```
POST   /api/v1/claims              - Submit new claim
GET    /api/v1/claims/{id}         - Get claim details
PUT    /api/v1/claims/{id}         - Update claim
POST   /api/v1/claims/{id}/docs    - Upload documents
GET    /api/v1/claims?status=APPROVED - Filter claims
```

**Database Tables:**
- `claims` (claim_id, policy_id, claim_status, incident_date...)
- `claim_documents` (id, claim_id, document_type, storage_path)
- `claim_history` (audit trail for compliance)

**Code:** `claims-service/src/main/java/com/nagarro/cdms/`

---

### Notification Service (:8082)
**Responsibility:** Multi-channel notifications  
**Kafka Consumer Topics:** claim.submitted, claim.approved, repair.completed  
**Channels:** Email (Mailhog/AWS SES), SMS (Twilio)  

**Code:** `notification-service/src/main/java/com/nagarro/cdms/listener/`

---

### Document Service (:8083)
**Responsibility:** Claims documents storage & retrieval  
**Storage:** MinIO (dev), GCS (prod)  
**Features:** Virus scanning, signed URLs, versioning  

**Code:** `document-service/src/main/java/com/nagarro/cdms/`

---

### Partner Service (:8084)
**Responsibility:** Workshop network management  
**Special Features:** 
- Geo-spatial queries (find nearest workshops)
- Real-time availability updates (WebSocket)
- Parts inventory & labor rates

**Code:** `partner-service/src/main/java/com/nagarro/cdms/`

---

### Workflow Service (:8085 + Camunda)
**Responsibility:** BPMN claim orchestration  
**Engine:** Camunda 8 + Zeebe  
**Pattern:** Choreography (events) + Orchestration (Camunda)  

**Code:** `workflow-service/src/main/java/com/nagarro/cdms/`

---

### API Gateway (:8080)
**Responsibility:** Single entry point, authentication, rate limiting  
**Features:**
- JWT validation via Keycloak public key
- Rate limit: 100 req/min per user
- Circuit breaker fallback
- CORS configuration

**Code:** `api-gateway/src/main/java/com/nagarro/cdms/config/GatewayConfig.java`

---

## 5. Frontend Architecture

### React Component Structure
```
App.tsx (Context Provider)
├── AuthContext (Keycloak)
├── Layout (Navigation + Routes)
├── /customer/*
│   ├── Dashboard (My Claims)
│   ├── SubmitClaim (Multi-step form)
│   │   ├── VehicleDetails
│   │   ├── IncidentInfo
│   │   └── DocumentUpload
│   └── ClaimHistory
├── /adjustor/*
│   ├── AssignmentQueue (Claims to review)
│   ├── ReviewClaim (Detail view)
│   └── ApprovalWorkflow
├── /surveyor/*
│   ├── AssignedClaims
│   └── AssessmentForm
└── /partner/*
    ├── WorkshopDashboard
    └── RepairJobs
```

### Key Technologies
- **React 18** - UI framework
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling (no theme maintenance)
- **React Router v6** - SPA routing
- **Axios** - HTTP client with JWT injection
- **React Query** - Server state caching

**Code:** `frontend/src/` (280+ components)

---

## 6. Data Integration (Kafka Event Streams)

### 11 Event Topics
```
┌─────────────────────────────────────────────────┐
│  Topic Name                  │ Producer  │ Consumer  │
├─────────────────────────────────────────────────┤
│ claim.submitted              │ Claims    │ Notif     │
│ claim.validated              │ Claims    │ Workflow  │
│ surveyor.assigned            │ Workflow  │ Notif     │
│ assessment.submitted         │ Workflow  │ Claims    │
│ claim.approved               │ Workflow  │ Notif     │
│ workshop.assigned            │ Workflow  │ Notif     │
│ repair.status.updated        │ Partner   │ Notif     │
│ repair.completed             │ Partner   │ Workflow  │
│ payment.processed            │ Workflow  │ Accounting│
│ claim.closed                 │ Workflow  │ Audit     │
│ audit.events                 │ All       │ Analytics │
└─────────────────────────────────────────────────┘
```

**Consumer Groups:** 1 per service  
**Partitions:** 3 (for parallel processing)  
**Replication Factor:** 1 (dev), 3 (prod)

---

## 7. Security Architecture

### Authentication & Authorization
```
User Login
  ↓
Keycloak (OAuth2)
  ↓
JWT Token (60 min valid)
  ↓
API Gateway (validates JWT signature)
  ↓
Service Authorization (@PreAuthorize)
  ↓
Role-based Access Control (RBAC)
```

**User Roles:**
- CUSTOMER - Submit & track claims
- SURVEYOR - Assess damage
- ADJUSTOR - Review & approve claims
- CASE_MANAGER - Manage complex cases
- ADMIN - System administration

**Sample Role Check (Java):**
```java
@GetMapping("/claims/{id}")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADJUSTOR', 'CASE_MANAGER')")
public ResponseEntity<ClaimDTO> getClaim(@PathVariable UUID id) { ... }
```

**Sample Role Check (React):**
```typescript
const { user } = useAuth();
if (user?.roles?.includes('ADJUSTOR')) {
  return <AdjustorDashboard />;
}
return <AccessDenied />;
```

---

## 8. GCP Deployment Architecture

### GCP Services Stack
| Service | Purpose | Configuration |
|---------|---------|----------------|
| **GKE** | Kubernetes cluster | Autopilot, 3 nodes, e2-medium |
| **Cloud SQL** | PostgreSQL | 14.x, HA, 100GB SSD |
| **Artifact Registry** | Docker images | docker format, us-central1 |
| **Cloud Build** | CI/CD automation | Git trigger on push |
| **Cloud Storage** | Document storage | Multi-region, versioning |
| **Cloud Logging** | Centralized logs | JSON structured |
| **Cloud Monitoring** | Metrics & alerts | Prometheus export |

### GKE Namespace Architecture
```
eclaims-dev/
├── claims-service (1 replica)
├── notification-service (1 replica)
├── document-service (1 replica)
├── partner-service (1 replica)
├── workflow-service (1 replica)
├── api-gateway (1 replica)
├── frontend (1 replica - Nginx)
├── postgres (StatefulSet)
├── kafka (StatefulSet)
├── keycloak (1 replica)
└── ingress-nginx (LoadBalancer)

eclaims-staging/
├── (2 replicas each)

eclaims-prod/
├── (3 replicas each, HPA 2-10)
```

---

## 9. CI/CD Pipeline

### GitHub Actions Workflow
```
Developer creates PR
  ↓
pr-checks.yml runs (8 min):
  ├─ mvn compile (zero errors)
  ├─ mvn test (70%+ coverage)
  ├─ npm lint (ESLint)
  ├─ TruffleHog (secret scan)
  └─ Build Docker images
  
If PASS → Can merge to main
If FAIL → Blocked until fixed
  ↓
Merge to main
  ↓
ci-cd.yml runs (35+ steps, 20 min):
  ├─ Build backend (Maven)
  ├─ Build frontend (npm)
  ├─ Docker build & push (×7 services)
  ├─ SonarQube analysis
  ├─ OWASP DependencyCheck
  ├─ Trivy container scan
  ├─ CodeQL SAST scan
  ├─ Deploy to dev (automatic)
  ├─ Run integration tests
  ├─ Require 1 approval
  └─ Deploy to staging
  
For releases (release/* branch):
  ├─ Require 2 approvals
  ├─ 5-min delay
  └─ Deploy to production
```

**Quality Gates:**
- ✅ Zero compiler errors
- ✅ 70%+ test coverage
- ✅ SonarQube PASSED
- ✅ No critical CVEs
- ✅ No secrets detected

---

## 10. Deployment Instructions

### Local Development (Docker Compose)
```bash
git clone https://github.com/manjeetrananagap/promotioncasestudy-eclaims.git
cd eclaims-poc

./run-all.sh start
# Wait ~2 min for all containers to start

# Access endpoints:
Frontend        → http://localhost:3000
API Gateway     → http://localhost:8080
Keycloak Admin  → http://localhost:8180 (admin/admin123)
Kafka UI        → http://localhost:8090
MinIO Console   → http://localhost:9001
```

### GCP Deployment (First Time)
```bash
# 1. Create GKE cluster
gcloud container clusters create-auto eclaims-cluster \
  --region us-central1 \
  --project eclaims-promotioncasestudy

# 2. Get credentials
gcloud container clusters get-credentials eclaims-cluster \
  --region us-central1

# 3. Create Artifact Registry
gcloud artifacts repositories create eclaims \
  --repository-format=docker \
  --location=us-central1

# 4. Deploy (automatic on git push to main)
git push origin main
# Watch Cloud Build: https://console.cloud.google.com/cloud-build
```

### Get External IP
```bash
kubectl get svc -n eclaims-dev ingress-nginx-controller
# Copy the EXTERNAL-IP and open in browser
# https://<EXTERNAL-IP>/
```

---

## 11. Production Readiness Checklist

- [x] All 7 microservices deployed & tested
- [x] PostgreSQL with backups & HA failover
- [x] Kafka cluster with 3 brokers (prod)
- [x] SSL/TLS certificates (Cloud Armor)
- [x] Centralized logging (Cloud Logging)
- [x] Monitoring & alerting (Cloud Monitoring)
- [x] Auto-scaling policies (HPA, 2-10 replicas)
- [x] Disaster recovery plan
- [x] Load testing passed (1000 concurrent users)
- [x] Security audit passed
- [x] Compliance: GDPR, PCI-DSS ready

---

## 12. Support & Documentation

| Document | Location |
|----------|----------|
| Complete Solution Approach | `documents/SOLUTION_APPROACH.md` |
| README (Quick Start) | `README.md` |
| GitHub Setup | `.github/GITHUB_SETUP.md` |
| K8s Deployment | `k8s/scripts/deploy.sh` |
| Local Dev Setup | `run-all.sh` |
| API Documentation | Swagger: `/swagger-ui.html` |

---

## 13. Contact & Support

**Development Team:** Nagarro Engineering  
**GitHub Repository:** https://github.com/manjeetrananagap/promotioncasestudy-eclaims  
**Issue Tracker:** GitHub Issues  
**Documentation:** GitHub Wiki

---

**Document Version:** 1.0  
**Classification:** Internal Use  
**Last Updated:** March 19, 2026
