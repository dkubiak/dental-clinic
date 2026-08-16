package com.dentalclinic.auth.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * AWS SES integration (research.md #10), shared by PasswordResetService (T043, its originating use
 * case — reset links, FR-016/017) and AccountLockoutService (T041c — lockout notifications,
 * FR-011c), rather than duplicating an SES client per feature that needs to email a staff member.
 */
@Component
public class EmailSender {

  private final SesClient sesClient;
  private final String fromAddress;

  public EmailSender(SesClient sesClient, @Value("${app.ses.from-address}") String fromAddress) {
    this.sesClient = sesClient;
    this.fromAddress = fromAddress;
  }

  public void send(String toAddress, String subject, String bodyText) {
    SendEmailRequest request =
        SendEmailRequest.builder()
            .source(fromAddress)
            .destination(Destination.builder().toAddresses(toAddress).build())
            .message(
                Message.builder()
                    .subject(Content.builder().data(subject).build())
                    .body(Body.builder().text(Content.builder().data(bodyText).build()).build())
                    .build())
            .build();
    sesClient.sendEmail(request);
  }
}
