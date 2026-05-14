#!/usr/bin/env bash
# =============================================================================
# deploy-gcp.sh — eClaims End-to-End GCP Deployment Script
# Nagarro Software Pvt. Ltd.
#
# What this script does:
#   1. Validates prerequisites (gcloud, terraform, kubectl, kustomize)
#   2. Creates the GCS bucket for Terraform remote state (idempotent)
#   3. Provisions GCP infrastructure via Terraform (VPC, GKE, Cloud SQL, Redis)
#   4. Configures kubectl to talk to the GKE Autopilot cluster
#   5. Builds & pushes all Docker images via Cloud Build
#   6. Creates Kubernetes secrets + updates ConfigMap with GCP-native values
#   7. Substitutes Cloud SQL connection name into the proxy manifest
#   8. Deploys all services to GKE using Kustomize
#   9. Waits for all Deployments to roll out sucessfully
#
# Usage:
#   export GCP_PROJECT_ID=eclaims-promotioncasestudy
#   export DB_PASSWORD=<strong-password>          # optional — script will prompt
#   ./deploy-gcp.sh [dev|staging|prod]             # defaults to prod
#
# Required tools:
#   gcloud CLI  (authenticated via: gcloud auth login && gcloud auth application-default login)
#   terraform   >= 1.6
#   kubectl
#   kustomize   >= 5.0  (install: curl -sL .../install_kustomize.sh | bash)
# =============================================================================

set -euo pipefail

# ── Configuration (override via environment variables) ───────────────────────
PROJECT_ID="${GCP_PROJECT_ID:-eclaims-promotioncasestudy}"
REGION="${GCP_REGION:-us-central1}"
CLUSTER_NAME="${GKE_CLUSTER_NAME:-eclaims-autopilot}"
OVERLAY="${1:-prod}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo "latest")}"
TFSTATE_BUCKET="${PROJECT_ID}-tfstate"
REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/eclaims"
NAMESPACE="eclaims"
# For dev overlay the namespace differs
[[ "${OVERLAY}" == "dev" ]] && NAMESPACE="eclaims-dev"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
die()  { echo "ERROR: $*" >&2; exit 1; }
hr()   { echo ""; echo "=== $* ==="; }

# ── 1. Prerequisites ─────────────────────────────────────────────────────────
hr "Checking prerequisites"
for cmd in gcloud terraform kubectl kustomize; do
  command -v "${cmd}" >/dev/null 2>&1 || die "${cmd} is not installed or not on PATH"
  log "  ${cmd}: OK"
done

TERRAFORM_VERSION=$(terraform version -json | grep -oE '"terraform_version"\s*:\s*"[^"]+"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/' || terraform version | head -1 | grep -oP '[\d.]+')
log "  terraform ${TERRAFORM_VERSION}"

# ── 2. GCP authentication & project ──────────────────────────────────────────
hr "Configuring GCP project"
gcloud config set project "${PROJECT_ID}" --quiet
ACTIVE_ACCOUNT=$(gcloud config get-value account 2>/dev/null)
log "  Active account : ${ACTIVE_ACCOUNT}"
log "  Project        : ${PROJECT_ID}"
log "  Region         : ${REGION}"
log "  Overlay        : ${OVERLAY}"
log "  Image tag      : ${IMAGE_TAG}"

# ── 3. Terraform state bucket (idempotent) ────────────────────────────────────
hr "Ensuring Terraform state bucket"
if ! gsutil ls "gs://${TFSTATE_BUCKET}" >/dev/null 2>&1; then
  log "Creating GCS bucket gs://${TFSTATE_BUCKET} ..."
  gsutil mb -p "${PROJECT_ID}" -l "${REGION}" "gs://${TFSTATE_BUCKET}"
  gsutil versioning set on "gs://${TFSTATE_BUCKET}"
  log "  Bucket created and versioning enabled"
else
  log "  Bucket gs://${TFSTATE_BUCKET} already exists"
