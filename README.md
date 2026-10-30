# Plateforme bancaire micro-services (Test ADRIA ENSET 2027)

Plateforme permettant de consulter des comptes bancaires, d'enregistrer des transactions et de publier des évènements métier, avec un assistant conversationnel (Spring AI), Kafka, Keycloak, un client Angular et un volet de détection de fraude en Python.

## Stack

Java 21, Spring Boot 3.5, Spring Cloud (Gateway, Eureka, Config, OpenFeign, Resilience4j), Spring AI + MCP, GraphQL, Kafka, Keycloak, Angular, Docker / Docker Compose, GitHub Actions, Python (scikit-learn).

## Structure

```
.
├── pom.xml            parent Maven (un module par micro-service)
├── docs/              plan, conception, architecture
├── <services>/        modules Maven ajoutés au fil des parties
└── ...
```

Voir [docs/PLAN.md](docs/PLAN.md) pour l'avancement et [docs/architecture.md](docs/architecture.md) pour la conception.

## Prérequis

JDK 21, Maven 3.9+, Docker, Node.js (Angular), Python 3.10+.

## Build

```bash
mvn clean verify
```

## Services et ports

| Service | Port | Rôle |
|---|---|---|
| config-service | 8888 | Config Server (fournit `bank.transaction.max-amount=1000`) |
| discovery-service | 8761 | Eureka |
| account-service | 8081 | Comptes (REST, GraphQL, MCP) |
| transaction-service | 8082 | Transactions et virements (REST, GraphQL, MCP) |
| gateway-service | 8080 | Point d'entrée unique |

Ordre de démarrage : config-service, discovery-service, puis account-service, transaction-service et gateway-service. Les services métier ont besoin du config-service ; la gateway découvre les services via Eureka (compter jusqu'à 30 s de propagation du registre).

```bash
java -jar config-service/target/config-service-1.0.0-SNAPSHOT.jar
```

### Routes de la gateway

| Chemin | Destination |
|---|---|
| `/api/accounts/**` | account-service |
| `/api/transactions/**`, `/api/transfers/**` | transaction-service |
| `/graphql/accounts` | account-service (`/graphql`) |
| `/graphql/transactions` | transaction-service (`/graphql`) |

Les serveurs MCP (`/sse` sur 8081 et 8082) sont consommés directement par l'assistant, via Eureka.
