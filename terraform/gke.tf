resource "google_container_cluster" "eclaims" {
  name     = var.gke_cluster_name
  location = var.region

  enable_autopilot = true

  network    = var.network
  subnetwork = var.subnetwork

  release_channel {
    channel = "REGULAR"
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  # Anthos Service Mesh (Istio managed)
  addons_config {
    http_load_balancing {
      disabled = false
    }
  }

  binary_authorization {
    evaluation_mode = "PROJECT_SINGLETON_POLICY_ENFORCE"
  }

  logging_config {
    enable_components = ["SYSTEM_COMPONENTS", "WORKLOADS"]
  }

  monitoring_config {
    enable_components = ["SYSTEM_COMPONENTS"]
    managed_prometheus {
      enabled = true
    }
  }
}

# Workload Identity service accounts — one per microservice
locals {
  services = ["claims", "partner", "notification", "document", "workflow", "api-gateway"]
}

resource "google_service_account" "eclaims_service" {
  for_each     = toset(local.services)
  account_id   = "eclaims-${each.key}"
  display_name = "eClaims ${each.key} service account"
}

resource "google_service_account_iam_member" "workload_identity" {
  for_each           = toset(local.services)
  service_account_id = google_service_account.eclaims_service[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[eclaims-prod/eclaims-${each.key}]"

  # Identity pool `${project}.svc.id.goog` is created by GKE cluster creation.
  depends_on = [google_container_cluster.eclaims]
}
