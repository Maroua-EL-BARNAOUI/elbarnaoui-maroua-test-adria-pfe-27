package ma.adria.bank.transaction.messaging;

import ma.adria.bank.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Évènement métier publié dans bank.transactions.created (format du cahier des charges). */
public record TransactionCreatedEvent(
        UUID eventId,
        String eventType,
        UUID transactionId,
        UUID accountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {

    public static final String TYPE = "TransactionCreated";

    public static TransactionCreatedEvent from(Transaction transaction) {
        return new TransactionCreatedEvent(
                UUID.randomUUID(),
                TYPE,
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCreatedAt());
    }
}
