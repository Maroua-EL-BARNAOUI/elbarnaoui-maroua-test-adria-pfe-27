package ma.adria.bank.transaction.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publie TransactionCreated après la persistance réussie. La clé du message est l'accountId :
 * les évènements d'un même compte restent ordonnés (même partition).
 * <p>
 * Limite connue : la publication a lieu après le commit, sans outbox ; si Kafka est
 * indisponible à cet instant, la transaction reste valide mais l'évènement est perdu (journalisé).
 */
@Component
@ConditionalOnProperty(name = "bank.events.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventPublisher {

    public static final String TOPIC = "bank.transactions.created";

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * L'évènement est sérialisé ici avec l'ObjectMapper de Spring Boot (dates ISO-8601, comme
     * « 2026-01-15T10:30:00Z ») ; le sérialiseur JSON de spring-kafka écrirait les Instant en nombres.
     */
    public TransactionEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRegistered(TransactionRegistered registered) {
        TransactionCreatedEvent event = TransactionCreatedEvent.from(registered.transaction());
        String key = event.accountId().toString();
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, key, payload).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Évènement {} non publié sur {} (transaction {})",
                            event.eventId(), TOPIC, event.transactionId(), failure);
                } else {
                    log.info("Évènement {} publié sur {} partition {} offset {} (clé {})",
                            event.eventId(), TOPIC, result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(), key);
                }
            });
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Évènement {} non publié sur {} (transaction {})",
                    event.eventId(), TOPIC, event.transactionId(), e);
        }
    }
}
