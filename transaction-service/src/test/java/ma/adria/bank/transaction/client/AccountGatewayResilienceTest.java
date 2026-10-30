package ma.adria.bank.transaction.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import ma.adria.bank.transaction.domain.AccountDto;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.AccountStatus;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste le vrai client OpenFeign et Resilience4j contre un faux account-service HTTP
 * dont on pilote le code de réponse.
 */
@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.instances.account-service.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.account-service.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.account-service.wait-duration-in-open-state=60s",
        "resilience4j.retry.instances.account-service.max-attempts=3",
        "resilience4j.retry.instances.account-service.wait-duration=10ms"
})
class AccountGatewayResilienceTest {

    private static final HttpServer SERVER;
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile int responseStatus = 200;
    private static volatile String responseBody = "";

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVER.createContext("/", exchange -> {
            HITS.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            REQUESTS.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        SERVER.start();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("bank.clients.account-service.url", () -> "http://localhost:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Autowired
    AccountGateway gateway;

    @Autowired
    CircuitBreakerRegistry registry;

    private final UUID accountId = UUID.randomUUID();

    private CircuitBreaker circuitBreaker() {
        return registry.circuitBreaker("account-service");
    }

    private String accountJson(double balance) {
        return """
                {"id":"%s","customerReference":"C","status":"ACTIVE","currency":"EUR","balance":%s}
                """.formatted(accountId, balance);
    }

    private static void respond(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    @BeforeEach
    void reset() {
        HITS.set(0);
        REQUESTS.clear();
        respond(200, "");
        circuitBreaker().reset();
    }

    @Test
    void getAccountDeserializesRemoteAccount() {
        respond(200, accountJson(321.5));

        AccountDto account = gateway.getAccount(accountId);

        assertThat(account.id()).isEqualTo(accountId);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(321.5);
        assertThat(REQUESTS).containsExactly("GET /api/accounts/" + accountId + " ");
    }

    @Test
    void applyBalanceDeltaSendsPatchWithDelta() {
        respond(200, accountJson(100));

        gateway.applyBalanceDelta(accountId, -25.5);

        assertThat(REQUESTS).hasSize(1);
        assertThat(REQUESTS.get(0)).startsWith("PATCH /api/accounts/" + accountId + "/balance ")
                .contains("\"delta\":-25.5");
    }

    @Test
    void notFoundBecomesBusinessExceptionWithoutRetryNorCircuitFailure() {
        respond(404, "{\"detail\":\"Compte introuvable : x\"}");

        assertThatThrownBy(() -> gateway.getAccount(accountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Compte introuvable : x");

        assertThat(HITS.get()).isEqualTo(1);
        assertThat(circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void conflictBecomesBusinessRuleException() {
        respond(409, "{\"detail\":\"Solde insuffisant sur le compte x\"}");

        assertThatThrownBy(() -> gateway.applyBalanceDelta(accountId, -999))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Solde insuffisant sur le compte x");
        assertThat(HITS.get()).isEqualTo(1);
    }

    @Test
    void serverErrorOnReadIsRetriedThenFallsBackTo503Exception() {
        respond(500, "{\"detail\":\"boom\"}");

        assertThatThrownBy(() -> gateway.getAccount(accountId))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("indisponible");

        assertThat(HITS.get()).isEqualTo(3);
    }

    @Test
    void serverErrorOnBalanceUpdateIsNeverRetried() {
        respond(500, "{\"detail\":\"boom\"}");

        assertThatThrownBy(() -> gateway.applyBalanceDelta(accountId, -10))
                .isInstanceOf(AccountServiceUnavailableException.class);

        assertThat(HITS.get()).isEqualTo(1);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndFailsFast() {
        respond(500, "{\"detail\":\"boom\"}");

        // 1er appel : 3 tentatives ; 2e appel : la 4e défaillance ouvre le circuit
        assertThatThrownBy(() -> gateway.getAccount(accountId)).isInstanceOf(AccountServiceUnavailableException.class);
        assertThatThrownBy(() -> gateway.getAccount(accountId)).isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int hitsWhenOpened = HITS.get();
        assertThat(hitsWhenOpened).isEqualTo(4);

        // circuit ouvert : plus aucun appel réseau, et le retry n'insiste pas (un seul refus par appel)
        long refusedBefore = circuitBreaker().getMetrics().getNumberOfNotPermittedCalls();
        assertThatThrownBy(() -> gateway.getAccount(accountId)).isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(circuitBreaker().getMetrics().getNumberOfNotPermittedCalls() - refusedBefore).isEqualTo(1);
        assertThatThrownBy(() -> gateway.applyBalanceDelta(accountId, -1))
                .isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(HITS.get()).isEqualTo(hitsWhenOpened);
    }
}
