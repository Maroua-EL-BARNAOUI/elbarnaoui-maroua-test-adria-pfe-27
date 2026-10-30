package ma.adria.bank.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Deux faux services en aval : chacun répond « <nom> <méthode> <chemin> » pour identifier la route prise. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final HttpServer ACCOUNT = stub("account");
    private static final HttpServer TRANSACTION = stub("transaction");
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();

    private static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                BODIES.add(name + ":" + body);
                byte[] payload = (name + " " + exchange.getRequestMethod() + " "
                        + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.account-service-uri", () -> "http://localhost:" + ACCOUNT.getAddress().getPort());
        registry.add("gateway.routes.transaction-service-uri",
                () -> "http://localhost:" + TRANSACTION.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ACCOUNT.stop(0);
        TRANSACTION.stop(0);
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void clear() {
        BODIES.clear();
    }

    private void assertRoutedTo(String method, String path, String expectedBody) {
        client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(expectedBody);
    }

    @Test
    void accountRestIsRoutedToAccountService() {
        assertRoutedTo("GET", "/api/accounts/42", "account GET /api/accounts/42");
    }

    @Test
    void transactionAndTransferRestAreRoutedToTransactionService() {
        assertRoutedTo("GET", "/api/transactions/7", "transaction GET /api/transactions/7");
        assertRoutedTo("POST", "/api/transfers", "transaction POST /api/transfers");
    }

    @Test
    void graphqlIsRoutedByPrefixAndPrefixIsStripped() {
        assertRoutedTo("POST", "/graphql/accounts", "account POST /graphql");
        assertRoutedTo("POST", "/graphql/transactions", "transaction POST /graphql");
    }

    @Test
    void requestBodyIsForwarded() {
        client.post().uri("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":10}")
                .exchange().expectStatus().isOk();

        assertThat(BODIES).containsExactly("transaction:{\"amount\":10}");
    }

    @Test
    void unknownPathReturns404() {
        client.get().uri("/api/unknown").exchange().expectStatus().isNotFound();
        client.get().uri("/graphql").exchange().expectStatus().isNotFound();
    }
}
