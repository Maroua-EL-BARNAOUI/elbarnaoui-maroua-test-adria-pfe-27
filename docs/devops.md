# DevOps (Partie I) : réponses courtes

## Livrables

| Livrable | Fichier |
|---|---|
| Dockerfile (tous les micro-services, paramétré par `MODULE`) | [Dockerfile](../Dockerfile) |
| Docker Compose (Kafka + 6 services, dépendances et contrôles de santé) | [docker-compose.yml](../docker-compose.yml) |
| Pipeline GitHub Actions : compilation, tests, images, publication, déploiement | [.github/workflows/ci-cd.yml](../.github/workflows/ci-cd.yml) |

Lancement : `docker compose up -d --build`, puis la gateway répond sur http://localhost:8080.

## 1. Secrets et configuration

- **Configuration** : valeurs non sensibles qui varient selon l'environnement (plafond `bank.transaction.max-amount`, URLs, ports). Elle vit dans le config-service et les variables d'environnement (`CONFIG_SERVER_URL`, `EUREKA_URL`, `KAFKA_BOOTSTRAP_SERVERS`), versionnée avec le code.
- **Secrets** : tout ce qui donne un accès (mots de passe, clés d'API du LLM, secrets clients Keycloak, jetons de registre). Ils ne sont jamais dans le dépôt, ni dans les images, ni dans le config-service en clair. On les injecte à l'exécution via un gestionnaire (GitHub Actions Secrets en CI, Docker/Kubernetes secrets ou Vault au déploiement), avec rotation possible et accès restreint. Le `.gitignore` exclut `.env` et les clés.
- Distinction pratique : la configuration peut être lue et modifiée par l'équipe, un secret n'est lisible que par le service qui en a besoin.

## 2. Contrôles de démarrage, disponibilité et vitalité

Spring Boot Actuator expose les sondes (activées dans chaque service) :

- **Vitalité (liveness)** `/actuator/health/liveness` : le processus fonctionne ; sinon l'orchestrateur le redémarre.
- **Disponibilité (readiness)** `/actuator/health/readiness` : le service peut recevoir du trafic (dépendances prêtes) ; sinon il est retiré de l'équilibrage. C'est la sonde utilisée par les `healthcheck` de docker-compose.
- **Démarrage (startup)** : laisse le temps à un démarrage lent (`start_period` dans compose, `startupProbe` sous Kubernetes) avant d'activer la vitalité, pour éviter des redémarrages en boucle.
- `depends_on` avec `condition: service_healthy` impose l'ordre : config et Eureka, puis les services métier, puis la gateway.

## 3. Logs, métriques et traces distribuées

- **Logs** : sortie standard au format JSON structuré, avec un identifiant de corrélation par requête, collectés par Loki ou ELK. Pas de données sensibles dans les logs.
- **Métriques** : Micrometer + endpoint Prometheus, visualisées dans Grafana (latence, taux d'erreur, état des circuit breakers Resilience4j, retard du consumer Kafka).
- **Traces distribuées** : OpenTelemetry (ou Micrometer Tracing) vers Tempo ou Zipkin. Le `traceId` traverse gateway, transaction-service, account-service (Feign) et les messages Kafka (en-têtes), ce qui permet de suivre une transaction de bout en bout.

## 4. Stratégie de rollback

- Les images sont publiées avec le **SHA du commit** comme tag immuable (jamais seulement `latest`) : revenir en arrière consiste à redéployer le tag précédent (`IMAGE_TAG=<sha précédent> docker compose up -d`).
- Déploiement progressif (rolling ou canary) : si les sondes de disponibilité ou le taux d'erreur se dégradent, le déploiement s'arrête et revient automatiquement au tag précédent.
- Les migrations de base de données sont **rétro-compatibles** (ajout avant suppression) pour que l'ancienne version fonctionne encore sur le nouveau schéma.
- Les évènements Kafka gardent un format compatible (champs ajoutés seulement) ; les consumers ignorent les champs inconnus.
