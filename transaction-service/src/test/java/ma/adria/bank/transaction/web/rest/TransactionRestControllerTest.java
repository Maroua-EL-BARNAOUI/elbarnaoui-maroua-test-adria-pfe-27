package ma.adria.bank.transaction.web.rest;

import ma.adria.bank.transaction.client.AccountGateway;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static ma.adria.bank.transaction.TestAccounts.active;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionRestControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountGateway gateway;

    private static String body(UUID accountId, String amount, String currency) {
        return """
                {"accountId":"%s","amount":%s,"currency":"%s","merchantCategory":"GROCERY","country":"FR"}
                """.formatted(accountId, amount, currency);
    }

    @Test
    void registerThenGetTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 900));

        String response = mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, "250.00", "EUR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(id.toString()))
                .andExpect(jsonPath("$.amount").value(250.0))
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        String txId = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/transactions/{id}", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));
        mvc.perform(get("/api/transactions").param("accountId", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "-1", "EURO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void amountAboveCeilingReturns422() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, "1000.01", "EUR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("plafond")));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenThrow(new ResourceNotFoundException("Compte introuvable : " + id));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, "10", "EUR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountServiceDownReturns503() throws Exception {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenThrow(new AccountServiceUnavailableException("account-service est indisponible", null));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, "10", "EUR")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownTransactionReturns404() throws Exception {
        mvc.perform(get("/api/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferReturnsDebitAndCredit() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 700));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 0));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":120.5,"currency":"EUR"}
                                """.formatted(from, to)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId").exists())
                .andExpect(jsonPath("$.debit.type").value("DEBIT"))
                .andExpect(jsonPath("$.debit.accountId").value(from.toString()))
                .andExpect(jsonPath("$.credit.type").value("CREDIT"))
                .andExpect(jsonPath("$.credit.accountId").value(to.toString()))
                .andExpect(jsonPath("$.credit.amount").value(120.5));
    }

    @Test
    void transferToSameAccountReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":10,"currency":"EUR"}
                                """.formatted(id, id)))
                .andExpect(status().isBadRequest());
    }
}
