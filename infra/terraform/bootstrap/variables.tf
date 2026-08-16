variable "region" {
  type    = string
  default = "eu-central-1" # Frankfurt — keeps patient/staff personal data in the EU (RODO)
}

variable "state_bucket_name" {
  type    = string
  default = "dental-clinic-terraform-state"
}

variable "lock_table_name" {
  type    = string
  default = "dental-clinic-terraform-locks"
}
