variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "allowed_security_group_ids" {
  type        = list(string)
  description = "Security group IDs (typically the EKS node/pod SG) allowed to reach Postgres on 5432."
}

variable "database_name" {
  type    = string
  default = "dental_clinic_auth"
}

variable "master_username" {
  type    = string
  default = "auth_service_admin"
}

variable "engine_version" {
  type    = string
  default = "16.4"
}

variable "instance_class" {
  type    = string
  default = "db.r6g.large"
}

variable "instance_count" {
  type        = number
  default     = 2
  description = "1 writer + N-1 readers. 2 gives Multi-AZ failover (Principle V)."
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "skip_final_snapshot" {
  type    = bool
  default = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
