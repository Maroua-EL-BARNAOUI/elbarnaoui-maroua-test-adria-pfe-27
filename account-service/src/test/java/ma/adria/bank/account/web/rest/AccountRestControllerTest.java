package ma.adria.bank.account.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String createAccount(String customer, String currency, double balance) throws Exception {
        String body = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"%s","currency":"%s","initialBalance":%s}
                                """.formatted(customer, currency, balance)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        return json.get("id").asText();
    }

    @Test
    void createThenGetAccount() throws Exception {
        String id = createAccount("REST-1", "EUR", 250.5);

        mvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("REST-1"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(250.5));
    }

    @Test
    void listCanBeFilteredByCustomer() throws Exception {
        createAccount("REST-FILTER", "EUR", 1);
        createAccount("REST-FILTER", "MAD", 1);

        mvc.perform(get("/api/accounts").param("customerReference", "REST-FILTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mvc.perform(get("/api/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"\",\"currency\":\"EURO\",\"initialBalance\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void balanceDeltaUpdatesBalanceAndRejectsOverdraft() throws Exception {
        String id = createAccount("REST-BAL", "EUR", 100);

        mvc.perform(patch("/api/accounts/{id}/balance", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60));

        mvc.perform(patch("/api/accounts/{id}/balance", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-100}"))
                .andExpect(status().isConflict());
    }

    @Test
    void blockedAccountRejectsBalanceChange() throws Exception {
        String id = createAccount("REST-BLK", "EUR", 100);

        mvc.perform(patch("/api/accounts/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(patch("/api/accounts/{id}/balance", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1}"))
                .andExpect(status().isConflict());
    }
}
