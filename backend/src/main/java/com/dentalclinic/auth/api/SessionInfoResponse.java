package com.dentalclinic.auth.api;

/** Per contracts/auth-api.yaml {@code GET /auth/session}. */
public record SessionInfoResponse(String role) {}
