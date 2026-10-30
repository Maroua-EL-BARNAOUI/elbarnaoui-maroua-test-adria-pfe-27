package ma.adria.bank.transaction.config;

import ma.adria.bank.transaction.messaging.TransactionEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "bank.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    /** Créé au démarrage si le broker est joignable (3 partitions : parallélisme par compte). */
    @Bean
    NewTopic transactionsCreatedTopic() {
        return TopicBuilder.name(TransactionEventPublisher.TOPIC).partitions(3).replicas(1).build();
    }
}
