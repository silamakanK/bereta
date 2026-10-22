# BERETA — backend

Backend de l'application de gestion des cotisations associatives BERETA.

## Prérequis

- Java 21

Le wrapper Gradle fourni permet de construire et lancer le projet sans installer Gradle séparément.

## Lancer le backend

Depuis la racine du projet :

```bash
./gradlew bootRun
```

Dans un autre terminal, vérifier la route de test :

```bash
curl http://localhost:8080/api/bonjour
```

Réponse attendue :

```text
Bonjour depuis BERETA !
```

Si le port 8080 est déjà utilisé, lancer temporairement l'application sur un autre port :

```bash
./gradlew bootRun --args='--server.port=18080'
curl http://localhost:18080/api/bonjour
```

## Lancer les tests

```bash
./gradlew test
```

Le test de la route utilise `MockMvc` : il vérifie le démarrage du contexte Spring et la réponse HTTP sans démarrer un serveur externe.

## État du projet

Ce socle valide uniquement le démarrage de Spring Boot et une première route HTTP. Les caisses, campagnes, membres, paiements et autres fonctions métier seront ajoutés progressivement, avec leurs tests.
