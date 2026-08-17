package com.dentalclinic.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
    @NotBlank String token, @NotBlank @Size(min = 12) String newPassword) {}
