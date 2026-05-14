output "gke_cluster_name" {
  description = "GKE Autopilot cluster name"
  value       = google_container_cluster.eclaims.name
}

output "gke_cluster_endpoint" {
  description = "GKE cluster API endpoint"
  value       = google_container_cluster.eclaims.endpoint
  sensitive   = true
}

output "cloudsql_connection_name" {
  description = "Cloud SQL connection name for Workload Identity auth"
  value       = google_sql_database_instance.eclaims.connection_name
}

output "artifact_registry_url" {
  description = "Artifact Registry base URL"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/eclaims"
}
