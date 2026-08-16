# PROD environment root module — persistent, always-on (Environments & Release Process). Never
# shares a cluster or failure domain with DEV (own VPC/EKS/RDS, own state file). All deployments
# into this environment go through canary progressive delivery (Argo Rollouts) — see
# .github/workflows/deploy.yml — never a direct-to-100% rollout except documented emergency
# rollback to a previously-canaried version.

locals {
  tags = {
    Environment = "prod"
  }
}

module "vpc" {
  source = "../../modules/vpc"

  name_prefix = var.name_prefix
  az_count    = 3 # wider spread than dev for Principle V high-availability
  tags        = local.tags
}

module "eks" {
  source = "../../modules/eks"

  name_prefix         = var.name_prefix
  private_subnet_ids  = module.vpc.private_subnet_ids
  node_desired_size   = 3
  node_min_size       = 3
  node_max_size       = 6
  node_instance_types = ["m6i.xlarge"]
  tags                = local.tags
}

module "rds" {
  source = "../../modules/rds"

  name_prefix                 = var.name_prefix
  vpc_id                      = module.vpc.vpc_id
  private_subnet_ids          = module.vpc.private_subnet_ids
  allowed_security_group_ids  = []
  instance_class               = "db.r6g.xlarge"
  instance_count                = 2 # writer + reader — Multi-AZ failover (Principle V)
  deletion_protection           = true
  skip_final_snapshot           = false
  tags                          = local.tags
}

module "kms" {
  source = "../../modules/kms"

  name_prefix       = var.name_prefix
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider_url = module.eks.oidc_provider_url
  tags              = local.tags
}
