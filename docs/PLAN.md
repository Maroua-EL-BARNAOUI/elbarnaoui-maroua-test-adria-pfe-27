# Plan de réalisation

Chaque partie est développée, testée, puis enregistrée séparément sur GitHub.

| # | Partie | Contenu (réf. cahier des charges) | Statut |
|---|---|---|---|
| 1 | Fondations et conception | A (projet Maven parent, .gitignore, README), B (diagrammes) | Terminé |
| 2 | account-service | C.1 : domain/repository/service, REST, GraphQL, MCP, tests | Terminé |
| 3 | transaction-service | C.2 et C.3 : règles métier, virement, OpenFeign, Resilience4j, tests | Terminé |
| 4 | Services techniques | D : config-service (`bank.transaction.max-amount=1000`), Eureka, Gateway | Terminé |
| 5 | Kafka | F : producer `bank.transactions.created` (clé = accountId), consumer qui journalise | À faire |
| 6 | Assistant IA | E : agent Spring AI, outils branchés aux serveurs MCP, API REST de test | À faire |
| 7 | Sécurité Keycloak | Authentification et autorisations des services | À faire |
| 8 | Client Angular | G : consultation des comptes, transactions, chat | À faire |
| 9 | Détection de fraude | H : script/notebook Python, pipeline scikit-learn, réponses aux 4 questions | À faire |
| 10 | DevOps | I : Dockerfile, docker-compose, GitHub Actions, réponses courtes | À faire |
| 11 | Rapport PDF | Réponses et captures d'écran des tests | À faire |

## Contraintes retenues

- Maven multi-modules, un module par micro-service.
- Couches par service : `domain/`, `repository/`, `service/`, `web/rest/`, `web/graphql/`, `mcp/`. REST, GraphQL et MCP appellent la même couche `service`.
- Règles métier : montant strictement positif et <= plafond configuré ; compte existant et ACTIVE ; devise identique ; consultation du solde avant, mise à jour après ; virement entre comptes.
- Les parties A (Google Sheet, repository GitHub privé, commits toutes les 15 minutes, rendu public final, dépôt Classroom) relèvent de l'utilisateur.
