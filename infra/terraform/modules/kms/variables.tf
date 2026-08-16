variable "name_prefix" {
  type = string
}

variable "oidc_provider_arn" {
  type        = string
  description = "EKS cluster OIDC provider ARN (module.eks.oidc_provider_arn) — required for IRSA trust policy."
}

variable "oidc_provider_url" {
  type        = string
  description = "EKS cluster OIDC provider URL without the https:// prefix (module.eks.oidc_provider_url)."
}

variable "k8s_namespace" {
  type    = string
  default = "auth-service"
}

variable "k8s_service_account" {
  type    = string
  default = "auth-service"
}

variable "tags" {
  type    = map(string)
  default = {}
}
