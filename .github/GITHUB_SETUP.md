# GitHub Repository Setup Guide
## eClaims POC — Nagarro Software Pvt. Ltd.

---

## 1. Create the Repository

```bash
# Create public or private repo at github.com/manjeetrananagap/promotioncasestudy-eclaims
# Then push the code:

git init
git add .
git commit -m "feat: initial eClaims POC — Nagarro"
git branch -M main
git remote add origin https://github.com/manjeetrananagap/promotioncasestudy-eclaims.git
git push -u origin main
```

---

## 2. Branch Strategy

```
main              ← protected, auto-deploys to dev
release/v1.x      ← protected, deploys to staging → production
feature/xxx        ← developer branches, PR into main
hotfix/xxx         ← emergency fixes, PR into main + release branch
```

---

## 3. Branch Protection Rules

Go to **Settings → Branches → Add rule** and set:

### `main` branch
| Setting | Value |
|---------|-------|
| Require pull request before merging | ✅ |
| Required approvals | **1** |
| Dismiss stale reviews when new commits pushed | ✅ |
| Require status checks to pass | ✅ |
| Required status checks | `compile`, `unit-tests`, `frontend-check`, `secret-scan` |
| Require branches to be up to date | ✅ |
| Require conversation resolution | ✅ |
| Restrict who can push | `manjeetrananagap` team |
| Allow force pushes | ❌ |
| Allow deletions | ❌ |

### `release/**` branches
| Setting | Value |
|---------|-------|
| Require pull request before merging | ✅ |
| Required approvals | **2** |
| Required status checks | All CI jobs |
| Restrict who can push | `manjeetrananagap` team |

---

## 4. GitHub Environments

Go to **Settings → Environments** and create:

### `development`
- No protection rules (auto-deploys on every merge to `main`)
- Environment URL: `https://dev.eclaims.yourdomain.com`

### `staging`
- Required reviewers: `manjeetrananagap` (1 reviewer)
- Deployment branches: `main`, `release/**`
- Environment URL: `https://staging.eclaims.yourdomain.com`

### `production`
- Required reviewers: `manjeetrananagap` (2 reviewers)
- Wait timer: **5 minutes**
- Deployment branches: `release/**` only
- Environment URL: `https://eclaims.yourdomain.com`

---

## 5. Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret**:

| Secret Name | Value | Where to get |
|-------------|-------|-------------|
| `GCP_PROJECT_ID` | `your-gcp-project-id` | GCP Console |
| `GCP_SA_KEY` | Base64-encoded service account JSON | `base64 -i sa-key.json` |
| `GKE_CLUSTER_NAME` | `eclaims-cluster` | GCP → GKE |
| `GKE_CLUSTER_REGION` | `us-central1` | GCP → GKE |
| `SONAR_TOKEN` | SonarCloud token | sonarcloud.io → Account → Security |
| `SONAR_HOST_URL` | `https://sonarcloud.io` | SonarCloud |
| `PROD_API_URL` | `https://api.eclaims.yourdomain.com` | Your domain |
| `PROD_KEYCLOAK_URL` | `https://auth.eclaims.yourdomain.com` | Your domain |
| `SLACK_WEBHOOK_URL` | `https://hooks.slack.com/services/...` | Slack → Apps → Incoming Webhooks |

### Create GCP Service Account
```bash
# Create service account
gcloud iam service-accounts create eclaims-github-actions \
  --display-name="eClaims GitHub Actions"

# Grant required roles
SA_EMAIL="eclaims-github-actions@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/container.developer"        # Deploy to GKE

gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer"    # Push Docker images

gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/storage.objectViewer"       # Pull from GCS

# Create and download key
gcloud iam service-accounts keys create sa-key.json \
  --iam-account="${SA_EMAIL}"

# Base64 encode for GitHub secret
base64 -i sa-key.json | tr -d '\n'
# Paste output as GCP_SA_KEY secret in GitHub

# Delete local key file immediately
rm sa-key.json
```

---

## 6. GitHub Teams (Recommended)

Create these teams at **github.com/manjeetrananagap/teams**:

| Team | Members | Permissions |
|------|---------|------------|
| `eclaims-team` | All developers | Write to repo |
| `eclaims-leads` | Tech leads | Staging environment approval |
| `eclaims-releases` | Release managers | Production environment approval |

---

## 7. GitHub Actions Workflow Overview

```
Pull Request to main:
  pr-checks.yml
    ├── compile          (fast — 2 min)
    ├── unit-tests       (with Postgres + Kafka — 5 min)
    ├── frontend-check   (TypeScript + build — 3 min)
    ├── secret-scan      (TruffleHog — 1 min)
    └── label-size       (auto-label PR size)

Push to main (after PR merge):
  ci-cd.yml
    ├── validate         (maven validate + K8s lint)
    ├── build-backend    (compile + test + JaCoCo)
    ├── build-frontend   (TypeScript + npm build)
    ├── sonarqube        (quality gate — blocks on fail)
    ├── security         (OWASP + CodeQL + Trivy)
    ├── docker-build     (parallel matrix × 7 services)
    ├── deploy-dev       (automatic — no approval)
    ├── integration-tests
    └── deploy-staging   ← REQUIRES 1 REVIEWER APPROVAL

Push to release/** branch:
  All jobs above PLUS:
    └── deploy-production ← REQUIRES 2 REVIEWERS + 5 min timer
        └── Creates GitHub Release + tags
```

---

## 8. SonarCloud Setup

1. Go to [sonarcloud.io](https://sonarcloud.io) and log in with GitHub
2. Click **+** → **Analyze new project** → select `manjeetrananagap/promotioncasestudy-eclaims`
3. Choose **GitHub Actions** as the CI
4. Copy the `SONAR_TOKEN` to GitHub Secrets
5. Set `SONAR_HOST_URL` = `https://sonarcloud.io`

Add to each service's `pom.xml` (under `<properties>`):
```xml
<sonar.projectKey>manjeetrananagap_promotioncasestudy-eclaims</sonar.projectKey>
<sonar.organization>manjeetrananagap</sonar.organization>
```

---

## 9. First Deployment

```bash
# 1. Ensure GKE cluster exists
gcloud container clusters create eclaims-cluster \
  --enable-autopilot \
  --region us-central1 \
  --project $GCP_PROJECT_ID

# 2. Create Artifact Registry repository
gcloud artifacts repositories create eclaims \
  --repository-format=docker \
  --location=us-central1

# 3. Create namespaces
kubectl create namespace eclaims-dev
kubectl create namespace eclaims-staging
kubectl create namespace eclaims

# 4. Create DB secret in each namespace
kubectl create secret generic eclaims-db-secret \
  --from-literal=password='YOUR_DB_PASS' \
  -n eclaims-dev

# 5. Merge a PR to main → pipeline auto-deploys to dev ✅
```

---

## 10. ArgoCD GitOps

After deploying ArgoCD to the cluster:

```bash
# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f \
  https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Apply eClaims app definitions
kubectl apply -f k8s/argocd/applications.yaml

# Get initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d

# Port-forward ArgoCD UI
kubectl port-forward svc/argocd-server -n argocd 8888:443
# Open: https://localhost:8888
```

The CI/CD pipeline commits updated `kustomization.yaml` files back to the repo.  
ArgoCD watches the repo and auto-syncs dev/staging. Production requires manual sync via the ArgoCD UI.
