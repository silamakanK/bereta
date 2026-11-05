# Authentification BERETA

*Branche `feat/auth-complete`, mise à jour du 04/11/2026 : version simplifiée, **sans code ni vérification**. L'audit initial est dans [AUDIT_AUTH.md](AUDIT_AUTH.md) ; les étapes OTP, appareils de confiance et WhatsApp ont été retirées.*

## 1. Fonctionnement

| Sujet | Comportement |
|---|---|
| Inscription | Téléphone (obligatoire, normalisé en E.164, unique) + mot de passe + confirmation + CGU. Le compte est **actif immédiatement** et la session est ouverte. Prénom, nom et e-mail sont demandés ensuite, sur l'écran facultatif « Complète ton profil ». |
| Connexion | Un champ « Téléphone ou e-mail » + mot de passe → access token + refresh token **directement**. Erreur toujours générique : « Identifiants incorrects ». |
| Mot de passe | Au moins 8 caractères dont 1 chiffre (règle de la maquette), haché en BCrypt (coût 12). |
| Session | Access token JWT HS256 de 15 min, gardé en mémoire côté front. Refresh token opaque de 7 jours, stocké en SHA-256, avec rotation et détection de réutilisation. Cookie `bereta_refresh` `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, plus l'en-tête `X-Bereta-Refresh: 1` sur `/refresh` et `/logout`. |
| Protection | Limitation de débit (Bucket4j) sur la connexion, par IP et par identifiant, sur l'inscription, par IP, et sur le mot de passe oublié. Verrouillage de 15 min après 5 mots de passe erronés. |
| Mot de passe oublié | **Désactivé** (`BERETA_FORGOT_PASSWORD_ENABLED=false`). L'écran renvoie vers le responsable ou l'administrateur. Le code du lien par e-mail et la table `password_reset_tokens` sont conservés pour une réactivation avec Brevo. |
| Réinitialisation | Le responsable génère un mot de passe provisoire pour un trésorier. L'administrateur peut en générer un pour tout compte (`POST /api/admin/users/{id}/password`). Dans les deux cas, toutes les sessions sont révoquées et le mot de passe doit être changé à la connexion suivante. |
| Déconnexion | Révocation du refresh token. « Tous les appareils » incrémente aussi la version des access tokens. |
| Journal | `auth_audit_log` : inscription, connexions réussies ou refusées, verrouillage, réutilisation d'un refresh token, déconnexions, changements de mot de passe. L'identifiant y est masqué et aucun secret n'est enregistré. Les actions d'administration vont en plus dans `admin_audit_log`. |

## 2. Endpoints

Toutes les erreurs sont au format `ProblemDetail`, avec `code` et un `detail` en français.

### Publics

| Méthode | URL | Corps | Réponses |
|---|---|---|---|
| POST | `/api/auth/register` | `phone`, `password`, `acceptTerms` ; `firstName?`, `lastName?`, `email?` | 201 `TokenResponse` + cookie · 400 `PASSWORD_POLICY` / `TERMS_REQUIRED` / `PHONE_INVALID` · 409 `ACCOUNT_EXISTS` · 429 |
| POST | `/api/auth/login` | `identifier` (téléphone ou e-mail), `password` | 200 `TokenResponse` + cookie · 401 `INVALID_CREDENTIALS` · 429 |
| POST | `/api/auth/refresh` | — (cookie et en-tête `X-Bereta-Refresh: 1`) | 200 `TokenResponse` + nouveau cookie · 401 `SESSION_EXPIRED` · 403 sans l'en-tête |
| POST | `/api/auth/logout` | — (cookie et en-tête) | 204, cookie effacé |
| POST | `/api/auth/password/forgot` | `identifier` | 503 `FORGOT_PASSWORD_DISABLED` (une fois réactivé : 200 `{ message }`, identique que le compte existe ou non) |
| POST | `/api/auth/password/reset` | `token` (lien e-mail), `password` | 200 `{ message }` · 410 `LINK_INVALID` |

`TokenResponse` = `{ accessToken, tokenType: "Bearer", expiresInSeconds: 900, user: { id, displayName, email, phone, role, mustChangePassword } }`.

### Connecté (`Authorization: Bearer …`)

| Méthode | URL | Corps | Réponses |
|---|---|---|---|
| GET | `/api/auth/me` | — | 200 `user` |
| PATCH | `/api/auth/profile` | `firstName?`, `lastName?`, `email?` (chaîne vide = retirer l'e-mail) | 200 `user` · 400 `EMAIL_INVALID` · 409 `EMAIL_UNAVAILABLE` |
| POST | `/api/auth/password/change` | `currentPassword`, `newPassword` | 200 `TokenResponse` (nouvelle session, les autres révoquées) · 400 `CURRENT_PASSWORD_INVALID` / `PASSWORD_POLICY` / `PASSWORD_UNCHANGED` |
| POST | `/api/auth/logout-all` | — | 204 |
| POST | `/api/admin/users/{id}/password` | — (rôle `ADMIN`) | 200 `{ temporaryPassword }`, montré une seule fois · 403 · 404 |
| POST | `/api/caisses-principales/{id}/acces/{membershipId}/mot-de-passe-provisoire` | — (responsable) | 200 `{ temporaryPassword, maskedPhone }` · 409 si le compte sert ailleurs |

Tant que `mustChangePassword` vaut `true`, seuls `/me`, `/password/change`, `/logout` et `/logout-all` sont accessibles (403 `PASSWORD_CHANGE_REQUIRED`).

## 3. Variables d'environnement

| Variable | Obligatoire | Rôle |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Oui | PostgreSQL / Supabase |
| `JWT_SECRET` | **Oui** (32 octets ou plus) | Signature des access tokens (`openssl rand -base64 48`) |
| `BERETA_CORS_ALLOWED_ORIGINS` | Non (`http://localhost:4200`) | Origines du front |
| `BERETA_REFRESH_COOKIE_SECURE` | Non (`true`) | `false` seulement en HTTP hors `localhost` |
| `BERETA_FORWARD_HEADERS` | Non (`none`) | `framework` derrière un reverse proxy (IP réelle) |
| `BERETA_RATE_LIMIT_ENABLED` | Non (`true`) | Désactivé seulement dans les tests |
| `BERETA_PHONE_DEFAULT_REGION` | Non (`FR`) | Pays des numéros saisis sans indicatif |
| `BERETA_FORGOT_PASSWORD_ENABLED` | Non (`false`) | Réactive le mot de passe oublié par lien e-mail |
| `BERETA_PASSWORD_RESET_BASE_URL` | Non | URL du front utilisée dans le lien |
| `BERETA_MAIL_PROVIDER` | Non (`log`) | `log` ou `smtp`, par exemple le relais Brevo `smtp-relay.brevo.com:587` |
| `MAIL_FROM`, `MAIL_FROM_NAME`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | Si `smtp` | — |
| `BOOTSTRAP_USER_*` | Non | Compte initial |

