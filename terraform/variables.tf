variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "Primary GCP region"
  type        = string
  default     = "us-central1"
}

variable "dr_region" {
  description = "DR GCP region"
  type        = string
  default     = "us-east1"
}

variable "network" {
  description = "VPC network name"
  type        = string
  default     = "eclaims-vpc"
}

variable "subnetwork" {
  description = "VPC subnetwork name"
  type        = string
  default     = "eclaims-subnet"
}

variable "gke_cluster_name" {
  description = "GKE cluster name"
  type        = string
  default     = "eclaims-autopilot"
}

variable "db_instance_name" {
  description = "Cloud SQL instance name"
  type        = string
  default     = "eclaims-postgres"
}

variable "redis_instance_name" {
  description = "Memorystore Redis instance name"
  type        = string
  default     = "eclaims-redis"
}
