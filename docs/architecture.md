# Conception et architecture (Partie B)

Les diagrammes sont écrits en [Mermaid](https://mermaid.js.org/) : GitHub les affiche directement dans ce fichier.

## 1. Schéma d'architecture

```mermaid
flowchart LR
    subgraph Clients
        WEB[Client Angular]
        TG[Telegram]
        DC[Discord]
        REST[Client REST]
    end

    KC[Keycloak<br/>OAuth2 / OIDC]
    GW[Spring Cloud Gateway<br/>:8080]
    EUR[Eureka Discovery<br/>:8761]
    CFG[Config Service<br/>:8888<br/>bank.transaction.max-amount=1000]

    subgraph Metier[Micro-services métier]
        ACC[account-service<br/>:8081<br/>REST / GraphQL / MCP]
        TRX[transaction-service<br/>:8082<br/>REST / GraphQL / MCP]
    end

    AST[assistant-service<br/>Spring AI + LLM<br/>:8083]
    CON[notification-consumer<br/>:8084]
    KFK[(Kafka<br/>bank.transactions.created)]
    LLM[[LLM<br/>Ollama / API]]

    WEB --> GW
    REST --> GW
    TG --> AST
    DC --> AST
    GW --> ACC
    GW --> TRX
    GW --> AST
    KC -. JWT .-> GW
    KC -. JWT .-> ACC
    KC -. JWT .-> TRX

    TRX -- "OpenFeign + Resilience4j" --> ACC
    TRX -- "publie (clé = accountId)" --> KFK
    KFK --> CON
    AST -- "client MCP" --> ACC
    AST -- "client MCP" --> TRX
    AST --> LLM

    ACC -.-> EUR
    TRX -.-> EUR
    GW -.-> EUR
    AST -.-> EUR
    ACC -.-> CFG
    TRX -.-> CFG
```

Composants métier : `account-service` et `transaction-service`.
Composants techniques : `config-service`, `discovery-service`, `gateway-service`, `assistant-service`, `notification-consumer`.

## 2. Diagramme de classes

```mermaid
classDiagram
    direction LR

    class Account {
        UUID id
        String customerReference
        AccountStatus status
        String currency
        double balance
    }
    class AccountStatus {
        <<enumeration>>
        ACTIVE
        BLOCKED
    }
    class Transaction {
        UUID id
        UUID accountId
        BigDecimal amount
        String currency
        String merchantCategory
        String country
        Instant createdAt
    }
    class TransactionCreatedEvent {
        UUID eventId
        String eventType
        UUID transactionId
        UUID accountId
        BigDecimal amount
        String currency
        Instant occurredAt
    }

    class AccountRepository {
        <<interface>>
    }
    class TransactionRepository {
        <<interface>>
    }
    class AccountService {
        create(...) Account
        findById(UUID) Account
        findAll() List~Account~
        applyBalanceDelta(UUID, double) Account
    }
    class TransactionService {
        register(...) Transaction
        transfer(from, to, amount) List~Transaction~
        findByAccount(UUID) List~Transaction~
    }
    class AccountClient {
        <<FeignClient + Resilience4j>>
        getAccount(UUID) AccountDto
        applyBalanceDelta(UUID, double) AccountDto
    }
    class TransactionEventPublisher {
        publish(Transaction)
    }

    class AccountRestController
    class AccountGraphqlController
    class AccountMcpTools
    class TransactionRestController
    class TransactionGraphqlController
    class TransactionMcpTools

    Account --> AccountStatus
    AccountRepository ..> Account
    TransactionRepository ..> Transaction
    Transaction "0..*" --> "1" Account : accountId

    AccountRestController --> AccountService
    AccountGraphqlController --> AccountService
    AccountMcpTools --> AccountService
    AccountService --> AccountRepository

    TransactionRestController --> TransactionService
    TransactionGraphqlController --> TransactionService
    TransactionMcpTools --> TransactionService
    TransactionService --> TransactionRepository
    TransactionService --> AccountClient
    TransactionService --> TransactionEventPublisher
    TransactionEventPublisher ..> TransactionCreatedEvent
```

Règle de séparation des couches : les contrôleurs REST, GraphQL et MCP n'appellent que la couche `service`.

## 3. Organisation en couches de chaque micro-service

```
src/main/java/ma/adria/bank/<service>/
├── domain/          entités et types métier
├── repository/      accès aux données (Spring Data)
├── service/         logique métier (unique point d'entrée des connecteurs)
├── web/rest/        contrôleurs REST
├── web/graphql/     contrôleurs GraphQL
└── mcp/             outils MCP exposés à l'assistant
```

## 4. Enregistrement d'une transaction

```mermaid
sequenceDiagram
    actor U as Utilisateur / Assistant
    participant T as transaction-service
    participant A as account-service
    participant K as Kafka

    U->>T: créer transaction (accountId, amount, currency, ...)
    T->>T: montant > 0 et <= bank.transaction.max-amount
    T->>A: GET compte (OpenFeign + Resilience4j)
    A-->>T: compte (statut, devise, solde)
    T->>T: compte ACTIVE et devise identique
    T->>T: enregistrer la transaction
    T->>A: mise à jour du solde
    T->>K: publier TransactionCreated (clé = accountId)
    T-->>U: transaction créée
```

Un virement est un enchaînement débit du compte source / crédit du compte destination, réalisé par `transaction-service` avec les mêmes contrôles, chaque jambe produisant une transaction et un évènement.

## 5. Choix techniques

| Sujet | Choix | Justification |
|---|---|---|
| Socle | Java 21, Spring Boot 3.5, Spring Cloud 2025.0, Spring AI 1.0 | Versions alignées avec les technologies imposées |
| Persistance | H2 (fichier/mémoire) | Épreuve de 3 h : pas de dépendance d'infrastructure supplémentaire |
| Montant | `BigDecimal` pour les transactions, `double` pour le solde | Respect du modèle minimal imposé |
| Sécurité | Keycloak (OIDC), services en resource server JWT | Authentification centralisée, autorisations par rôle |
| LLM | Ollama en local (configurable) | Pas de clé API à stocker dans le dépôt |
