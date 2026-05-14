# ==============================================================================
# VPC Networking — eClaims
# Creates the VPC, subnets, Private Service Access (for Cloud SQL),
# Cloud Router, and Cloud NAT required by GKE Autopilot and Cloud SQL.
# ==============================================================================

# ── VPC Network ───────────────────────────────────────────────────────────────
resource "google_compute_network" "eclaims_vpc" {
  name                    = var.network
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"

  depends_on = [google_project_service.apis]
}

# ── Primary Subnet (GKE nodes + secondary ranges for pods/services) ──────────
resource "google_compute_subnetwork" "eclaims_subnet" {
  name          = var.subnetwork
  ip_cidr_range = "10.100.0.0/20"
  region        = var.region
  network       = google_compute_network.eclaims_vpc.id

  # Required for Autopilot nodes to reach Google APIs without NAT
  private_ip_google_access = true

  # Secondary ranges for VPC-native GKE pod/service CIDRs
  secondary_ip_range {
    range_name    = "gke-pods"
    ip_cidr_range = "10.101.0.0/16"
  }

  secondary_ip_range {
    range_name    = "gke-services"
    ip_cidr_range = "10.102.0.0/20"
  }

  # Autopilot may add/reshape secondary ranges; do not force destructive updates
  # once the subnet is attached to a running cluster.
  lifecycle {
    ignore_changes = [secondary_ip_range]
  }
}

# ── Private Service Access for Cloud SQL (RFC 1918 peering) ──────────────────
resource "google_compute_global_address" "private_service_range" {
  name          = "eclaims-private-service-range"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.eclaims_vpc.id

  depends_on = [google_project_service.apis]
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.eclaims_vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_service_range.name]
}

# ── Cloud Router + NAT (outbound internet for private GKE nodes) ─────────────
resource "google_compute_router" "eclaims_router" {
  name    = "eclaims-router"
  region  = var.region
  network = google_compute_network.eclaims_vpc.id
}

resource "google_compute_router_nat" "eclaims_nat" {
  name                               = "eclaims-nat"
  router                             = google_compute_router.eclaims_router.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}

# ── Firewall: allow internal traffic within the VPC ──────────────────────────
resource "google_compute_firewall" "allow_internal" {
  name    = "eclaims-allow-internal"
  network = google_compute_network.eclaims_vpc.id

  allow {
    protocol = "tcp"
  }
  allow {
    protocol = "udp"
  }
  allow {
    protocol = "icmp"
  }

  source_ranges = [
    "10.100.0.0/20",  # node subnet
    "10.101.0.0/16",  # pod CIDR
    "10.102.0.0/20",  # service CIDR
  ]

  priority = 1000
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "vpc_network_id" {
  description = "VPC network self-link"
  value       = google_compute_network.eclaims_vpc.id
}

output "subnet_id" {
  description = "Primary subnet self-link"
  value       = google_compute_subnetwork.eclaims_subnet.id
}
