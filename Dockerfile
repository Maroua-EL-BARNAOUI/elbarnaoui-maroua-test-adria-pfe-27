# Dockerfile unique pour tous les micro-services Java :
#   docker build --build-arg MODULE=account-service -t bank/account-service .
# Build multi-étapes : compilation avec Maven, image finale réduite (JRE) exécutée en utilisateur non-root.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY . .
# -am construit aussi le POM parent ; les tests sont exécutés par la CI, pas ici
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
# curl sert uniquement aux contrôles de santé de docker-compose
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 bank
WORKDIR /app
COPY --from=build /build/${MODULE}/target/${MODULE}-*-SNAPSHOT.jar app.jar
USER bank
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
