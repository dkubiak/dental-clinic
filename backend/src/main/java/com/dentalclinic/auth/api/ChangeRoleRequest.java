package com.dentalclinic.auth.api;

import com.dentalclinic.auth.role.Role;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /accounts/{id}} request body (T077, contracts/auth-api.yaml). */
public record ChangeRoleRequest(@NotNull Role role) {}