fi

# ── 4. Terraform: provision infrastructure ───────────────────────────────────
hr "Provisioning GCP infrastructure"
pushd terraform >/dev/null

if [[ ! -f terraform.tfvars ]]; then
  cp terraform.tfvars.example terraform.tfvars
  # Substitute the example project ID with the actual one
  sed -i.bak "s|eclaims-promotioncasestudy|${PROJECT_ID}|g" terraform.tfvars
  rm -f terraform.tfvars.bak

  # Prompt if DB_PASSWORD not set in environment
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    read -rsp "  Enter a strong DB password for the Cloud SQL 'eclaims' user: " DB_PASSWORD
    echo ""
  fi
  sed -i.bak "s|CHANGE_ME_STRONG_PASSWORD|${DB_PASSWORD}|g" terraform.tfvars
  rm -f terraform.tfvars.bak
  log "Created terraform.tfvars — you can edit it at terraform/terraform.tfvars"
fi

terraform init \
  -reconfigure \
  -backend-config="bucket=${TFSTATE_BUCKET}" \
  -backend-config="prefix=eclaims/state" \
  -input=false

terraform validate
terraform plan \
  -out=tfplan \
  -var="project_id=${PROJECT_ID}" \
  -var="region=${REGION}" \
  -input=false

terraform apply -auto-approve -input=false tfplan
rm -f tfplan

popd >/dev/null
log "Infrastructure provisioned successfully"

# ── 5. Collect Terraform outputs ─────────────────────────────────────────────
hr "Collecting Terraform outputs"
pushd terraform >/dev/null
CLOUD_SQL_CONNECTION=$(terraform output -raw cloudsql_connection_name)
REDIS_HOST=$(terraform output -raw redis_host)
REDIS_PORT=$(terraform output -raw redis_port)
popd >/dev/null

log "  Cloud SQL connection : ${CLOUD_SQL_CONNECTION}"
log "  Redis               : ${REDIS_HOST}:${REDIS_PORT}"

# ── 6. Configure kubectl ──────────────────────────────────────────────────────
hr "Configuring kubectl"
gcloud container clusters get-credentials "${CLUSTER_NAME}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}"
log "  Cluster endpoint: $(kubectl config current-context)"

# ── 7. Build & push Docker images via Cloud Build ────────────────────────────
hr "Building and pushing Docker images (Cloud Build)"
gcloud builds submit \
  --config=cloudbuild.yaml \
  --substitutions="_REGION=${REGION},SHORT_SHA=${IMAGE_TAG}" \
  --project="${PROJECT_ID}" \
  .
log "All images pushed to ${REGISTRY}"

# ── 8. Kubernetes namespace ───────────────────────────────────────────────────
hr "Ensuring Kubernetes namespace: ${NAMESPACE}"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# ── 9. Kubernetes Secrets ─────────────────────────────────────────────────────
hr "Creating Kubernetes secrets"

# DB password
if [[ -z "${DB_PASSWORD:-}" ]]; then
  read -rsp "  Enter the DB password (must match terraform.tfvars db_password): " DB_PASSWORD
  echo ""
fi
kubectl create secret generic eclaims-db-secret \
  --namespace="${NAMESPACE}" \
  --from-literal=password="${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -
log "  eclaims-db-secret created/updated"

# MinIO/GCS credentials (kept for document-service compatibility)
MINIO_ACCESS="${MINIO_ACCESS_KEY:-eclaims_admin}"
MINIO_SECRET="${MINIO_SECRET_KEY:-eclaims_secret_2024}"
kubectl create secret generic eclaims-minio-secret \
  --namespace="${NAMESPACE}" \
  --from-literal=access-key="${MINIO_ACCESS}" \
  --from-literal=secret-key="${MINIO_SECRET}" \
  --dry-run=client -o yaml | kubectl apply -f -
log "  eclaims-minio-secret created/updated"

