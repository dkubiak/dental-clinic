terraform {
  backend "s3" {
    bucket         = "dental-clinic-terraform-state"
    key            = "prod/auth-service.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "dental-clinic-terraform-locks"
    encrypt        = true
  }
}
