package ma.adria.bank.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.adria.bank.transaction.client.AccountGateway;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.service.RegisterTransactionCommand;
import ma.adria.bank.transaction.service.TransactionService;
import ma.adria.bank.transaction.service.TransferCommand;
import ma.adria.bank.transaction.service.TransferResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ma.adria.bank.transaction.TestAccounts.active;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "bank.events.enabled=true")
@EmbeddedKafka(partitions = 3, topics = TransactionEventPublisher.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaEventPublishingTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    TransactionService service;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    AccountGateway gateway;

    private Consumer<String, String> consumer;

    /** Tous les messages déjà lus, par clé : un message d'une autre clé ne doit jamais être perdu. */
    private final Map<String, List<ConsumerRecord<String, String>>> seen = new HashMap<>();

    @BeforeEach
    void subscribe() {
        seen.clear();
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", broker);
        props.put("auto.offset.reset", "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, TransactionEventPublisher.TOPIC);
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    /** Lit le topic jusqu'à obtenir {@code expected} messages de cette clé, ou jusqu'à l'échéance. */
    private List<ConsumerRecord<String, String>> recordsForKey(String key, int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (seen.getOrDefault(key, List.of()).size() < expected && System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                seen.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(record);
            }
        }
        return seen.getOrDefault(key, List.of());
    }

    private static RegisterTransactionCommand command(UUID accountId, String amount) {
        return new RegisterTransactionCommand(accountId, new BigDecimal(amount), "EUR", "GROCERY", "FR");
    }

    @Test
    void registerPublishesEventKeyedByAccountIdWithAgreedFormat() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(gateway.getAccount(accountId)).thenReturn(active(accountId, "EUR", 900));

        Transaction tx = service.register(command(accountId, "250"));

        List<ConsumerRecord<String, String>> records = recordsForKey(accountId.toString(), 1, Duration.ofSeconds(15));
        assertThat(records).hasSize(1);
        ConsumerRecord<String, String> record = records.get(0);
        assertThat(record.topic()).isEqualTo("bank.transactions.created");
        assertThat(record.key()).isEqualTo(accountId.toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();

        JsonNode json = mapper.readTree(record.value());
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "eventType", "transactionId", "accountId", "amount", "currency", "occurredAt");
        assertThat(UUID.fromString(json.get("eventId").asText())).isNotNull();
        assertThat(json.get("eventType").asText()).isEqualTo("TransactionCreated");
        assertThat(json.get("transactionId").asText()).isEqualTo(tx.getId().toString());
        assertThat(json.get("accountId").asText()).isEqualTo(accountId.toString());
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(json.get("currency").asText()).isEqualTo("EUR");
        assertThat(java.time.Instant.parse(json.get("occurredAt").asText())).isEqualTo(tx.getCreatedAt());
    }

    @Test
    void transferPublishesOneEventPerLegEachKeyedByItsAccount() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 500));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 0));

        TransferResult result = service.transfer(new TransferCommand(from, to, new BigDecimal("120"), "EUR", "FR"));

        List<ConsumerRecord<String, String>> debit = recordsForKey(from.toString(), 1, Duration.ofSeconds(15));
        List<ConsumerRecord<String, String>> credit = recordsForKey(to.toString(), 1, Duration.ofSeconds(15));
        assertThat(debit).hasSize(1);
        assertThat(credit).hasSize(1);
        assertThat(debit.get(0).value()).contains(result.debit().getId().toString());
        assertThat(credit.get(0).value()).contains(result.credit().getId().toString());
    }

    @Test
    void eventsOfTheSameAccountShareAPartitionAndStayOrdered() {
        UUID accountId = UUID.randomUUID();
        when(gateway.getAccount(accountId)).thenReturn(active(accountId, "EUR", 900));

        Transaction first = service.register(command(accountId, "10"));
        Transaction second = service.register(command(accountId, "20"));
        Transaction third = service.register(command(accountId, "30"));

        List<ConsumerRecord<String, String>> records = recordsForKey(accountId.toString(), 3, Duration.ofSeconds(15));
        assertThat(records).hasSize(3);
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.get(0).partition());
        assertThat(records.get(0).value()).contains(first.getId().toString());
        assertThat(records.get(1).value()).contains(second.getId().toString());
        assertThat(records.get(2).value()).contains(third.getId().toString());
    }

    @Test
    void nothingIsPublishedWhenTheTransactionIsRolledBack() {
        UUID accountId = UUID.randomUUID();
        when(gateway.getAccount(accountId)).thenReturn(active(accountId, "EUR", 900));
        when(gateway.applyBalanceDelta(accountId, -50.0))
                .thenThrow(new AccountServiceUnavailableException("indisponible", null));

        assertThatThrownBy(() -> service.register(command(accountId, "50")))
                .isInstanceOf(AccountServiceUnavailableException.class);

        assertThat(recordsForKey(accountId.toString(), 1, Duration.ofSeconds(4))).isEmpty();
    }

    @Test
    void nothingIsPublishedWhenABusinessRuleRejectsTheTransaction() {
        UUID accountId = UUID.randomUUID();
        when(gateway.getAccount(accountId)).thenReturn(active(accountId, "EUR", 10));

        assertThatThrownBy(() -> service.register(command(accountId, "50")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(recordsForKey(accountId.toString(), 1, Duration.ofSeconds(4))).isEmpty();
    }
}
