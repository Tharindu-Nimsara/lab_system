package com.lab.backend.report;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends finalized lab reports by email as a PDF attachment (plan §5.4).
 * Delivery is off unless {@code app.mail.enabled=true} and a mail sender is
 * configured; the caller is responsible for the consent gate.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final LabInfo lab;

    @Value("${app.mail.enabled}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    /** True when email delivery is actually usable in this environment. */
    public boolean isEnabled() {
        return enabled && mailSender.getIfAvailable() != null;
    }

    public void sendReport(String toEmail, String patientName, String invoiceNo, byte[] pdf) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email delivery is disabled (set app.mail.enabled=true to enable)");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mail sender is not configured");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(lab.getName() + " — Lab report " + invoiceNo);
            helper.setText("""
                    Dear %s,

                    Please find your laboratory report attached (invoice %s).
                    This report is confidential and intended only for you.

                    Regards,
                    %s
                    %s · %s
                    """.formatted(patientName, invoiceNo, lab.getName(),
                    lab.getAddress(), lab.getPhone()));
            helper.addAttachment("report-" + invoiceNo + ".pdf",
                    new ByteArrayResource(pdf), "application/pdf");
            sender.send(message);
            log.info("Emailed report {} to {}", invoiceNo, toEmail);
        } catch (Exception e) {
            log.error("Failed to email report {} to {}", invoiceNo, toEmail, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to send email: " + e.getMessage());
        }
    }
}
