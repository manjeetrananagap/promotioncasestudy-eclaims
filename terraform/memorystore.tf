resource "google_redis_instance" "eclaims" {
  name           = var.redis_instance_name
  tier           = "STANDARD_HA"
  memory_size_gb = 4
  region         = var.region

  location_id             = "${var.region}-a"
  alternative_location_id = "${var.region}-b"

  redis_version     = "REDIS_7_0"
  display_name      = "eClaims Memorystore Redis"
  reserved_ip_range = "10.0.0.0/29"

  auth_enabled = true

  persistence_config {
    persistence_mode    = "RDB"
    rdb_snapshot_period = "ONE_HOUR"
  }

  maintenance_policy {
    weekly_maintenance_window {
      day = "SATURDAY"
      start_time {
        hours   = 2
        minutes = 0
        seconds = 0
        nanos   = 0
      }
    }
  }
}

output "redis_host" {
  value = google_redis_instance.eclaims.host
}

output "redis_port" {
  value = google_redis_instance.eclaims.port
}
