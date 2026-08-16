# DEV environment root module — ephemeral, on-demand (Environments & Release Process). Applied
# by the terraform.yml GitHub Actions workflow when pre-PROD verification is needed, and
# `terraform destroy`ed again once testing completes (never left running indefinitely).

locals {
  tags = {
    Environment = "dev"
  }
}

module "vpc" {
  source = "../../modules/vpc"

  name_prefix = var.name_prefix
  az_count    = 2 # minimum for Principle V redundancy checks in dev; prod uses 3
  tags        = local.tags
}

module "eks" {
  source = "../../modules/eks"

  name_prefix         = var.name_prefix
  private_subnet_ids  = module.vpc.private_subnet_ids
  node_desired_size   = 2
  node_min_size       = 1 # dev tolerates a smaller footprint than prod
  node_max_size       = 3
  node_instance_types = ["m6i.large"]
  tags                = local.tags
}

module "rds" {
  source = "../../modules/rds"

  name_prefix                = var.name_prefix
  vpc_id                     = module.vpc.vpc_id
  private_subnet_ids         = module.vpc.private_subnet_ids
  allowed_security_group_ids = [] # populated once the EKS node/pod SG is known (see helm values)
  instance_class              = "db.r6g.large"
  instance_count               = 1 # single instance in dev — no Multi-AZ cost in an ephemeral env
  deletion_protection          = false
  skip_final_snapshot          = true
  tags                         = local.tags
}

module "kms" {
  source = "../../modules/kms"

  name_prefix        = var.name_prefix
  oidc_provider_arn  = module.eks.oidc_provider_arn
  oidc_provider_url  = module.eks.oidc_provider_url
  tags               = local.tags
}
