package ma.adria.bank.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServiceTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper mapper;

    @Test
    void servesSharedMaxAmountToAnyApplication() throws Exception {
        for (String application : new String[]{"transaction-service", "account-service"}) {
            JsonNode body = mapper.readTree(rest.getForObject("/" + application + "/default", String.class));

            assertThat(body.get("name").asText()).isEqualTo(application);
            JsonNode source = body.at("/propertySources/0/source");
            assertThat(source.get("bank.transaction.max-amount").asInt()).isEqualTo(1000);
        }
    }

    @Test
    void servesPropertyAsPlainYaml() {
        String yaml = rest.getForObject("/transaction-service-default.yml", String.class);

        assertThat(yaml).contains("max-amount: 1000");
    }
}
