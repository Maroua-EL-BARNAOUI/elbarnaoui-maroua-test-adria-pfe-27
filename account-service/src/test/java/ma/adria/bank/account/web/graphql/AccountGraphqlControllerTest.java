package ma.adria.bank.account.web.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountGraphqlControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private org.springframework.test.web.servlet.ResultActions graphql(String query) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("query", query));
        return mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void createAccountThenQueryById() throws Exception {
        String created = graphql("""
                mutation { createAccount(input: {customerReference: "GQL-1", currency: "eur", initialBalance: 75.5}) {
                    id status currency balance } }
                """)
                .andExpect(jsonPath("$.data.createAccount.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createAccount.currency").value("EUR"))
                .andExpect(jsonPath("$.data.createAccount.balance").value(75.5))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).at("/data/createAccount/id").asText();

        graphql("{ accountById(id: \"" + id + "\") { customerReference balance } }")
                .andExpect(jsonPath("$.data.accountById.customerReference").value("GQL-1"))
                .andExpect(jsonPath("$.data.accountById.balance").value(75.5));
    }

    @Test
    void queryByCustomerReturnsOnlyMatchingAccounts() throws Exception {
        graphql("mutation { createAccount(input: {customerReference: \"GQL-C\", currency: \"EUR\", initialBalance: 1}) { id } }");
        graphql("mutation { createAccount(input: {customerReference: \"GQL-C\", currency: \"MAD\", initialBalance: 2}) { id } }");

        graphql("{ accountsByCustomer(customerReference: \"GQL-C\") { currency } }")
                .andExpect(jsonPath("$.data.accountsByCustomer.length()").value(2));
    }

    @Test
    void unknownAccountReturnsNull() throws Exception {
        graphql("{ accountById(id: \"" + UUID.randomUUID() + "\") { id } }")
                .andExpect(jsonPath("$.data.accountById").doesNotExist());
    }

    @Test
    void changeStatusOfUnknownAccountReturnsNotFoundError() throws Exception {
        graphql("mutation { changeAccountStatus(id: \"" + UUID.randomUUID() + "\", status: BLOCKED) { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("NOT_FOUND"));
    }

    @Test
    void invalidInputReturnsBadRequestError() throws Exception {
        graphql("mutation { createAccount(input: {customerReference: \"X\", currency: \"EURO\", initialBalance: 1}) { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }
}
