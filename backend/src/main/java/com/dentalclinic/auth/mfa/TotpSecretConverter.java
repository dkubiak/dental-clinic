package com.dentalclinic.auth.mfa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

/**
 * Transparently encrypts/decrypts {@link MfaEnrollment#getTotpSecret()} through AWS KMS on every
 * read/write (T022a, FR-013; research.md #11). The KMS key itself and the IAM (IRSA) permission
 * granting this pod {@code kms:Encrypt}/{@code kms:Decrypt} are provisioned by Terraform
 * (infra/terraform/modules/kms, T009a) — this class only ever holds the key id.
 *
 * <p>{@code autoApply = false}: applied explicitly via {@code @Convert} on the one field that needs
 * it (MfaEnrollment.totpSecret), not implicitly to every String column.
 */
@Component
@Converter(autoApply = false)
public class TotpSecretConverter implements AttributeConverter<String, byte[]> {

  private final KmsClient kmsClient;
  private final String keyId;

  public TotpSecretConverter(
      KmsClient kmsClient, @Value("${app.kms.mfa-secret-key-id}") String keyId) {
    this.kmsClient = kmsClient;
    this.keyId = keyId;
  }

  @Override
  public byte[] convertToDatabaseColumn(String plaintextSecret) {
    if (plaintextSecret == null) {
      return null;
    }
    var response =
        kmsClient.encrypt(
            EncryptRequest.builder()
                .keyId(keyId)
                .plaintext(SdkBytes.fromByteArray(plaintextSecret.getBytes(StandardCharsets.UTF_8)))
                .build());
    return response.ciphertextBlob().asByteArray();
  }

  @Override
  public String convertToEntityAttribute(byte[] ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    var response =
        kmsClient.decrypt(
            DecryptRequest.builder()
                .keyId(
                    keyId) // pinning the key id also rejects ciphertext encrypted under a different
                // key
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                .build());
    return new String(response.plaintext().asByteArray(), StandardCharsets.UTF_8);
  }
}
