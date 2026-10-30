package ma.adria.bank.notification;

import ma.adria.bank.notification.model.TransactionCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Journalise chaque évènement reçu. Ce service n'a aucun accès aux données bancaires
 * (ni base de données, ni appel vers les autres services) : il ne peut rien modifier.
 */
@Component
public class TransactionEventListener {

    public static final String TOPIC = "bank.transactions.created";

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    @KafkaListener(topics = TOPIC)
    public void onTransactionCreated(ConsumerRecord<String, TransactionCreatedEvent> record) {
        TransactionCreatedEvent event = record.value();
        if (event == null) {
            log.warn("Message sans contenu ignoré (partition {}, offset {})", record.partition(), record.offset());
            return;
        }
        log.info("Évènement reçu : eventId={} eventType={} transactionId={} accountId={} amount={} currency={} "
                        + "occurredAt={} [clé={} partition={} offset={}]",
                event.eventId(), event.eventType(), event.transactionId(), event.accountId(),
                event.amount(), event.currency(), event.occurredAt(),
                record.key(), record.partition(), record.offset());
    }
}