# ── 10. ConfigMap with GCP-native values ──────────────────────────────────────
hr "Updating ConfigMap with GCP values"
KEYCLOAK_URI="${KEYCLOAK_ISSUER_URI:-https://auth.eclaims.yourdomain.com/realms/eclaims}"
kubectl create configmap eclaims-config \
  --namespace="${NAMESPACE}" \
  --from-literal=SPRING_KAFKA_BOOTSTRAP_SERVERS="kafka-service:9092" \
  --from-literal=SPRING_DATASOURCE_USERNAME="eclaims" \
  --from-literal=KEYCLOAK_ISSUER_URI="${KEYCLOAK_URI}" \
  --from-literal=MINIO_ENDPOINT="https://storage.googleapis.com" \
  --from-literal=ZEEBE_GATEWAY_ADDRESS="zeebe-service:26500" \
  --from-literal=REDIS_HOST="${REDIS_HOST}" \
  --from-literal=REDIS_PORT="${REDIS_PORT}" \
  --dry-run=client -o yaml | kubectl apply -f -
log "  eclaims-config created/updated"

# ── 11. Inject Cloud SQL connection name into proxy manifest ──────────────────
hr "Patching Cloud SQL connection name"
# Use a temporary copy so the source file stays clean
TMP_PROXY=$(mktemp /tmp/cloud-sql-proxy-XXXXXX.yaml)
sed "s|CLOUD_SQL_CONNECTION_NAME|${CLOUD_SQL_CONNECTION}|g" \
  k8s/base/cloud-sql-proxy.yaml > "${TMP_PROXY}"
log "  Connection name: ${CLOUD_SQL_CONNECTION}"

# ── 12. Deploy via Kustomize ──────────────────────────────────────────────────
hr "Deploying services to GKE (overlay: ${OVERLAY})"

# Build kustomize output and inject the patched proxy manifest
kustomize build "k8s/overlays/${OVERLAY}" \
  | sed "s|CLOUD_SQL_CONNECTION_NAME|${CLOUD_SQL_CONNECTION}|g" \
  | sed "s|IMAGE_TAG|${IMAGE_TAG}|g" \
  | kubectl apply -f -

# Also apply the patched proxy directly in case kustomize doesn't include it in the namespace
kubectl apply -f "${TMP_PROXY}" --namespace="${NAMESPACE}"
rm -f "${TMP_PROXY}"
log "  Manifests applied"

# ── 13. Wait for rollouts ─────────────────────────────────────────────────────
hr "Waiting for Deployments"
DEPLOYMENTS=(
  cloud-sql-proxy
  claims-service
  notification-service
  document-service
  partner-service
  workflow-service
  api-gateway
  frontend
)
for svc in "${DEPLOYMENTS[@]}"; do
  # dev overlay adds "-dev" suffix via nameSuffix
  SVC_NAME="${svc}"
  [[ "${OVERLAY}" == "dev" ]] && SVC_NAME="${svc}-dev"
  log "  Waiting: ${SVC_NAME} ..."
  kubectl rollout status "deployment/${SVC_NAME}" \
    --namespace="${NAMESPACE}" \
    --timeout=300s
done

# ── 14. Summary ───────────────────────────────────────────────────────────────
hr "Deployment complete"
echo ""
echo "  Project    : ${PROJECT_ID}"
echo "  Cluster    : ${CLUSTER_NAME} (${REGION})"
echo "  Namespace  : ${NAMESPACE}"
echo "  Image tag  : ${IMAGE_TAG}"
echo ""
echo "  Pods:"
kubectl get pods --namespace="${NAMESPACE}"
echo ""
echo "  Ingress (external IP may take 2-3 min):"
kubectl get ingress --namespace="${NAMESPACE}"
echo ""
echo "Next steps:"
echo "  1. Point your domain to the ingress IP"
echo "  2. Google-managed SSL cert will auto-provision (~15 min)"
echo "  3. Set up GitHub Secrets per .github/GITHUB_SETUP.md for CI/CD"
