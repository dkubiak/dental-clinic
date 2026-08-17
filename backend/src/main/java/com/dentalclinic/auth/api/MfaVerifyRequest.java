package com.dentalclinic.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(
    @NotBlank String preAuthToken, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String totpCode) {}
