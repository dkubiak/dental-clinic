package com.dentalclinic.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS SDK v2 client beans. No static credentials configured here — the default credential provider
 * chain resolves the pod's IRSA role (T022a / infra/terraform/modules/kms) when running on EKS, or
 * local/CI credentials otherwise.
 *
 * <p>{@code !e2e-seed}: the {@code e2e-seed} profile (local Playwright runs, quickstart.md — "local
 * KMS-equivalent") substitutes stub beans instead (see E2eStubAwsConfig) so a developer can run the
 * full login flow locally without provisioning real AWS credentials or LocalStack.
 */
@Configuration
@Profile("!e2e-seed")
public class AwsClientsConfig {

  @Bean
  public KmsClient kmsClient() {
    return KmsClient.builder().build();
  }

  @Bean
  public SesClient sesClient() {
    return SesClient.builder().build();
  }
}
