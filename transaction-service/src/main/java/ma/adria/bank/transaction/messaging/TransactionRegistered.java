package ma.adria.bank.transaction.messaging;

import ma.adria.bank.transaction.domain.Transaction;

/** Évènement interne émis par la couche service ; il n'est transmis à Kafka qu'après le commit. */
public record TransactionRegistered(Transaction transaction) {
}
