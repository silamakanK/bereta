# Image du backend BERETA (Spring Boot 3.5, Java 21, Gradle).
# Build : docker build -t bereta-backend .   Exécution : docker run -p 8080:8080 --env-file .env bereta-backend

# Étape 1 : compilation avec le wrapper Gradle du projet
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dépendances d'abord : cette couche reste en cache tant que build.gradle ne change pas.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src ./src
# Les tests tournent dans la CI, pas dans l'image. On garde le jar exécutable (pas le « -plain »).
RUN ./gradlew --no-daemon -q bootJar \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# Étape 2 : exécution (JRE seul, utilisateur non root)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bereta && adduser -S bereta -G bereta
COPY --from=build /app/app.jar app.jar
USER bereta
# Mémoire limitée (offres gratuites ~512 Mo) : 75 % de la RAM du conteneur, GC série (le plus léger).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
# Render (et la plupart des hébergeurs) fournissent le port dans $PORT ; 8080 par défaut.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
