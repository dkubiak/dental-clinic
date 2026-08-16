output "key_id" {
  value = aws_kms_key.mfa_secret.key_id
}

output "key_arn" {
  value = aws_kms_key.mfa_secret.arn
}

output "alias_name" {
  value = aws_kms_alias.mfa_secret.name
}

output "irsa_role_arn" {
  value       = aws_iam_role.mfa_secret_kms.arn
  description = "Annotate the auth-service K8s ServiceAccount with eks.amazonaws.com/role-arn = this value (see helm/auth-service values.yaml)."
}
