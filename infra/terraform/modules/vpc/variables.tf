variable "name_prefix" {
  type        = string
  description = "Prefix for resource names, e.g. \"dental-clinic-dev\"."
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR block for the VPC."
}

variable "az_count" {
  type        = number
  default     = 2
  description = "Number of availability zones to spread public/private subnets across (Principle V redundancy)."
}

variable "tags" {
  type        = map(string)
  default     = {}
  description = "Common tags applied to all resources in this module."
}
