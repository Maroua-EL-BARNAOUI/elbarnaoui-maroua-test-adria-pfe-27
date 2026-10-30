package ma.adria.bank.transaction.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.adria.bank.transaction.client.AccountGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static ma.adria.bank.transaction.TestAccounts.active;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class TransactionMcpToolsTest {

    @Autowired
    List<ToolCallbackProvider> providers;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    AccountGateway gateway;

    private ToolCallback tool(String name) {
        return providers.stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Outil MCP introuvable : " + name));
    }

    @Test
    void exposesTheExpectedTools() {
        List<String> names = providers.stream()
                .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                .map(t -> t.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder(
                "register_transaction", "transfer_funds", "get_transaction", "list_transactions_by_account");
    }

    @Test
    void registerAndReadTransactionThroughTools() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 400));

        JsonNode created = mapper.readTree(tool("register_transaction").call("""
                {"accountId":"%s","amount":75.5,"currency":"EUR","merchantCategory":"TRAVEL","country":"MA"}
                """.formatted(id)));
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("75.5");
        assertThat(created.get("type").asText()).isEqualTo("DEBIT");
        verify(gateway).applyBalanceDelta(id, -75.5);

        JsonNode fetched = mapper.readTree(tool("get_transaction")
                .call("{\"transactionId\":\"" + created.get("id").asText() + "\"}"));
        assertThat(fetched.get("merchantCategory").asText()).isEqualTo("TRAVEL");

        JsonNode list = mapper.readTree(tool("list_transactions_by_account")
                .call("{\"accountId\":\"" + id + "\"}"));
        assertThat(list).hasSize(1);
    }

    @Test
    void transferFundsThroughTool() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 400));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 0));

        JsonNode result = mapper.readTree(tool("transfer_funds").call("""
                {"fromAccountId":"%s","toAccountId":"%s","amount":100,"currency":"EUR"}
                """.formatted(from, to)));

        assertThat(result.at("/debit/type").asText()).isEqualTo("DEBIT");
        assertThat(result.at("/credit/type").asText()).isEqualTo("CREDIT");
    }
}
