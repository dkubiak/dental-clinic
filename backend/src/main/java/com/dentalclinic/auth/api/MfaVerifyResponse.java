package com.dentalclinic.auth.api;

import com.dentalclinic.auth.role.Role;

public record MfaVerifyResponse(Role role) {}
