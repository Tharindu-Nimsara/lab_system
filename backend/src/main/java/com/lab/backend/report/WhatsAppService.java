package com.lab.backend.report;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Sends finalized lab reports over the WhatsApp Business Cloud API (Meta) as a
 * PDF document message (plan §5.4). Two-step Graph API flow: upload the media,
 * then send a document message referencing the returned media id.
 *
 * <p>Off unless {@code app.whatsapp.enabled=true} with a phone-number id and
 * access token configured (see MANUAL_TASKS.md). The caller owns the consent gate.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final LabInfo lab;

    @Value("${app.whatsapp.enabled}")
    private boolean enabled;

    @Value("${app.whatsapp.api-base}")
    private String apiBase;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.default-country-code}")
    private String defaultCountryCode;

    /** True when WhatsApp delivery is usable in this environment. */
    public boolean isEnabled() {
        return enabled && !phoneNumberId.isBlank() && !accessToken.isBlank();
    }

    public void sendReport(String toPhone, String patientName, String invoiceNo, byte[] pdf) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "WhatsApp delivery is disabled (set app.whatsapp.enabled=true and configure "
                            + "the Meta API credentials)");
        }
        String recipient = normalize(toPhone);
        RestClient client = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        try {
            String mediaId = uploadMedia(client, invoiceNo, pdf);
            sendDocument(client, recipient, mediaId, invoiceNo, patientName);
            log.info("Sent WhatsApp report {} to {}", invoiceNo, recipient);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp report {} to {}", invoiceNo, recipient, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to send WhatsApp message: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String uploadMedia(RestClient client, String invoiceNo, byte[] pdf) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("messaging_product", "whatsapp");
        form.add("type", "application/pdf");
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "report-" + invoiceNo + ".pdf";
            }
        });
        Map<String, Object> body = client.post()
                .uri("/{id}/media", phoneNumberId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object id = body == null ? null : body.get("id");
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "WhatsApp media upload returned no id");
        }
        return id.toString();
    }

    private void sendDocument(RestClient client, String recipient, String mediaId,
                              String invoiceNo, String patientName) {
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipient,
                "type", "document",
                "document", Map.of(
                        "id", mediaId,
                        "filename", "report-" + invoiceNo + ".pdf",
                        "caption", lab.getName() + " — lab report " + invoiceNo
                                + " for " + patientName));
        client.post()
                .uri("/{id}/messages", phoneNumberId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Normalize a local number to WhatsApp's country-code form: strip spaces and
     * any leading '+'/'0', and prefix the default country code when it looks local.
     */
    String normalize(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = defaultCountryCode + digits.substring(1);
        } else if (digits.length() <= 10 && !digits.startsWith(defaultCountryCode)) {
            digits = defaultCountryCode + digits;
        }
        return digits;
    }
}
