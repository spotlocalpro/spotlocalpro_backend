package com.servicepoint.core.service;

import sendinblue.*;
import sendinblue.auth.*;
import sibApi.TransactionalEmailsApi;
import sibModel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.List;

@Service
public class BrevoEmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public boolean sendEmail(String to, String subject, String htmlContent) {
        return sendEmail(to, subject, htmlContent, null, null);
    }

    public boolean sendEmail(String to, String subject, String htmlContent,
                             List<byte[]> attachmentContents, List<String> attachmentNames) {
        try {
            ApiClient defaultClient = Configuration.getDefaultApiClient();
            ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
            apiKeyAuth.setApiKey(apiKey);

            TransactionalEmailsApi api = new TransactionalEmailsApi();

            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setEmail(fromEmail);
            sender.setName("SpotLocalPro");

            List<SendSmtpEmailTo> toList = new ArrayList<>();
            SendSmtpEmailTo recipient = new SendSmtpEmailTo();
            recipient.setEmail(to);
            toList.add(recipient);

            SendSmtpEmail email = new SendSmtpEmail();
            email.setSender(sender);
            email.setTo(toList);
            email.setSubject(subject);
            email.setHtmlContent(htmlContent);

            if (attachmentContents != null && !attachmentContents.isEmpty()) {
                List<SendSmtpEmailAttachment> attachments = new ArrayList<>();
                for (int i = 0; i < attachmentContents.size(); i++) {
                    SendSmtpEmailAttachment att = new SendSmtpEmailAttachment();
                    att.setContent(attachmentContents.get(i));
                    att.setName(i < attachmentNames.size() ? attachmentNames.get(i) : "photo-" + (i + 1) + ".jpg");
                    attachments.add(att);
                }
                email.setAttachment(attachments);
            }

            CreateSmtpEmail result = api.sendTransacEmail(email);
            System.out.println("✅ Brevo email sent: " + result.getMessageId());
            return true;

        } catch (Exception e) {
            System.err.println("❌ Brevo failed: " + e.getMessage());
            return false;
        }
    }
}