Supprimées : `OTP_HASH_SECRET`, `BERETA_SMS_PROVIDER`, `SMS_SENDER`, `BREVO_API_KEY`, `TWILIO_*`, `APP_DOMAIN`, `OTP_WHATSAPP_ENABLED`, `BERETA_WHATSAPP_PROVIDER`, `BERETA_TRUSTED_DEVICE_TTL`.

## 4. Base de données

- **V20** (`V20__remove_verification.sql`) supprime les tables `otp_codes` et `trusted_devices`, passe tous les comptes en actifs, puis supprime les colonnes `status`, `phone_verified_at` et `email_verified_at` de `user_accounts`. Elle a été testée sur PostgreSQL 16 à partir d'une base en V19. En production, au moment de la validation, la base contenait 0 compte, 0 code et 0 appareil.
- Les migrations V15 à V19 restent telles quelles, car elles sont déjà appliquées.
- V16 (écrite à la main) se trouve dans `db/vendor/postgresql/`, avec un contenu inchangé. Son équivalent H2 pour les tests est une migration Java dans `src/test/java/db/vendor/h2/`.

## 5. Points ouverts

1. **Mot de passe oublié.** Pour le réactiver : vérifier l'expéditeur dans Brevo, de préférence sur un domaine à soi plutôt qu'une adresse @gmail.com, puis passer `BERETA_FORGOT_PASSWORD_ENABLED=true` et rétablir le formulaire côté front. Seuls les comptes qui ont un e-mail pourront s'en servir.
2. **E-mail non vérifié.** Un utilisateur peut saisir l'e-mail de quelqu'un d'autre, ce qui bloque cette adresse pour son vrai titulaire. Le risque est faible tant que le lien par e-mail est désactivé ; il faudra une vérification avant de le réactiver.
3. **Inscription avec un numéro déjà pris.** Le message d'erreur révèle qu'un compte existe. C'est inévitable dès lors que l'inscription ouvre directement la session.
4. **Verrouillage du compte.** Un tiers qui connaît un numéro peut verrouiller le compte pendant 15 minutes. La limitation par identifiant atténue ce risque.
5. **Limitation de débit en mémoire.** Elle suffit pour une seule instance. Avec plusieurs réplicas, il faudra `bucket4j-redis`.
6. **Écran « Mon profil ».** Il n'est pas construit : changer son mot de passe ou se déconnecter de tous les appareils n'est possible que par l'API.
7. **Moindre privilège et tests.** L'application se connecte encore avec le compte `postgres`. Les tests tournent sur H2 ; il faudrait ajouter Testcontainers.
8. **Page des CGU.** Elle n'existe pas encore.
