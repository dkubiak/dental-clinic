# KMS module (T009a) — customer-managed key encrypting MfaEnrollment.totp_secret_encrypted
# (FR-013; research.md #11). Access is granted only to the auth-service pod's IRSA role
# (T022a), never a static IAM user credential.

terraform {
  required_version = ">= 1.9"
}

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "mfa_secret" {
  description             = "Encrypts MfaEnrollment.totp_secret_encrypted for ${var.name_prefix} (FR-013)"
  deletion_window_in_days = 30
  enable_key_rotation     = true # automatic annual rotation — see CHK022 in checklists/security.md

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AccountRootFullAccess"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      },
      {
        Sid       = "AuthServiceEncryptDecrypt"
        Effect    = "Allow"
        Principal = { AWS = aws_iam_role.mfa_secret_kms.arn }
        Action    = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"]
        Resource  = "*"
      }
    ]
  })

  tags = var.tags
}

resource "aws_kms_alias" "mfa_secret" {
  name          = "alias/${var.name_prefix}-mfa-secret"
  target_key_id = aws_kms_key.mfa_secret.key_id
}

# IRSA role (T022a) — trust policy scoped to the auth-service Kubernetes ServiceAccount only,
# via the EKS cluster's OIDC provider (module.eks output).
resource "aws_iam_role" "mfa_secret_kms" {
  name = "${var.name_prefix}-auth-service-kms-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = var.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${var.oidc_provider_url}:sub" = "system:serviceaccount:${var.k8s_namespace}:${var.k8s_service_account}"
          "${var.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "mfa_secret_kms" {
  name = "${var.name_prefix}-mfa-secret-kms-policy"
  role = aws_iam_role.mfa_secret_kms.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"]
      Resource = aws_kms_key.mfa_secret.arn
    }]
  })
}
