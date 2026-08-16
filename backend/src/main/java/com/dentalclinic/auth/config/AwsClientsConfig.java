package com.dentalclinic.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * AWS SDK v2 client beans. No static credentials configured here — the default credential provider
 * chain resolves the pod's IRSA role (T022a / infra/terraform/modules/kms) when running on EKS, or
 * local/CI credentials otherwise.
 */
@Configuration
public class AwsClientsConfig {

  @Bean
  public KmsClient kmsClient() {
    return KmsClient.builder().build();
  }
}
