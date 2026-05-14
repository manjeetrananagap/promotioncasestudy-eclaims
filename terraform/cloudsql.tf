resource "google_sql_database_instance" "eclaims" {
  name             = var.db_instance_name
  database_version = "POSTGRES_15"
  region           = var.region

  settings {
    tier              = "db-custom-4-15360"
    availability_type = "REGIONAL"   # HA with automatic failover

    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = true
      backup_retention_settings {
        retained_backups = 30
      }
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = "projects/${var.project_id}/global/networks/${var.network}"
      require_ssl     = true
    }

    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = false
    }
  }

  deletion_protection = true
}

# Cross-region read replica for DR
resource "google_sql_database_instance" "eclaims_dr" {
  name                 = "${var.db_instance_name}-dr"
  database_version     = "POSTGRES_15"
  region               = var.dr_region
  master_instance_name = google_sql_database_instance.eclaims.name

  settings {
    tier = "db-custom-2-7680"

    ip_configuration {
      ipv4_enabled    = false
      private_network = "projects/${var.project_id}/global/networks/${var.network}"
      require_ssl     = true
    }
  }

  deletion_protection = true
}

# Databases per service (database-per-service pattern)
locals {
  databases = ["claims_db", "partner_db", "notification_db", "document_db", "workflow_db"]
}

resource "google_sql_database" "eclaims_dbs" {
  for_each = toset(local.databases)
  name     = each.key
  instance = google_sql_database_instance.eclaims.name
}

resource "google_sql_user" "eclaims" {
  name     = "eclaims"
  instance = google_sql_database_instance.eclaims.name
  password = var.db_password
}

variable "db_password" {
  description = "Cloud SQL master password — injected via CI/CD secret manager"
  type        = string
  sensitive   = true
}

# ── Workload Identity service account for K8s pods ───────────────────────────
# The K8s ServiceAccount 'eclaims-sa' (in namespace 'eclaims') is annotated
# with this GCP SA, allowing pods to authenticate to Google APIs via OIDC.
resource "google_service_account" "eclaims_workload" {
  account_id   = "eclaims-workload"
  display_name = "eClaims workload identity (all pods)"
  project      = var.project_id
}

# Grant Cloud SQL client role so pods can connect via Cloud SQL Auth Proxy
resource "google_project_iam_member" "cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.eclaims_workload.email}"
}

# Bind the GCP SA to the K8s SA via Workload Identity
resource "google_service_account_iam_member" "eclaims_workload_identity" {
  service_account_id = google_service_account.eclaims_workload.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[eclaims/eclaims-sa]"

  # Identity pool `${project}.svc.id.goog` is created by GKE cluster creation.
  depends_on = [google_container_cluster.eclaims]
}

output "workload_sa_email" {
  description = "GCP service account email for Workload Identity"
  value       = google_service_account.eclaims_workload.email
}
