package ma.adria.bank.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrat de l'évènement bank.transactions.created. Copie volontairement indépendante de celle du
 * producteur : seuls le format JSON et le topic couplent les deux services.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedEvent(
        UUID eventId,
        String eventType,
        UUID transactionId,
        UUID accountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
