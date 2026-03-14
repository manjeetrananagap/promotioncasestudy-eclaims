# eClaims — Electronic Insurance Claims Platform
### Nagarro Software Pvt. Ltd. | Java 17 · Spring Boot 3 · React.js 18 · GCP · GitHub Actions

[![CI/CD](https://github.com/manjeetrananagap/promotioncasestudy-eclaims/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/manjeetrananagap/promotioncasestudy-eclaims/actions/workflows/ci-cd.yml)
[![PR Checks](https://github.com/manjeetrananagap/promotioncasestudy-eclaims/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/manjeetrananagap/promotioncasestudy-eclaims/actions/workflows/pr-checks.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=manjeetrananagap_promotioncasestudy-eclaims&metric=alert_status)](https://sonarcloud.io/summary/manjeetrananagap_promotioncasestudy-eclaims)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=manjeetrananagap_promotioncasestudy-eclaims&metric=coverage)](https://sonarcloud.io/summary/manjeetrananagap_promotioncasestudy-eclaims)
[![License: Proprietary](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│               React.js 18 Frontend (TypeScript)           │
│     Customer · Adjustor · Surveyor · Partner Portals      │
└─────────────────────────┬────────────────────────────────┘
                          │ HTTPS / JWT
┌─────────────────────────▼────────────────────────────────┐
│          API Gateway  :8080  (Spring Cloud Gateway)       │
│     Rate Limit · JWT Validate · Cloud Armor WAF           │
└──┬──────────┬──────────┬─────────┬─────────┬─────────────┘
   │          │          │         │         │
   ▼          ▼          ▼         ▼         ▼
:8081      :8082      :8083     :8084     :8085
Claims  Notification Document  Partner  Workflow
Service   Service    Service   Service  (Camunda8)
   │          │          │         │         │
   └──────────┴──────────┴─────────┴─────────┘
                         │
              ┌──────────▼──────────┐
              │    Apache Kafka      │  11 topics
              └──────────────────────┘
              │          │           │
           PostgreSQL  MinIO      Keycloak
           (Cloud SQL) (GCS)      (OAuth2)
```

---

## Quick Start

### Prerequisites
| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Docker | 24+ with Compose v2 |
| Node.js | 20+ |

### One-command start
```bash
git clone https://github.com/manjeetrananagap/promotioncasestudy-eclaims.git
cd eclaims-poc
./run-all.sh start
```

### Step by step
```bash
# Start all infrastructure containers
./run-all.sh infra

# Build all Java services
./run-all.sh build

# Start all microservices
./run-all.sh start

# In a separate terminal — start React frontend
cd frontend && npm install && npm start
```

---

## Service URLs (local)

| Service | URL | Credentials |
|---------|-----|-------------|
| **Frontend** | http://localhost:3000 | Keycloak login |
| **API Gateway** | http://localhost:8080 | JWT required |
| **Claims Swagger** | http://localhost:8081/swagger-ui.html | — |
| **Partner Swagger** | http://localhost:8084/swagger-ui.html | — |
| **Keycloak** | http://localhost:8180 | admin / admin123 |
| **Kafka UI** | http://localhost:8090 | — |
| **MinIO Console** | http://localhost:9001 | eclaims_admin / eclaims_secret_2024 |
| **Mailhog** | http://localhost:8025 | — |
| **Camunda Operate** | http://localhost:8889 | — |
| **Grafana** | http://localhost:3001 | admin / admin123 |

---

## Test Users

| Username | Password | Role |
|----------|----------|------|
| `customer1` | `Customer@123` | CUSTOMER |
| `surveyor1` | `Surveyor@123` | SURVEYOR |
| `adjustor1` | `Adjustor@123` | ADJUSTOR |
| `casemanager1` | `Manager@123` | CASE_MANAGER |

---

## Test the Full Claim Flow

```bash
# Automated end-to-end test
./run-all.sh test-claim

# Get a JWT token manually
./run-all.sh token customer1

# Use it
TOKEN=$(cat /tmp/eclaims-token.txt)
curl -s -X POST http://localhost:8080/api/v1/claims \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": "POL-2024-001234",
    "vehicleReg": "DL01AB1234",
    "vehicleMake": "Toyota",
    "vehicleModel": "Innova",
    "accidentLat": 28.6139,
    "accidentLng": 77.2090,
    "incidentDate": "2024-03-14",
    "incidentDescription": "Rear-end collision at traffic signal."
  }' | python3 -m json.tool
```

---

## CI/CD Pipeline (GitHub Actions)

```
Pull Request → main
  pr-checks.yml: compile · unit-tests · frontend-check · secret-scan

Push → main (after merge)
  ci-cd.yml:
    validate → build-backend → build-frontend
    → sonarqube → security (OWASP+CodeQL+Trivy)
    → docker-build (×7 parallel)
    → deploy-dev (automatic)
    → integration-tests
    → deploy-staging (1 reviewer approval)

Push → release/**
    + deploy-production (2 reviewers + 5 min timer)
    + GitHub Release created automatically
```

See [`.github/GITHUB_SETUP.md`](.github/GITHUB_SETUP.md) for full setup instructions.

---

## GCP Deployment

```bash
# 1. Authenticate
gcloud auth login
export GCP_PROJECT_ID=your-project-id
gcloud config set project $GCP_PROJECT_ID

# 2. Create GKE Autopilot cluster
gcloud container clusters create-auto eclaims-cluster --region us-central1

# 3. Create Artifact Registry
gcloud artifacts repositories create eclaims \
  --repository-format=docker --location=us-central1

# 4. Push to main branch → GitHub Actions deploys automatically
git push origin main
```

---

## Repository Structure

```
eclaims-poc/
├── .github/
│   ├── workflows/
│   │   ├── ci-cd.yml          ← Main pipeline (10 jobs)
│   │   └── pr-checks.yml      ← Fast PR feedback
│   ├── dependabot.yml         ← Auto dependency updates
│   ├── GITHUB_SETUP.md        ← Full setup guide
│   └── PULL_REQUEST_TEMPLATE/ ← PR checklist
├── shared-events/             ← Kafka event DTOs (shared library)
├── api-gateway/               ← Spring Cloud Gateway :8080
├── claims-service/            ← FNOL · 12-state lifecycle :8081
├── notification-service/      ← Email/SMS consumers :8082
├── document-service/          ← MinIO/GCS storage :8083
├── partner-service/           ← Geo-radius · workshops :8084
├── workflow-service/          ← Camunda 8 BPMN :8085
├── frontend/                  ← React.js 18 + TypeScript SPA
├── k8s/
│   ├── base/                  ← K8s manifests (Deployments, HPA, Ingress)
│   ├── overlays/dev/          ← Dev: 1 replica
│   ├── overlays/staging/      ← Staging: 2 replicas
│   ├── overlays/prod/         ← Prod: 3 replicas, HPA 2-10
│   └── argocd/                ← ArgoCD Application definitions
├── docker-compose.yml         ← Full local dev (18 containers)
├── cloudbuild.yaml            ← GCP Cloud Build (alternative)
├── pom.xml                    ← Parent POM (Spring Boot 3.2, Java 17)
└── run-all.sh                 ← Local dev runner
```

---

## Contributing

1. Fork the repo or create a feature branch: `git checkout -b feature/your-feature`
2. Make changes, add tests
3. Push: `git push origin feature/your-feature`
4. Open a Pull Request → GitHub will run `pr-checks.yml` automatically
5. Get 1 reviewer approval → merge to `main`
6. Pipeline auto-deploys to dev

For production releases: create a `release/v1.x` branch from `main`.

---

*Nagarro Software Pvt. Ltd. — eClaims POC v1.0*
