# Authentification BERETA — livrable

*Branche `feat/auth-complete`, 04/11/2026. L'audit initial et les décisions prises sont dans [AUDIT_AUTH.md](AUDIT_AUTH.md).*

> ⚠️ **Avant le prochain démarrage du backend sur Supabase :**
> 1. Ajouter `JWT_SECRET` dans `bereta-backend/.env`, par exemple avec `openssl rand -base64 48`. Sans lui, l'application refuse de démarrer.
> 2. Désactiver l'API de données de Supabase (*Settings → API → Data API*).
>
> Au démarrage, Flyway appliquera les migrations V14 (fermeture du schéma public) et V15 (authentification).

---

## 1. Récapitulatif

| Sujet | Existait | Corrigé | Ajouté |
|---|---|---|---|
| Base Supabase ouverte au rôle `anon` (lecture et écriture) | — | V14 : `REVOKE` + RLS sur toutes les tables, plus un callback `afterMigrate` pour les tables futures | — |
| Identifiant | E-mail seul | E-mail **facultatif** | Téléphone E.164 unique (`libphonenumber`), connexion par e-mail **ou** téléphone |
| Inscription | E-mail + mot de passe de 6 caractères, rôle `TRESORIER` | Rôle `RESPONSABLE`, mot de passe de 8 caractères avec 1 chiffre, CGU obligatoires | Compte en attente tant que le téléphone n'est pas vérifié par SMS ; e-mail vérifié par code s'il est saisi |
| Connexion | Session serveur + CSRF, ouverte dès que le mot de passe est bon | — | 2 étapes : mot de passe puis **code SMS à chaque connexion** ; aucun jeton avant le code |
| Session | Cookie `JSESSIONID` | — | Access token JWT de 15 min (en mémoire côté front) + refresh token de 7 jours (cookie `HttpOnly; Secure; SameSite=Strict`), avec rotation et détection de réutilisation |
| Codes OTP | — | — | 6 chiffres, `SecureRandom`, HMAC-SHA256, 5 min, usage unique, 5 essais, renvoi après 60 s, 3 renvois par 15 min |
| Mot de passe oublié | Lien par e-mail de 30 min ; 503 si le SMTP manque | Lien de 15 min, réponse toujours identique | Code par SMS ; toutes les sessions révoquées ; notification par SMS et/ou e-mail |
| Trésoriers | Invitation par lien e-mail | — | Désignation **par téléphone** (rattachement du compte s'il existe, sinon mot de passe provisoire), changement imposé à la première connexion, nouveau mot de passe provisoire remis par le responsable |
| Protection | Aucune limitation | Cookie `Secure`, CORS centralisé | Bucket4j sur la connexion, les envois de code, l'oubli et l'inscription ; verrouillage de 15 min après 5 échecs |
| Déconnexion | Session invalidée | — | Révocation du refresh token ; « tous les appareils » |
| Journal | Admin seulement | — | `auth_audit_log` : 16 types d'événements, identifiant masqué, IP, navigateur, jamais aucun secret |
| Erreurs | Messages du backend perdus | `ProblemDetail` avec `detail` en français, affiché tel quel par le front | — |
| Front | Formulaires e-mail, pas de garde | Route `/admin` branchée ; locale `fr-FR` enregistrée | Intercepteur avec rafraîchissement automatique, `authGuard` / `guestGuard`, OTP en 6 cases, jauge de force, pages conformes à la charte |

**Supprimé (remplacé par la nouvelle version) :**
- `AccountStatusFilter` et `BeretaUserDetailsService` (le convertisseur JWT les remplace) ;
- `UserRegistrationService` et `PasswordResetService` (remplacés par `RegistrationService` et `PasswordService`) ;
- `SmtpPasswordResetEmailSender` (remplacé par `EmailSender`) ;
- l'invitation de trésorier par lien : `TreasurerInvitationService`, `FundInvitation*` et `/api/auth/invitations/accept`. **La table `fund_invitations` est conservée** ;
- les annotations `@CrossOrigin` dispersées, et l'ancien `AuthControllerTest`.

**Tests :**
- backend : **42 tests verts**, dont `AuthFlowIntegrationTest` (15 scénarios) et `AuthRulesTest` ;
- frontend : 13 tests Vitest verts, build de production OK ;
- parcours complet vérifié avec curl sur un PostgreSQL 16 local (V14 puis V15), puis à travers le proxy Angular.

---

## 2. Endpoints

Sauf mention contraire, les corps sont en JSON. Les erreurs sont au format `ProblemDetail` : `{ status, detail, code }`, avec `retryAfterSeconds` et l'en-tête `Retry-After` en cas de 429.

### Publics

| Méthode | URL | Corps | Réponse |
|---|---|---|---|
| POST | `/api/auth/register` | `firstName`, `lastName`, `phone`, `email?`, `password`, `acceptTerms` | 201 `{ phoneVerification: { challengeId, channel, maskedDestination, expiresInSeconds, resendAvailableInSeconds }, emailVerificationSent }` · 400 `PASSWORD_POLICY` / `TERMS_REQUIRED` / `PHONE_INVALID` · 409 `PHONE_TAKEN` / `EMAIL_TAKEN` · 429 |
| POST | `/api/auth/register/verify` | `challengeId`, `code` | 200 `TokenResponse` + cookie · 400 `CODE_INVALID` |
| POST | `/api/auth/login` | `identifier` (e-mail ou téléphone), `password` | 200 `{ step: "OTP_REQUIRED", challengeId, maskedPhone, expiresInSeconds, resendAvailableInSeconds }`, ou `{ step: "PHONE_REQUIRED", enrollmentToken }` pour un compte sans téléphone · 401 `INVALID_CREDENTIALS` (message générique, y compris compte verrouillé ou inconnu) · 429 |
| POST | `/api/auth/login/phone` | `enrollmentToken`, `phone` | 200 `{ step: "OTP_REQUIRED", … }` · 401 `ENROLLMENT_EXPIRED` · 409 `PHONE_TAKEN` |
| POST | `/api/auth/login/verify` | `challengeId`, `code` | 200 `TokenResponse` + cookie · 400 `CODE_INVALID` |
| POST | `/api/auth/otp/resend` | `challengeId` | 200 `{ challengeId, … }` (même réponse si le challenge n'existe pas) · 429 `RESEND_TOO_SOON` / `RESEND_LIMIT` |
| POST | `/api/auth/refresh` | — (cookie `bereta_refresh` et en-tête `X-Bereta-Refresh: 1`) | 200 `TokenResponse` + nouveau cookie · 401 `SESSION_EXPIRED` · 403 si l'en-tête manque |
| POST | `/api/auth/logout` | — (cookie et en-tête `X-Bereta-Refresh: 1`) | 204, cookie effacé |
| POST | `/api/auth/password/forgot` | `identifier` | 200 `{ message, channel: "SMS" \| "EMAIL", challengeId (SMS seulement), expiresInSeconds }`, identique que le compte existe ou non |
| POST | `/api/auth/password/reset/code` | `challengeId`, `code`, `password` | 200 `{ message }` · 400 `CODE_INVALID` / `PASSWORD_POLICY` |
| POST | `/api/auth/password/reset` | `token` (lien e-mail), `password` | 200 `{ message }` · 410 `LINK_INVALID` |

`TokenResponse` = `{ accessToken, tokenType: "Bearer", expiresInSeconds: 900, user: { id, displayName, email, phone, role, emailVerified, phoneVerified, mustChangePassword } }`

### Connecté (en-tête `Authorization: Bearer …`)

| Méthode | URL | Corps | Réponse |
|---|---|---|---|
| GET | `/api/auth/me` | — | 200 `user` |
| POST | `/api/auth/password/change` | `currentPassword`, `newPassword` | 200 `TokenResponse` (nouvelle session ; les autres sont révoquées) · 400 `CURRENT_PASSWORD_INVALID` / `PASSWORD_POLICY` / `PASSWORD_UNCHANGED` |
| POST | `/api/auth/email/send-code` | — | 200 `{ challengeId, … }` · 400 `EMAIL_MISSING` · 409 `EMAIL_ALREADY_VERIFIED` |
| POST | `/api/auth/email/verify` | `code` | 200 `user` |
| POST | `/api/auth/logout-all` | — | 204 : tous les appareils sont déconnectés |
| POST | `/api/caisses-principales/{id}/acces` | `localFundId`, `phone`, `firstName?`, `lastName?` | 201 `{ membership, accountCreated, temporaryPassword }`. Le mot de passe provisoire n'est renvoyé que si le compte vient d'être créé. Responsable uniquement |
| POST | `/api/caisses-principales/{id}/acces/{membershipId}/mot-de-passe-provisoire` | — | 200 `{ temporaryPassword, maskedPhone }` · 409 si le compte sert aussi dans une autre association |

Tant qu'un mot de passe provisoire n'est pas remplacé, toutes les autres routes répondent **403 `PASSWORD_CHANGE_REQUIRED`**. Restent ouvertes : `/me`, `/password/change`, `/logout` et `/logout-all`.

---

## 3. Variables d'environnement

Modèle : `bereta-backend/.env.example`.

| Variable | Obligatoire | Rôle |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Oui | PostgreSQL / Supabase |
| `JWT_SECRET` | **Oui** (au moins 32 octets) | Signature HS256 des access tokens |
| `OTP_HASH_SECRET` | Non (par défaut `JWT_SECRET`) | Clé HMAC des codes OTP |
| `BERETA_CORS_ALLOWED_ORIGINS` | Non (`http://localhost:4200`) | Origines du front, séparées par des virgules |
| `BERETA_REFRESH_COOKIE_SECURE` | Non (`true`) | Ne mettre `false` qu'en HTTP hors `localhost` |
| `BERETA_PASSWORD_RESET_BASE_URL` | Non | URL du front, utilisée dans le lien de réinitialisation |
| `BERETA_FORWARD_HEADERS` | Non (`none`) | `framework` derrière un reverse proxy, pour obtenir l'IP réelle (limitation de débit, audit) |
| `BERETA_RATE_LIMIT_ENABLED` | Non (`true`) | Désactivé seulement dans les tests |
| `BERETA_PHONE_DEFAULT_REGION` | Non (`FR`) | Pays des numéros saisis sans indicatif |
| `BERETA_SMS_PROVIDER` | Non (`log`) | `log` (code écrit dans les logs, **développement uniquement**), `brevo` ou `twilio` |
| `SMS_SENDER` | Non (`BERETA`) | Nom d'expéditeur des SMS |
| `BREVO_API_KEY` | Si `brevo` | — |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | Si `twilio` | — |
| `BERETA_MAIL_PROVIDER` | Non (`log`) | `log` ou `smtp` (fonctionne aussi avec le SMTP de Brevo) |
| `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | Si `smtp` | — |
| `BOOTSTRAP_USER_EMAIL`, `BOOTSTRAP_USER_PHONE`, `BOOTSTRAP_USER_PASSWORD`, `BOOTSTRAP_USER_NAME` | Non | Compte initial. Le mot de passe doit respecter la nouvelle règle |

`BERETA_MAIL_ENABLED` disparaît : il est remplacé par `BERETA_MAIL_PROVIDER`.

---

## 4. Points restants et décisions à valider

1. **Fournisseur SMS.** Il faut choisir entre Brevo et Twilio, ouvrir le compte et faire déclarer l'expéditeur « BERETA », ce qui est réglementé en France. Un OTP à chaque connexion représente un coût par SMS. Les deux implémentations suivent la documentation des API mais **n'ont pas été testées avec un vrai compte**.
2. **Mot de passe provisoire.** Il est affiché une seule fois au responsable, et le SMS envoyé au trésorier ne fait que le prévenir. La maquette dit « Reçu par SMS / Fiche » : faut-il aussi envoyer le mot de passe par SMS ?
3. **Option « Créer un profil indépendant »** de la maquette de création de caisse : elle est incompatible avec un téléphone unique et n'a pas été implémentée.
4. **Comptes inscrits mais jamais vérifiés.** Ils réservent le numéro saisi. Il faut une règle, par exemple libérer le numéro au bout de 24 h. La purge est délicate avec la règle « aucune suppression ».
5. **Comptes existants.** Les 2 comptes actuels n'ont pas de téléphone : à leur prochaine connexion, ils devront en ajouter un puis le vérifier.
6. **Ajout d'un co-responsable et actions de l'admin.** Ils se font encore **par e-mail** (`AssociationAccessService.addManager`, `AdminService`). Un compte qui n'a qu'un téléphone ne peut donc pas être ajouté comme co-responsable : à adapter.
7. **Écran « Mon profil ».** Il n'est pas construit, alors que les endpoints existent : changement de mot de passe, déconnexion de tous les appareils, vérification de l'e-mail. Dans la maquette, la double authentification apparaît comme une option ; ici, elle est toujours active.
8. **Limitation de débit en mémoire.** Elle suffit pour une seule instance. Avec plusieurs réplicas (Kubernetes), il faudra un stockage partagé, Redis via `bucket4j-redis`.
9. **Verrouillage du compte.** Un tiers qui connaît un numéro peut verrouiller le compte pendant 15 minutes. C'est le prix du verrouillage demandé ; la limitation par identifiant l'atténue.
10. **Page des CGU.** Elle n'existe pas encore : le texte à cocher n'a pas de lien.
11. **Moindre privilège.** L'application se connecte encore avec le compte `postgres` : il faut créer un rôle dédié.
12. **Tests sur H2.** Les tests automatiques tournent sur H2 ; le comportement sous PostgreSQL (verrous, RLS) n'a été vérifié qu'à la main. Il faudrait ajouter Testcontainers.
13. **Taille du bundle front.** Il pèse 569 Ko, au-delà du budget de 500 Ko défini dans `angular.json`. C'est un avertissement, pas une erreur.
