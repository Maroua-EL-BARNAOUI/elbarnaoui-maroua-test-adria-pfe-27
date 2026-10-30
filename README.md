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
