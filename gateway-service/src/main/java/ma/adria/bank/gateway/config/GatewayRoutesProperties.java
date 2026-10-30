package ma.adria.bank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Destinations des routes : par défaut le nom logique Eureka (lb://), surchargeable. */
@ConfigurationProperties(prefix = "gateway.routes")
public record GatewayRoutesProperties(
        @DefaultValue("lb://account-service") String accountServiceUri,
        @DefaultValue("lb://transaction-service") String transactionServiceUri) {
}
