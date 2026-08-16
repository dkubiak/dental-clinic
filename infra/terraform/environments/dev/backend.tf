# Isolated state per environment (Environments & Release Process — "DEV MUST be provisioned from
# its own dedicated Terraform workspace/state, isolated from PROD state"). A distinct S3 key
# (not a `terraform workspace`) is used so dev/prod can never accidentally share a state file
# even if someone forgets to switch workspaces.
terraform {
  backend "s3" {
    bucket         = "dental-clinic-terraform-state"
    key            = "dev/auth-service.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "dental-clinic-terraform-locks"
    encrypt        = true
  }
}
