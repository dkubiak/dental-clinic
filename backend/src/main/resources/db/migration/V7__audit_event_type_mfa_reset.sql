-- FR-015b: admin-triggered MFA reset (POST /accounts/{id}/mfa-reset) is always audit-logged as
-- MFA_RESET (data-model.md MfaEnrollment "Admin-triggered reset" section).
ALTER TYPE audit_event_type ADD VALUE 'MFA_RESET';
