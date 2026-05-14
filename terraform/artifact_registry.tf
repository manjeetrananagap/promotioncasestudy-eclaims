resource "google_artifact_registry_repository" "eclaims" {
  location      = var.region
  repository_id = "eclaims"
  description   = "eClaims microservices container images"
  format        = "DOCKER"

  cleanup_policies {
    id     = "keep-recent-10"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }
}
