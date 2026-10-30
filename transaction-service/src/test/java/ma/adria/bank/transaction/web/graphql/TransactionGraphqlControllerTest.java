package ma.adria.bank.transaction.web.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.adria.bank.transaction.client.AccountGateway;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static ma.adria.bank.transaction.TestAccounts.active;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionGraphqlControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    AccountGateway gateway;

    private ResultActions graphql(String query) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("query", query));
        return mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private String register(UUID accountId, String amount) {
        return """
                mutation { registerTransaction(input: {accountId: "%s", amount: %s, currency: "EUR",
                    merchantCategory: "GROCERY", country: "FR"}) { id accountId amount currency type createdAt } }
                """.formatted(accountId, amount);
    }

    @Test
    void registerThenQueryTransactions() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 900));

        String response = graphql(register(id, "99.9"))
                .andExpect(jsonPath("$.data.registerTransaction.accountId").value(id.toString()))
                .andExpect(jsonPath("$.data.registerTransaction.amount").value(99.9))
                .andExpect(jsonPath("$.data.registerTransaction.type").value("DEBIT"))
                .andReturn().getResponse().getContentAsString();
        String txId = mapper.readTree(response).at("/data/registerTransaction/id").asText();

        graphql("{ transactionById(id: \"" + txId + "\") { currency } }")
                .andExpect(jsonPath("$.data.transactionById.currency").value("EUR"));
        graphql("{ transactionsByAccount(accountId: \"" + id + "\") { id } }")
                .andExpect(jsonPath("$.data.transactionsByAccount.length()").value(1));
    }

    @Test
    void transferReturnsBothLegs() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 500));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 5));

        graphql("""
                mutation { transfer(input: {fromAccountId: "%s", toAccountId: "%s", amount: 40, currency: "EUR"}) {
                    transferId debit { type accountId } credit { type accountId } } }
                """.formatted(from, to))
                .andExpect(jsonPath("$.data.transfer.debit.type").value("DEBIT"))
                .andExpect(jsonPath("$.data.transfer.credit.accountId").value(to.toString()));
    }

    @Test
    void unknownTransactionReturnsNull() throws Exception {
        graphql("{ transactionById(id: \"" + UUID.randomUUID() + "\") { id } }")
                .andExpect(jsonPath("$.data.transactionById").doesNotExist());
    }

    @Test
    void businessRuleViolationReturnsBadRequestError() throws Exception {
        graphql(register(UUID.randomUUID(), "5000"))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }

    @Test
    void unknownAccountReturnsNotFoundError() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenThrow(new ResourceNotFoundException("Compte introuvable"));

        graphql(register(id, "10"))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("NOT_FOUND"));
    }
}
