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

Les évolutions de schéma sont gérées par Flyway. `V12__add_association_scope.sql` crée les associations et leurs accès responsables, puis ajoute un périmètre d’association aux caisses, membres, inscriptions, paiements, remises, accès trésoriers et invitations. Les clés étrangères composites empêchent d’associer les opérations et inscriptions à des associations différentes. Cette migration suppose que les tables métier sont vides, conformément à la remise à zéro réalisée avant son déploiement. Elle ne modifie aucune migration antérieure ni l’historique Flyway. `ddl-auto=none` reste actif : Hibernate ne gère pas le schéma.

## Associations et périmètre des données

- `GET /api/associations` liste les associations auxquelles le compte connecté a accès.
- `POST /api/associations` crée une association et ajoute automatiquement son créateur comme responsable.
- `GET, POST /api/associations/{id}/responsables` permet aux responsables de consulter les responsables et d’en ajouter à partir d’un compte BERETA existant.
- Les responsables peuvent gérer plusieurs associations ; plusieurs responsables peuvent gérer une même association.
- Toute requête métier exige le contexte `X-Association-Id`. Le backend vérifie que le compte est responsable de cette association ou trésorier d’au moins une caisse locale qui en dépend.
- Les listes de caisses, membres, campagnes, paiements et remises sont filtrées par l’association active. Un responsable voit toutes les fiches de son association ; un trésorier voit uniquement les fiches et opérations qui touchent ses caisses locales attribuées.
- Une fiche membre est séparée par association. Aucun rapprochement automatique n’est fait sur le nom. Dans une association, la même fiche peut être inscrite à des campagnes de plusieurs caisses principales.
- Les trésoriers peuvent avoir plusieurs caisses locales attribuées. Une association commune ne donne pas accès aux autres caisses.

Après connexion, un compte sans association est invité à en créer une. S’il en gère ou rejoint plusieurs, il choisit son association active. Il peut en créer une autre depuis le sélecteur du tableau de bord.

## Membres — première API

- `GET /api/membres` : liste les membres par nom puis prénom.
- `GET /api/membres/{id}` : consulte un membre.
- `POST /api/membres` : crée un membre dans une caisse locale accessible. Le prénom, le nom et `localFundId` sont obligatoires ; le téléphone est facultatif.
- `PUT /api/membres/{id}` : modifie le prénom, le nom ou le téléphone d'un membre.
- `GET, POST /api/caisses-principales` : consulte et crée des caisses principales.
- `GET, POST /api/caisses-principales/{id}/caisses-locales` : consulte et crée leurs caisses locales.
- `GET /api/campagnes` : liste les campagnes (filtrage possible avec `caissePrincipaleId`).
- `POST /api/campagnes` : crée une campagne annuelle ou événementielle.
- `GET, POST /api/membres/{id}/inscriptions` : consulte et inscrit un membre à une campagne et à une caisse locale.
- `GET /api/paiements/inscriptions-a-payer` : inscriptions accessibles qui ont encore un solde à régler.
- `POST /api/paiements/inscriptions/{inscriptionId}` : enregistre un versement partiel ou total avec sa date, son mode, une note facultative et l’auteur connecté.
- `GET /api/paiements` et `GET /api/paiements/membres/{id}` : consultent l’historique accessible.
- `GET /api/paiements/synthese` : calcule les montants attendus, reçus et restants à partir des inscriptions et versements accessibles.
- `POST /api/caisses-locales/{id}/remises` : le trésorier déclaré de la caisse locale déclare une remise d’un montant disponible, avec sa date, son mode et une note facultative.
- `GET /api/caisses-locales/{id}/remises/solde` : consulte les cotisations encaissées, le disponible local et les remises en transit de cette caisse.
- `GET /api/caisses-principales/{id}/remises` : consulte les remises accessibles et leur historique d’auteurs et de statuts.
- `GET /api/caisses-principales/{id}/remises/synthese` : le responsable voit les soldes locaux, le total en transit, le solde central et le total global. Un trésorier reçoit uniquement les montants de sa caisse locale.
- `POST /api/caisses-principales/{id}/remises/{remiseId}/confirmer` : le responsable confirme une remise encore en attente.
- `POST /api/caisses-principales/{id}/remises/{remiseId}/annuler` : le responsable annule une remise en attente avec un motif obligatoire.

Les montants attendus sont stockés en décimal exact. Une campagne événementielle peut ne pas encore avoir de montant ni d'échéance. Une inscription conserve une copie du montant attendu au moment de sa création. Les homonymes restent autorisés.

## Connexion

L'application utilise une session serveur et un cookie de session `HttpOnly`. Le mot de passe est haché avec BCrypt ; le navigateur ne le conserve pas. Les routes `/api/auth/csrf`, `/api/auth/login`, `/api/auth/register`, `/api/auth/invitations/accept`, `/api/auth/password/forgot` et `/api/auth/password/reset` sont publiques ; les autres routes API demandent une session connectée. La protection CSRF est activée pour les requêtes qui modifient les données.

