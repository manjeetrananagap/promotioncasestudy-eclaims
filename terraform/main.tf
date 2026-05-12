terraform {
  required_version = ">= 1.6.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
  backend "gcs" {
    # bucket name is passed via -backend-config at init time
    # e.g. terraform init -backend-config="bucket=${PROJECT_ID}-tfstate"
    bucket = "eclaims-tfstate"
    prefix = "eclaims/state"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# ── Enable required GCP APIs (must be done before any other resources) ────────
resource "google_project_service" "apis" {
  for_each = toset([
    "container.googleapis.com",           # GKE
    "sqladmin.googleapis.com",            # Cloud SQL
    "redis.googleapis.com",               # Memorystore
    "artifactregistry.googleapis.com",    # Artifact Registry
    "servicenetworking.googleapis.com",   # Private Service Connect (Cloud SQL)
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "cloudbuild.googleapis.com",
    "secretmanager.googleapis.com",
    "compute.googleapis.com",             # VPC / networking
    "monitoring.googleapis.com",
    "logging.googleapis.com",
  ])
  service            = each.key
  disable_on_destroy = false
}
