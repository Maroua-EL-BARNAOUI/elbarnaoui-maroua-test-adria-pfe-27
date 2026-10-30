package ma.adria.bank.account.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountMcpToolsTest {

    @Autowired
    List<ToolCallbackProvider> providers;

    @Autowired
    ObjectMapper mapper;

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
                "list_accounts", "get_account", "get_accounts_by_customer",
                "create_account", "change_account_status");
    }

    @Test
    void createGetAndBlockAccountThroughTools() throws Exception {
        JsonNode created = mapper.readTree(tool("create_account").call(
                "{\"customerReference\":\"MCP-1\",\"currency\":\"EUR\",\"initialBalance\":300}"));
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode fetched = mapper.readTree(tool("get_account").call("{\"accountId\":\"" + id + "\"}"));
        assertThat(fetched.get("balance").asDouble()).isEqualTo(300);

        JsonNode blocked = mapper.readTree(tool("change_account_status")
                .call("{\"accountId\":\"" + id + "\",\"status\":\"BLOCKED\"}"));
        assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");

        JsonNode byCustomer = mapper.readTree(tool("get_accounts_by_customer")
                .call("{\"customerReference\":\"MCP-1\"}"));
        assertThat(byCustomer.isArray()).isTrue();
        assertThat(byCustomer).hasSize(1);
    }
}