La page `/inscription` crée un compte utilisateur sans rôle de caisse ni accès existant. Après connexion, l’utilisateur crée une association dont il devient responsable, puis ses caisses principales. Pour rejoindre une association existante comme trésorier, il doit utiliser une invitation valide à une caisse locale. Le mot de passe doit contenir au moins 6 caractères et ne pas dépasser la limite de 72 octets UTF-8 de BCrypt. L’adresse e-mail est normalisée en minuscules et son unicité est également imposée par PostgreSQL.

### Réinitialisation du mot de passe

Le lien **Mot de passe oublié ?** mène à un parcours qui envoie un jeton aléatoire à usage unique, stocké sous forme hachée et valable 30 minutes. L’envoi réel est désactivé par défaut (`BERETA_MAIL_ENABLED=false`) : tant qu’un SMTP n’est pas configuré, l’API répond `503 Service Unavailable` et l’interface indique qu’aucun e-mail n’a été envoyé.

Pour activer l’envoi, définir dans l’environnement local du backend ou dans IntelliJ :

```dotenv
BERETA_MAIL_ENABLED=true
BERETA_PASSWORD_RESET_BASE_URL=https://votre-frontend.example
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=<compte-smtp>
MAIL_PASSWORD=<secret-smtp-local>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_FROM=Bereta <no-reply@example.com>
```

Remplacer ces valeurs par celles du fournisseur e-mail. Ne pas committer `MAIL_PASSWORD`. Sans cette configuration, aucune simulation d’envoi n’est faite.

Pour créer un compte initial avec lequel se connecter, ajouter ces variables au fichier local `bereta-backend/.env` (ou à l'environnement de lancement IntelliJ) :

```dotenv
BOOTSTRAP_USER_EMAIL=responsable@example.com
BOOTSTRAP_USER_PASSWORD=<mot-de-passe-local-de-6-caracteres-minimum>
BOOTSTRAP_USER_NAME=Nom du responsable
```

Le compte est créé au premier démarrage seulement si son adresse n'existe pas déjà. Le mot de passe n'est jamais journalisé ni inclus dans Angular. Modifier la variable après la création ne change pas le compte existant ; pour le réinitialiser, utiliser le parcours e-mail après avoir configuré SMTP ou définir le nouveau mot de passe par une procédure locale sécurisée.

Le responsable est défini par association et voit toutes ses caisses principales, locales et fiches membres. Le trésorier conserve un accès précis aux caisses locales qui lui sont attribuées ; il ne voit que les membres créés dans ses caisses ou inscrits à une campagne suivie dans celles-ci, avec leurs inscriptions et opérations autorisées. Les créations de caisses locales et de campagnes ainsi que la gestion des accès sont réservées aux responsables de l’association. Les contrôles sont faits dans les services backend, même si un identifiant d’une autre association ou caisse est fourni dans une URL.

Dans **Configuration → Accès des trésoriers**, le responsable crée un lien d’invitation à usage unique, valable 7 jours, pour une caisse locale. Il doit transmettre le lien en privé ; aucun e-mail n’est envoyé automatiquement. Le trésorier crée alors son compte et son mot de passe. Les comptes existants peuvent être ajoutés par `POST /api/caisses-principales/{id}/acces` ; les accès peuvent être désactivés avec `DELETE /api/caisses-principales/{id}/acces/{membershipId}`.

Routes d'authentification : `GET /api/auth/csrf` prépare le jeton CSRF, `POST /api/auth/login` prend `username` et `password` en formulaire, `POST /api/auth/register` crée un compte, `GET /api/auth/me` renvoie le compte courant, `POST /api/auth/invitations/accept` accepte une invitation et `POST /api/auth/logout` ferme la session. La récupération utilise `POST /api/auth/password/forgot` puis `POST /api/auth/password/reset`. L'interface Angular appelle ces routes et envoie automatiquement le jeton CSRF.

Les versements sont enregistrés de façon immuable, avec le compte auteur et la caisse locale, et leur montant ne peut pas dépasser le reste dû. Le tableau de bord calcule les montants depuis les inscriptions et paiements accessibles ; les montants attendus non définis sont exclus du calcul du reste dû.

Une remise en attente est déduite du disponible local et ajoutée aux fonds en transit ; elle n’est pas encore incluse dans le solde central. À confirmation par le responsable, elle quitte les fonds en transit et rejoint le solde central. Une annulation par le responsable exige un motif et libère le montant dans la caisse locale. Le total détenu est calculé ainsi : disponible local + fonds en transit + solde central. Une remise ne change ni les cotisations encaissées ni ce total global. Les montants de fonds détenus sont provisoires tant que les dépenses ne sont pas suivies. Les corrections traçables de paiements, dépenses, reprise de soldes, historique des réaffectations et imports restent à développer.

## Lancer le backend

Depuis ce dossier, après avoir renseigné les variables d'environnement dans le terminal ou dans IntelliJ :

```bash
./gradlew bootRun
```

Après avoir créé ou configuré un compte, ouvrir l'interface Angular et se connecter. `GET /api/bonjour` reste protégé par session et sert uniquement d'endpoint de test sans données métier.

Le backend écoute sur `http://localhost:8081`.

## Compiler et lancer les tests

```bash
./gradlew build
./gradlew test
```

Les tests utilisent une base H2 en mémoire isolée et ne nécessitent pas les identifiants Supabase. Cette configuration de test n'est pas utilisée au démarrage normal de l'application.
