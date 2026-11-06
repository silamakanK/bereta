# BERETA

[![CI](https://github.com/silamakanK/bereta/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/silamakanK/bereta/actions/workflows/ci.yml)

Gestion des cotisations d'associations : caisse principale, caisses locales, campagnes et paiements.

| Dossier | Contenu |
| --- | --- |
| [bereta-backend](bereta-backend) | API Spring Boot 3.5 / Java 21 / Gradle, PostgreSQL + Flyway |
| [bereta-frontend](bereta-frontend) | Application Angular 21 |
| [docs](docs) | Maquettes et documentation |

## Lancer en local

```bash
cd bereta-backend && ./gradlew bootRun     # API sur http://localhost:8081
cd bereta-frontend && npm ci && npm start  # front sur http://localhost:4200
```

## Tests

```bash
cd bereta-backend && ./gradlew build       # tests + couverture JaCoCo
cd bereta-frontend && npm run test:ci      # tests + couverture lcov
```
