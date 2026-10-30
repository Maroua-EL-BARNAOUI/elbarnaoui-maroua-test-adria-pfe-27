package ma.adria.bank.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutesConfig {

    /**
     * Les deux services exposent /graphql : la gateway les distingue par un préfixe
     * (/graphql/accounts, /graphql/transactions) et le retire avant de transmettre.
     */
    @Bean
    RouteLocator bankRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties uris) {
        return builder.routes()
                .route("account-rest", r -> r.path("/api/accounts/**")
                        .uri(uris.accountServiceUri()))
                .route("account-graphql", r -> r.path("/graphql/accounts")
                        .filters(f -> f.setPath("/graphql"))
                        .uri(uris.accountServiceUri()))
                .route("transaction-rest", r -> r.path("/api/transactions/**", "/api/transfers/**")
                        .uri(uris.transactionServiceUri()))
                .route("transaction-graphql", r -> r.path("/graphql/transactions")
                        .filters(f -> f.setPath("/graphql"))
                        .uri(uris.transactionServiceUri()))
                .build();
    }
}
