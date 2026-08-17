package com.dentalclinic.auth.e2eseed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

/**
 * Stub AWS clients for local Playwright/manual runs ({@code -Dspring.profiles.active=e2e-seed}) —
 * KMS "encryption" is a passthrough (ciphertext == plaintext bytes) and SES sends are captured to a
 * local file instead of actually dispatched (quickstart.md's "local mail-catcher"), so a
 * developer/CI can exercise the full login/password-reset flow — including retrieving the emailed
 * reset link — without real AWS credentials or a LocalStack container. MUST NOT be active in any
 * deployed environment — see AwsClientsConfig's {@code !e2e-seed} guard.
 */
@Configuration
@Profile("e2e-seed")
public class E2eStubAwsConfig {

  @Bean
  public KmsClient kmsClient() {
    return (KmsClient)
        java.lang.reflect.Proxy.newProxyInstance(
            KmsClient.class.getClassLoader(),
            new Class<?>[] {KmsClient.class},
            (proxy, method, args) -> {
              Object objectMethodResult = handleObjectMethod(proxy, method, args);
              if (objectMethodResult != NOT_AN_OBJECT_METHOD) {
                return objectMethodResult;
              }
              if ("encrypt".equals(method.getName()) && args[0] instanceof EncryptRequest request) {
                return EncryptResponse.builder().ciphertextBlob(request.plaintext()).build();
              }
              if ("decrypt".equals(method.getName()) && args[0] instanceof DecryptRequest request) {
                return DecryptResponse.builder().plaintext(request.ciphertextBlob()).build();
              }
              if ("serviceName".equals(method.getName())) {
                return "kms";
              }
              if ("close".equals(method.getName())) {
                return null;
              }
              throw new UnsupportedOperationException(
                  "E2eStubAwsConfig KmsClient stub does not implement " + method.getName());
            });
  }

  @Bean
  public SesClient sesClient(
      @Value("${app.e2e-seed.captured-emails-path}") String capturedEmailsPath) {
    return (SesClient)
        java.lang.reflect.Proxy.newProxyInstance(
            SesClient.class.getClassLoader(),
            new Class<?>[] {SesClient.class},
            (proxy, method, args) -> {
              Object objectMethodResult = handleObjectMethod(proxy, method, args);
              if (objectMethodResult != NOT_AN_OBJECT_METHOD) {
                return objectMethodResult;
              }
              if ("sendEmail".equals(method.getName())
                  && args[0] instanceof SendEmailRequest request) {
                captureEmail(capturedEmailsPath, request);
                return SendEmailResponse.builder().messageId("e2e-seed-stub").build();
              }
              if ("serviceName".equals(method.getName())) {
                return "ses";
              }
              if ("close".equals(method.getName())) {
                return null;
              }
              throw new UnsupportedOperationException(
                  "E2eStubAwsConfig SesClient stub does not implement " + method.getName());
            });
  }

  /**
   * Spring's bean lifecycle (e.g. destruction-callback bookkeeping via a {@code ConcurrentHashMap})
   * calls {@code hashCode}/{@code equals}/{@code toString} on every singleton bean, including these
   * proxies — {@link #NOT_AN_OBJECT_METHOD} lets callers distinguish "really returned null" from
   * "not one of these methods".
   */
  private static final Object NOT_AN_OBJECT_METHOD = new Object();

  private static Object handleObjectMethod(
      Object proxy, java.lang.reflect.Method method, Object[] args) {
    return switch (method.getName()) {
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
      case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "E2eStub";
      default -> NOT_AN_OBJECT_METHOD;
    };
  }

  /** Appends one JSON-line per sent email, for the Playwright suite to read (e2e/support). */
  private static void captureEmail(String capturedEmailsPath, SendEmailRequest request) {
    String line =
        "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}%n"
            .formatted(
                String.join(",", request.destination().toAddresses()),
                jsonEscape(request.message().subject().data()),
                jsonEscape(request.message().body().text().data()));
    try {
      Path path = Path.of(capturedEmailsPath);
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.writeString(
          path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to capture stubbed SES email for e2e tests", e);
    }
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
