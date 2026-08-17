package com.dentalclinic.auth.api;

import com.dentalclinic.auth.role.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /accounts} request body (T077, contracts/auth-api.yaml). */
public record CreateAccountRequest(@NotBlank @Email String email, @NotNull Role role) {}
