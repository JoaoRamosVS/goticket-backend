package tech.goticket.backendapi.idempotency.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tech.goticket.backendapi.idempotency.IdempotencyKey;
import tech.goticket.backendapi.idempotency.IdempotencyKeyRepository;
import tech.goticket.backendapi.shared.exception.ConflictException;
import tech.goticket.backendapi.shared.exception.InvalidArgumentException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;

    @Value("${goticket.checkout.idempotency.ttl-hours:24}")
    private long ttlHours;

    public LookupResult checkAndRegister(String key, UUID userId, String endpoint, String bodyJson) {
        if(key == null || key.isBlank()) {
            throw new InvalidArgumentException("Header Idempotency-Key obrigatório.");
        }
        String hash = sha256(bodyJson);

        Optional<IdempotencyKey> existingKey = repository.findByKey(key);
        if(existingKey.isPresent()) {
            return replayOrInFlight(existingKey.get(), userId, hash);
        }

        IdempotencyKey newKey = new IdempotencyKey();
        newKey.setKey(key);
        newKey.setUserId(userId);
        newKey.setEndpoint(endpoint);
        newKey.setRequestHash(hash);
        newKey.setCreatedAt(Instant.now());
        newKey.setExpiresAt(Instant.now().plus(Duration.ofHours(ttlHours)));

        try {
            repository.saveAndFlush(newKey);
            return LookupResult.fresh();
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotency registration race for key={}, recovering winner.", key);
            IdempotencyKey winner = repository.findByKey(key)
                    .orElseThrow(() -> new ConflictException(
                            "Conflito de Idempotency-Key, mas registro não encontrado na recovery."));
            return replayOrInFlight(winner, userId, hash);
        }
    }

    private LookupResult replayOrInFlight(IdempotencyKey ik, UUID userId, String hash) {
        if(!ik.getUserId().equals(userId)) {
            throw new ConflictException("Idempotency-Key já foi utilizada por outro usuário.");
        }
        if(!ik.getRequestHash().equals(hash)) {
            throw new ConflictException(
                    "Idempotency-Key reutilizada com body diferente. " +
                            "Gere uma nova chave para uma compra distinta.");
        }
        if(ik.getOrderId() == null) {
            throw new ConflictException(
                    "Já existe uma requisição em processamento para esta Idempotency-Key. " +
                            "Tente novamente em instantes.");
        }
        log.info("Idempotency hit: key={}, orderId={}", ik.getKey(), ik.getOrderId());
        return LookupResult.replay(ik.getOrderId());
    }

    @Transactional
    public void linkToOrder(String key, Long orderId, int responseStatus) {
        repository.findById(key).ifPresent(ik -> {
            ik.setOrderId(orderId);
            ik.setResponseStatus(responseStatus);
            repository.save(ik);
        });
    }

    private static String sha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    public sealed interface LookupResult permits Fresh, Replay {
        static LookupResult fresh() { return new Fresh(); }
        static LookupResult replay(Long orderId) { return new Replay(orderId); }
        boolean isReplay();
    }
    public record Fresh() implements LookupResult {
        public boolean isReplay() { return false; }
    }
    public record Replay(Long orderId) implements LookupResult {
        public boolean isReplay() { return true; }
    }
}
