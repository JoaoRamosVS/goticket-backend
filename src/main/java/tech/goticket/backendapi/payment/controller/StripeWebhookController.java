package tech.goticket.backendapi.payment.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tech.goticket.backendapi.payment.service.StripeWebhookService;

@RestController
@RequiredArgsConstructor
@Hidden
public class StripeWebhookController {
    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookService stripeWebhookService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.webhook.tolerance-seconds:300}")
    private long toleranceSeconds;

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handle(@RequestHeader("Stripe-Signature") String signature,
                                         @RequestBody String rawBody) {
        Event event;
        try {
            event = Webhook.constructEvent(rawBody, signature, webhookSecret, toleranceSeconds);
        } catch (SignatureVerificationException e) {
            log.warn("Assinatura Stripe inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Assinatura inválida.");
        }

        try {
            stripeWebhookService.handle(event, rawBody);
            return ResponseEntity.ok("Ok");
        } catch (Exception e) {
            log.error("Falha ao processar evento {}: {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.status(500).body("Erro ao processar evento.");
        }
    }
}
