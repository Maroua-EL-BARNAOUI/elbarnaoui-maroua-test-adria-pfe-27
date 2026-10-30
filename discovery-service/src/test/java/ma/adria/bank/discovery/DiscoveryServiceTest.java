package ma.adria.bank.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void eurekaDashboardIsServed() {
        assertThat(rest.getForEntity("/", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void instanceCanRegisterAndBeListed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        String instance = """
                {"instance":{"instanceId":"demo:1","hostName":"localhost","app":"DEMO-SERVICE",
                "ipAddr":"127.0.0.1","status":"UP","port":{"$":9999,"@enabled":"true"},
                "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
                """;

        ResponseEntity<String> registered = rest.exchange("/eureka/apps/DEMO-SERVICE", HttpMethod.POST,
                new HttpEntity<>(instance, headers), String.class);
        assertThat(registered.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> apps = rest.exchange("/eureka/apps", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(apps.getBody()).contains("DEMO-SERVICE");
    }
}
