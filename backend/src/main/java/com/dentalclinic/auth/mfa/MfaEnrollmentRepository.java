package com.dentalclinic.auth.mfa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaEnrollmentRepository extends JpaRepository<MfaEnrollment, UUID> {}
