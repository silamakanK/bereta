# BERETA — backend

Backend Spring Boot de l'application de gestion des cotisations associatives BERETA.

## Prérequis

- Java 21
- Accès au projet Supabase PostgreSQL

Le projet conserve Gradle et son wrapper : aucune installation séparée de Gradle n'est nécessaire.

## Connexion à PostgreSQL Supabase

Le backend lit les paramètres depuis l'environnement du processus :

| Variable | Valeur |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.cauffmnpfxewzdozwdst` |
| `DB_PASSWORD` | À saisir localement dans l'environnement de lancement |

`application.properties` transmet ces variables à Spring Boot et importe explicitement le fichier `.env` du répertoire de travail avec `spring.config.import`. Spring Boot ne charge pas automatiquement ce fichier. Le fichier local `bereta-backend/.env` est ignoré par Git ; ne copiez jamais son contenu dans un fichier suivi par Git.

Si le fichier n'existe pas encore sur une autre machine, le créer dans `bereta-backend/.env` avec ces entrées, puis y saisir localement le mot de passe :

```dotenv
DB_URL=jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.cauffmnpfxewzdozwdst
DB_PASSWORD=<mot-de-passe-local>
```

### IntelliJ IDEA

1. Ouvrir **Run → Edit Configurations…**.
2. Choisir la configuration de `BeretaBackendApplication` (ou la créer comme application Spring Boot, classe principale `com.bereta.BeretaBackendApplication`).
3. Régler **Working directory** sur `$PROJECT_DIR$/bereta-backend`, afin que Spring trouve `bereta-backend/.env`.
4. Le fichier `.env` est alors chargé explicitement par `application.properties`. Il est aussi possible de définir `DB_URL`, `DB_USERNAME` et `DB_PASSWORD` dans **Environment variables** ; les variables du processus prennent priorité sur le fichier.
5. Pour exécuter le contrôle de connexion décrit ci-dessous, ajouter `BERETA_DATABASE_VERIFY_ON_STARTUP=true` dans **Environment variables**.
6. Enregistrer la configuration et démarrer l'application.

Le champ d'IntelliJ accepte une liste telle que :

```text
DB_URL=jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require;DB_USERNAME=postgres.cauffmnpfxewzdozwdst;DB_PASSWORD=<mot-de-passe-local>;BERETA_DATABASE_VERIFY_ON_STARTUP=true
```

Remplacer `<mot-de-passe-local>` dans IntelliJ ; ne pas copier cette ligne complétée dans un fichier suivi par Git ou dans un terminal partagé.

## Vérifier l'accès sans modifier les tables

Avec le fichier `.env` renseigné, démarrer l'application en activant `BERETA_DATABASE_VERIFY_ON_STARTUP=true`, ou lancer depuis `bereta-backend` :

```bash
./gradlew bootRun --args='--bereta.database.verify-on-startup=true'
```

Le backend exécute alors uniquement `SELECT 1` au démarrage. En cas de succès, le journal indique que la requête a renvoyé `1`. Le mot de passe n'est jamais journalisé.

Le contrôle est désactivé par défaut. Pour le refaire, laisser cette variable à `true` dans la configuration de lancement puis redémarrer l'application. Il n'exécute aucune instruction de création ou de modification de table.

La stratégie Hibernate est configurée sur `spring.jpa.hibernate.ddl-auto=none` : Hibernate ne crée ni ne met à jour le schéma.

Les évolutions de schéma sont gérées par Flyway. Au prochain démarrage du backend, Flyway applique les migrations manquantes : `V1` crée les membres, `V2` crée les caisses principales, caisses locales et campagnes, `V3` crée les inscriptions des membres aux campagnes et `V4` crée les comptes utilisateurs. Elles ajoutent ces nouvelles tables sans altérer les tables existantes. `ddl-auto=none` reste actif : Hibernate ne gère pas le schéma.

## Membres — première API

- `GET /api/membres` : liste les membres par nom puis prénom.
- `GET /api/membres/{id}` : consulte un membre.
- `POST /api/membres` : crée un membre. Le prénom et le nom sont obligatoires ; le téléphone est facultatif.
- `PUT /api/membres/{id}` : modifie le prénom, le nom ou le téléphone d'un membre.
- `GET, POST /api/caisses-principales` : consulte et crée des caisses principales.
- `GET, POST /api/caisses-principales/{id}/caisses-locales` : consulte et crée leurs caisses locales.
- `GET /api/campagnes` : liste les campagnes (filtrage possible avec `caissePrincipaleId`).
- `POST /api/campagnes` : crée une campagne annuelle ou événementielle.
- `GET, POST /api/membres/{id}/inscriptions` : consulte et inscrit un membre à une campagne et à une caisse locale.

Les montants attendus sont stockés en décimal exact. Une campagne événementielle peut ne pas encore avoir de montant ni d'échéance. Une inscription conserve une copie du montant attendu au moment de sa création. Les homonymes restent autorisés.

## Connexion

L'application utilise une session serveur et un cookie de session `HttpOnly`. Le mot de passe est haché avec BCrypt ; le navigateur ne le conserve pas. Les routes `/api/auth/csrf` et `/api/auth/login` sont publiques ; les autres routes API demandent une session connectée. La protection CSRF est activée pour les requêtes qui modifient les données.

Pour créer le premier compte responsable, ajouter ces variables au fichier local `bereta-backend/.env` (ou à l'environnement de lancement IntelliJ) :

```dotenv
BOOTSTRAP_USER_EMAIL=responsable@example.com
BOOTSTRAP_USER_PASSWORD=<mot-de-passe-local-de-12-caracteres-minimum>
BOOTSTRAP_USER_NAME=Nom du responsable
```

Le compte est créé au premier démarrage seulement si son adresse n'existe pas déjà. Le mot de passe n'est jamais journalisé ni inclus dans Angular. Pour le réinitialiser, une procédure dédiée sera nécessaire ; modifier la variable après la création ne change pas le compte existant.

Routes d'authentification : `GET /api/auth/csrf` prépare le jeton CSRF, `POST /api/auth/login` prend `username` et `password` en formulaire, `GET /api/auth/me` renvoie le compte courant et `POST /api/auth/logout` ferme la session. L'interface Angular appelle ces routes et envoie automatiquement le jeton CSRF.

Les rôles sont enregistrés sur le compte, mais la restriction des données selon la caisse et les permissions spécifiques par rôle restent à implémenter. Les chiffres financiers du tableau de bord restent des exemples ; les paiements et l'historique des réaffectations restent à développer.

## Lancer le backend

Depuis ce dossier, après avoir renseigné les variables d'environnement dans le terminal ou dans IntelliJ :

```bash
./gradlew bootRun
```

Après avoir créé le compte initial, ouvrir l'interface Angular et se connecter. `GET /api/bonjour` demande désormais lui aussi une session ; un appel `curl` sans session reçoit `401 Unauthorized`.

Le backend écoute sur `http://localhost:8081`.

## Compiler et lancer les tests

```bash
./gradlew build
./gradlew test
```

Les tests utilisent une base H2 en mémoire isolée et ne nécessitent pas les identifiants Supabase. Cette configuration de test n'est pas utilisée au démarrage normal de l'application.
