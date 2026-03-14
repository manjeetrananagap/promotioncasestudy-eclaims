#!/usr/bin/env bash
##############################################################################
# eClaims — GitHub Push Script
# Repository: https://github.com/manjeetrananagap/promotioncasestudy-eclaims
#
# Run this from the project root after unzipping:
#   chmod +x push-to-github.sh
#   ./push-to-github.sh
##############################################################################

set -euo pipefail

REPO="https://github.com/manjeetrananagap/promotioncasestudy-eclaims.git"

RED='\033[0;31m';GREEN='\033[0;32m';YELLOW='\033[1;33m'
BLUE='\033[0;34m';BOLD='\033[1m';NC='\033[0m'

echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  eClaims POC — Push to GitHub${NC}"
echo -e "${BOLD}  ${BLUE}${REPO}${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo

# ── Check git is installed ───────────────────────────────────────────────────
command -v git >/dev/null || { echo -e "${RED}Error: git not installed${NC}"; exit 1; }

# ── Initialize if not already a git repo ────────────────────────────────────
if [ ! -d ".git" ]; then
  echo -e "${YELLOW}Initializing git repository...${NC}"
  git init
  git branch -M main
fi

# ── Configure remote ─────────────────────────────────────────────────────────
if git remote get-url origin &>/dev/null; then
  git remote set-url origin "$REPO"
  echo -e "${GREEN}✓ Remote 'origin' updated${NC}"
else
  git remote add origin "$REPO"
  echo -e "${GREEN}✓ Remote 'origin' added${NC}"
fi

echo -e "  Remote: ${BLUE}$(git remote get-url origin)${NC}"
echo

# ── Stage all files ───────────────────────────────────────────────────────────
echo -e "${YELLOW}Staging all files...${NC}"
git add .
echo -e "${GREEN}✓ Files staged: $(git diff --cached --name-only | wc -l) files${NC}"
echo

# ── Show what will be committed ───────────────────────────────────────────────
echo -e "${BOLD}Files to commit (summary by type):${NC}"
git diff --cached --name-only | awk -F. '{print $NF}' | sort | uniq -c | sort -rn | \
  while read count ext; do
    echo "  ${count}x .${ext}"
  done
echo

# ── Initial commit ────────────────────────────────────────────────────────────
if git diff --cached --quiet; then
  echo -e "${YELLOW}Nothing to commit — working tree clean${NC}"
else
  git commit -m "feat: eClaims POC — Nagarro Senior Staff Engineer Assessment

Complete event-driven microservices platform for YCompany insurance claims.

Backend (Java 17 + Spring Boot 3.2):
- api-gateway      :8080  Spring Cloud Gateway + OAuth2 JWT
- claims-service   :8081  FNOL + 12-state lifecycle + Kafka
- notification-svc :8082  Email/SMS consumers (Keycloak + Mailhog)
- document-service :8083  MinIO/GCS upload + pre-signed URLs
- partner-service  :8084  Haversine geo-radius + workshop matching
- workflow-service :8085  Camunda 8 (Zeebe) BPMN 2.0 process
- shared-events         Kafka event DTOs (11 topics)

Frontend (React.js 18 + TypeScript + TailwindCSS):
- Customer portal (FNOL form, GPS capture, claim timeline)
- Adjustor dashboard (claims queue, approve/reject with financials)
- Surveyor + Partner portals
- Role-based routing via Keycloak JWT

Infrastructure:
- docker-compose.yml  18 containers (Postgres, Kafka, Keycloak, MinIO,
                      Zeebe, Elasticsearch, Camunda Operate, Prometheus, Grafana)
- Kubernetes manifests (GKE Autopilot, HPA, Ingress, RBAC, Kustomize overlays)
- ArgoCD GitOps applications

CI/CD (GitHub Actions):
- ci-cd.yml      11-job pipeline: validate → build → sonar → security
                 → docker-build (×7 parallel) → deploy-dev (auto)
                 → integration-tests → staging (1 reviewer)
                 → production (2 reviewers + 5min timer)
- pr-checks.yml  Fast PR feedback (compile + test + typecheck + secret-scan)
- dependabot.yml Weekly auto-updates for Maven, npm, Docker, Actions

Technology: Java 17 · Spring Boot 3 · React.js 18 · Kafka · Camunda 8
            Keycloak · MinIO · PostgreSQL · GKE Autopilot · Terraform · ArgoCD"

  echo -e "${GREEN}✓ Initial commit created${NC}"
fi

echo
echo -e "${BOLD}Ready to push. Run:${NC}"
echo -e "  ${BLUE}git push -u origin main${NC}"
echo
echo -e "${YELLOW}If the repository already has commits, use:${NC}"
echo -e "  ${BLUE}git push -u origin main --force${NC}"
echo
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  After pushing, configure GitHub:${NC}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo
echo -e "1. ${BOLD}Secrets${NC} (Settings → Secrets → Actions):"
echo -e "   ${YELLOW}GCP_PROJECT_ID${NC}      Your GCP project ID"
echo -e "   ${YELLOW}GCP_SA_KEY${NC}          Service account JSON (base64)"
echo -e "   ${YELLOW}GKE_CLUSTER_NAME${NC}    eclaims-cluster"
echo -e "   ${YELLOW}GKE_CLUSTER_REGION${NC}  us-central1"
echo -e "   ${YELLOW}SONAR_TOKEN${NC}         From sonarcloud.io"
echo -e "   ${YELLOW}SONAR_HOST_URL${NC}      https://sonarcloud.io"
echo -e "   ${YELLOW}PROD_API_URL${NC}        https://api.eclaims.yourdomain.com"
echo -e "   ${YELLOW}PROD_KEYCLOAK_URL${NC}   https://auth.eclaims.yourdomain.com"
echo -e "   ${YELLOW}SLACK_WEBHOOK_URL${NC}   Your Slack webhook"
echo
echo -e "2. ${BOLD}Environments${NC} (Settings → Environments):"
echo -e "   ${YELLOW}development${NC}   — no rules (auto-deploys)"
echo -e "   ${YELLOW}staging${NC}       — 1 required reviewer"
echo -e "   ${YELLOW}production${NC}    — 2 reviewers + 5 min wait timer"
echo
echo -e "3. ${BOLD}Branch protection${NC} on ${YELLOW}main${NC} (Settings → Branches):"
echo -e "   Require PR · 1 approval · status checks: compile, unit-tests, frontend-check"
echo
echo -e "4. ${BOLD}Full setup guide:${NC} ${BLUE}.github/GITHUB_SETUP.md${NC}"
echo
echo -e "5. ${BOLD}Local dev:${NC}"
echo -e "   ${BLUE}./run-all.sh start${NC}      # Start everything locally"
echo -e "   ${BLUE}./run-all.sh test-claim${NC}  # End-to-end test"
echo
