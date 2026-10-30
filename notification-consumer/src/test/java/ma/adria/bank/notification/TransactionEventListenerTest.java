package ma.adria.bank.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = TransactionEventListener.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class TransactionEventListenerTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.StringSerializer()));
    }

    private static String eventJson(UUID eventId, UUID accountId) {
        return """
                {"eventId":"%s","eventType":"TransactionCreated","transactionId":"%s","accountId":"%s",
                 "amount":250.00,"currency":"EUR","occurredAt":"2026-01-15T10:30:00Z"}
                """.formatted(eventId, UUID.randomUUID(), accountId);
    }

    @Test
    void logsTheReceivedEventWithItsKeyAndPartition(CapturedOutput output) {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        producer().send(TransactionEventListener.TOPIC, accountId.toString(), eventJson(eventId, accountId));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(output.getAll())
                        .contains("Évènement reçu")
                        .contains("eventId=" + eventId)
                        .contains("eventType=TransactionCreated")
                        .contains("accountId=" + accountId)
                        .contains("amount=250.00")
                        .contains("currency=EUR")
                        .contains("occurredAt=2026-01-15T10:30:00Z")
                        .contains("clé=" + accountId));
    }

    @Test
    void ignoresUnknownFieldsAndSurvivesMalformedMessages(CapturedOutput output) {
        UUID accountId = UUID.randomUUID();
        KafkaTemplate<String, String> producer = producer();

        producer.send(TransactionEventListener.TOPIC, accountId.toString(), "ceci n'est pas du JSON");
        UUID eventId = UUID.randomUUID();
        producer.send(TransactionEventListener.TOPIC, accountId.toString(),
                eventJson(eventId, accountId).replace("\"currency\"", "\"champInconnu\":1,\"currency\""));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(output.getAll()).contains("eventId=" + eventId ));
    }
}
