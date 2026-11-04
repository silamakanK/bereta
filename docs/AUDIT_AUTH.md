# Audit de l'inscription et de l'authentification — BERETA

*Audit du 04/11/2026, réalisé avant toute modification. Sources : maquettes `docs/maquettes/`, code backend et frontend, base Supabase (consultée en lecture seule).*

---

## 0. 🔴 Problème critique à traiter immédiatement : la base Supabase est ouverte

Requête en lecture seule sur la base de production (`aws-0-eu-west-1.pooler.supabase.com`) :

| Constat | Résultat |
|---|---|
| RLS activé | **Non**, sur les 15 tables du schéma `public` |
| Le rôle `anon` peut lire (`SELECT`) | **Oui**, sur toutes les tables, y compris `user_accounts`, `password_reset_tokens`, `payments` et `members` |
| Le rôle `anon` peut écrire (`INSERT` / `UPDATE` / `DELETE`) | **Oui**, vérifié sur `user_accounts`, `payments`, `password_reset_tokens` et `flyway_schema_history` |
| Compte utilisé par l'application | `postgres`, le compte propriétaire de la base |

**Conséquence.** Si l'API de données de Supabase (PostgREST) est active, toute personne qui connaît l'URL du projet et la clé `anon` peut agir directement sur la base. Cette clé est publique par conception. Elle peut notamment :
- lire les e-mails et les empreintes BCrypt des mots de passe ;
- passer un compte en `ADMIN` (`UPDATE user_accounts SET role='ADMIN'`) ;
- modifier ou supprimer des paiements et des membres ;
- lire les numéros de téléphone des membres.

**Actions proposées (à valider) :**
1. **Tout de suite, dans le tableau de bord Supabase** : ouvrir *Settings → API → Data API*, puis désactiver l'API ou retirer `public` des schémas exposés. L'application n'utilise pas PostgREST : elle passe uniquement par JDBC.
2. **Migration Flyway `V14__lock_down_public_schema.sql`** :
   - `REVOKE ALL` sur les tables, séquences et fonctions du schéma `public` pour `anon` et `authenticated` ;
   - `ALTER DEFAULT PRIVILEGES` pour que les futures tables soient protégées elles aussi ;
   - `ENABLE ROW LEVEL SECURITY` sur chaque table, sans politique, ce qui refuse tout accès aux rôles autres que le propriétaire.

   L'application se connecte en tant que propriétaire et ignore donc le RLS : elle continuera de fonctionner sans changement.
3. **Plus tard** : créer un rôle PostgreSQL dédié à l'application, avec les seuls droits nécessaires (moindre privilège), pour ne plus utiliser `postgres`.

État des données : la base ne contient que **2 comptes**, et aucun admin. La migration vers le téléphone comme identifiant n'a donc presque aucune donnée existante à reprendre.

---

## 1. Écrans d'authentification présents dans les maquettes

| Maquette | Contenu |
|---|---|
| `connexion_bereta` | Un seul champ « Numéro de téléphone ou e-mail », mot de passe avec bouton Afficher, liens « Mot de passe oublié ? » et « Créer un compte responsable », accès à l'assistance |
| `inscription_responsable_bereta` | Prénom, nom, **téléphone obligatoire** (sélecteur +33), **e-mail facultatif**, mot de passe « au moins 8 caractères et 1 chiffre » avec jauge de force, confirmation, case des CGU |
| `mot_de_passe_oubli_bereta` | **Le trésorier contacte son responsable**, qui réinitialise son mot de passe provisoire. **Le responsable reçoit un code par SMS** sur son numéro |
| `premi_re_connexion_tr_sorier_bereta` | Saisie du mot de passe provisoire, nouveau mot de passe (8 caractères minimum), confirmation |
| `cr_er_caisse_tape_2_tr_sorier_bereta` | Le responsable désigne le trésorier **par téléphone**. Si le compte existe, il est rattaché ; sinon un profil est créé avec un **mot de passe initial**. Le trésorier est prévenu par SMS |
| `mon_profil_bereta` | Modifier ses coordonnées, modifier son mot de passe, « Double authentification (2FA) activée via SMS », déconnexion |
| `choix_de_la_caisse_bereta`, `s_lecteur_d_association_bereta` | Écrans affichés après la connexion |

**Écrans absents des maquettes**, mais exigés par la mission : saisie du code OTP à la connexion, vérification de l'e-mail, vérification du téléphone, saisie du code reçu par SMS puis du nouveau mot de passe, compte verrouillé, « déconnecter tous les appareils ». Je les dessinerai en suivant la charte `tontine_trust/DESIGN.md` (champs de 52 px de haut, texte de 16 px minimum, labels toujours visibles).

---

## 2. Tableau d'audit

Légende : ✅ OK · 🟡 partiel · ❌ manquant

| Fonctionnalité | Back | Front | Maquette | Statut | Action prévue |
|---|---|---|---|---|---|
| Inscription | `POST /api/auth/register` (prénom, nom, e-mail, mot de passe). Crée le rôle global **`TRESORIER`** | `register.component` | Oui | 🟡 | Ajouter téléphone et CGU, statut `PENDING_VERIFICATION`, rôle `RESPONSABLE` |
| E-mail unique | Index unique `email_normalized` (V10), réponse 409 | Message 409 | — | ✅ | Conserver. L'e-mail devient éventuellement facultatif (décision n°2) |
| Téléphone unique | ❌ | ❌ | Oui | ❌ | Colonne `phone_e164` avec index unique partiel |
| Normalisation E.164 (`libphonenumber`) | ❌ | ❌ | Sélecteur +33 | ❌ | `PhoneNumberService`, région par défaut FR |
| Politique de mot de passe | 6 à 72 octets (back et front) | `register-validation.ts` | « 8 caractères et 1 chiffre » | 🟡 | `PasswordPolicy` partagée par le back et le front (décision n°3) |
| BCrypt | Coût 12 | — | — | ✅ | Conserver |
| Vérification de l'e-mail | ❌ | ❌ | Absente | ❌ | Code OTP `EMAIL_VERIFY` envoyé par e-mail |
| Vérification du téléphone | ❌ | ❌ | Absente | ❌ | Code OTP `PHONE_VERIFY` envoyé par SMS |
| Renvoi de code (délai 60 s, 3 envois par 15 min) | ❌ | ❌ | — | ❌ | Géré dans `OtpService` et à l'écran |
| Connexion par e-mail **ou** téléphone | `formLogin`, `username` = e-mail uniquement | E-mail uniquement, validé par une regex | Oui | 🟡 | Détection automatique (`@` → e-mail, sinon téléphone) |
| Connexion en 2 étapes (OTP SMS) | ❌ La session est ouverte dès que le mot de passe est bon | ❌ | Profil : « 2FA activée » | ❌ | `POST /login` renvoie un `challengeId`, puis `POST /login/verify` |
| Access token JWT (15 min) + refresh token (7 j) | ❌ Session `JSESSIONID` + cookie CSRF | Cookie de session | — | ❌ | Changement d'architecture (voir §5) |
| Message d'erreur générique | ✅ « Identifiants invalides » en 401, y compris pour un compte désactivé | ✅ | — | ✅ | Conserver et étendre à l'étape OTP |
| Numéro masqué (`+33 6 ** ** ** 78`) | ❌ | ❌ | — | ❌ | Renvoyé avec le challenge |
| Mot de passe oublié par e-mail | ✅ Lien de 30 min, jeton SHA-256, réponse générique. ⚠️ Répond 503 si le SMTP est absent | `forgot-password` + `reset-password` | — | 🟡 | Passer à 15 min et toujours renvoyer une réponse identique |
| Mot de passe oublié par SMS | ❌ | ❌ | Oui (responsable) | ❌ | Code `PASSWORD_RESET` (décision n°4 pour les trésoriers) |
| Invalidation des sessions après réinitialisation | ❌ **Les sessions ouvertes restent valides** | — | — | ❌ | Révoquer tous les refresh tokens et incrémenter une `token_version` |
| Notification après réinitialisation | ❌ | — | — | ❌ | SMS et/ou e-mail |
| Table `otp_codes` | ❌ | — | — | ❌ | Migration avec les types `LOGIN`, `PHONE_VERIFY`, `EMAIL_VERIFY`, `PASSWORD_RESET` |
| Limitation de débit (Bucket4j) | ❌ | — | — | ❌ | Par adresse IP et par identifiant |
| Verrouillage après 5 échecs (15 min) | ❌ | — | — | ❌ | Colonnes `failed_login_count` et `locked_until` |
| Rotation et révocation du refresh token | Sans objet | — | — | ❌ | Table `refresh_tokens` (empreinte, famille, `revoked_at`), détection de réutilisation |
| Déconnexion / tous les appareils | ✅ Déconnexion simple. ❌ Tous les appareils | Déconnexion simple | Profil : déconnexion | 🟡 | `POST /logout` et `POST /logout-all` |
| CORS | `@CrossOrigin` dispersés, sans effet (pas de `http.cors()`) | Proxy de développement | — | 🟡 | Un seul `CorsConfigurationSource`, origines lues dans l'environnement |
| Secrets | ✅ Variables d'environnement, `.env` ignoré par git et absent de l'historique | — | — | ✅ | Ajouter `JWT_SECRET`, les clés SMS et e-mail, et un `.env.example` |
| Journal des événements d'authentification | ❌ (`admin_audit_log` ne trace que l'admin) | — | Journal d'audit | ❌ | Table `auth_audit_log` sans aucun secret |
| Base Supabase exposée | ❌ **Critique** (§0) | — | — | ❌ | V14 + réglage dans le tableau de bord |
| Création d'un trésorier | Invitation **par e-mail** : jeton de 7 jours renvoyé au responsable dans la réponse | `login.component` (`?invitation=`) | **Par téléphone + mot de passe initial** | 🟡 | Aligner sur la maquette (décision n°4) |
| Première connexion et changement forcé du mot de passe | ❌ | ❌ | Oui | ❌ | Indicateur `must_change_password` |
| Changement de mot de passe une fois connecté | ❌ | ❌ | Profil | ❌ | `POST /api/auth/password/change` |
| Garde d'authentification Angular | ❌ (`App` appelle `me()` au démarrage) | — | — | ❌ | `authGuard` + routes `/admin` et `/app` |
| Intercepteur ajoutant le token et rafraîchissant sur 401 | ❌ (seul `associationInterceptor` existe) | — | — | ❌ | `authInterceptor` avec un seul rafraîchissement partagé entre requêtes |
| Champ OTP en 6 cases, collage, compte à rebours | ❌ | ❌ | Absent | ❌ | Composant `otp-input` |
| Indicateur de force du mot de passe | ❌ | ❌ | Oui | ❌ | Composant `password-strength` |
| Promotion d'un admin | ✅ Option CLI `--bereta.admin.promote-email` avec audit | — | — | ✅ | Conserver, accepter aussi un téléphone |
| Compte initial (bootstrap) | ✅ Variables d'environnement | — | — | ✅ | Ajouter `BOOTSTRAP_USER_PHONE` |

---

## 3. Autres failles de sécurité

| Gravité | Problème | Où |
|---|---|---|
| 🔴 Critique | Base Supabase ouverte au rôle `anon` en lecture et en écriture | §0 |
| 🔴 Haute | Une réinitialisation du mot de passe **ne coupe pas les sessions** déjà ouvertes, y compris celles d'un éventuel attaquant | `PasswordResetService#reset`, `AccountStatusFilter` |
| 🔴 Haute | Aucune limitation de débit ni aucun verrouillage, avec un mot de passe de 6 caractères seulement | `SecurityConfiguration` |
| 🟠 Moyenne | Cookie de session sans l'attribut `Secure` | `application.properties` |
| 🟠 Moyenne | Le jeton d'invitation transite en clair dans la réponse donnée au responsable | `TreasurerInvitationService#create` |
| 🟠 Moyenne | Énumération des comptes possible via le 409 de l'inscription, de l'invitation et de l'affectation d'un trésorier. La mission impose un message clair à l'inscription ; le risque sera atténué par la limitation de débit | `UserRegistrationService`, `FundAccessService` |
| 🟡 Faible | « Mot de passe oublié » répond 503 quand le SMTP n'est pas configuré, ce qui révèle la configuration du serveur | `PasswordResetService#requestReset` |
| 🟡 Faible | L'application se connecte avec le compte `postgres` | `.env` |

## 4. Code mort et éléments à nettoyer

Rien ne sera supprimé sans votre accord. Les éléments hors du périmètre de l'authentification sont seulement signalés.

| Élément | Constat | Proposition |
|---|---|---|
| `bereta-backend/bin/`, `bereta-backend/target/` | Fichiers `.class` compilés versionnés dans git | Les retirer de l'index (`git rm --cached`) et les ajouter au `.gitignore`. Les fichiers restent sur le disque |
| `bereta-backend/Task` | Fichier vide | À supprimer (à confirmer) |
| `MemberRepository.assignUnownedMembersTo` | Requête native qui ne filtre pas par association, appelée nulle part | À supprimer plus tard (hors authentification) |
| `MemberRepository.findAllByOrderByLastName…`, `findAllByCreatedById…` | Appelées nulle part | Idem |
| `BonjourController` + son test | Endpoint de démonstration | Idem |
| `AdminComponent`, `adminGuard` | Écrits mais déclarés dans aucune route | **À brancher**, pas à supprimer |
| `RegisteredAccountLoginError` (front) | Devient inutile une fois le parcours d'inscription revu | Remplacé pendant la refonte |
| `TreasurerInvitationService`, `fund_invitations`, `?invitation=` | Remplacés si la décision n°4 suit la maquette | Désactivés et conservés jusqu'à validation. La table est gardée (aucune suppression) |

## 5. Incohérences entre les maquettes, la mission et le code

| Sujet | Maquettes | Mission | Code actuel |
|---|---|---|---|
| E-mail à l'inscription | **Facultatif** | Obligatoire **et** vérifié | Obligatoire, non vérifié |
| Politique de mot de passe | 8 caractères + 1 chiffre, « faciles à retenir » | 8 caractères + majuscule, minuscule, chiffre et caractère spécial | 6 caractères |
| Mot de passe oublié du trésorier | Passe par le responsable | Code SMS ou e-mail pour tout le monde | E-mail pour tout le monde |
| Compte du trésorier | Créé par le responsable avec un mot de passe provisoire | — | Invitation par e-mail |
| Double authentification | « Activée » dans le profil (suggère une option) | OTP SMS **à chaque connexion** | Aucune |
| Session | — | JWT access + refresh | Session serveur + CSRF |

**À propos des JWT.** L'architecture actuelle (session côté serveur, cookie `HttpOnly`, CSRF) est déjà sûre et facile à révoquer. Le passage aux JWT, demandé par la mission, se fera ainsi :
- l'access token (15 min) est gardé **en mémoire** dans Angular, jamais dans `localStorage` ;
- le refresh token (7 jours) est envoyé dans un cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` ;
- l'access token est signé en HS256 avec un secret `JWT_SECRET` lu dans l'environnement ;
- l'API devient sans état (stateless), et le CSRF n'est plus nécessaire que sur `/api/auth/refresh` et `/logout`, protégés par `SameSite=Strict` et un en-tête obligatoire.

Les trois clients existants qui reposent sur la session (application, admin, tests) seront migrés.

**Comptes sans téléphone.** Les 2 comptes existants n'ont pas de numéro. À leur prochaine connexion, ils devront ajouter puis vérifier un téléphone avant de recevoir un token.

---

## 6. Décisions validées (04/11/2026)

| Sujet | Décision |
|---|---|
| Supabase | Migration V14 en premier : `REVOKE` des droits `anon` et `authenticated`, RLS activé sans politique. L'API de données est aussi à désactiver dans le tableau de bord |
| E-mail | **Facultatif**, conformément à la maquette. S'il est saisi, il doit être vérifié. Le compte est activé dès que le téléphone est vérifié |
| Mot de passe | **Règle de la maquette** : 8 caractères minimum, au plus 72 octets, au moins 1 chiffre |
| Trésoriers | **Maquette, avec le SMS en secours** : création par téléphone avec un mot de passe provisoire, changement forcé à la première connexion. Réinitialisation possible par le responsable **ou** par un code SMS. L'invitation par lien est désactivée, sa table est conservée |
| Connexion | OTP SMS **à chaque connexion**, comme le demande la mission |
| Session | JWT (access token de 15 min en mémoire, refresh token de 7 jours en cookie `HttpOnly`), comme le demande la mission |

## 7. Plan de travail (un commit par bloc fonctionnel, sur une branche dédiée)

0. Créer la branche `feat/auth-complete`. Faire un commit d'état initial avec le travail existant non commité, et retirer `bin/` et `target/` de l'index.
1. Ajouter la migration **V14** qui verrouille Supabase (après votre accord).
2. Modèle de données : migration V15 (téléphone, statut, vérifications, verrouillage, `must_change_password`, `token_version`, `otp_codes`, `refresh_tokens`, `auth_audit_log`). Ajouter `PhoneNumberService`, `PasswordPolicy`, ainsi que `SmsSender` et `EmailSender` avec leurs versions de test (mock).
3. `OtpService`, nouveau parcours d'inscription, vérification de l'e-mail et du téléphone.
4. Connexion en 2 étapes, JWT et refresh avec rotation, déconnexion (simple et tous les appareils), verrouillage, limitation de débit, journal d'audit.
5. Mot de passe oublié (e-mail et SMS), invalidation des sessions, notifications, changement de mot de passe, première connexion du trésorier.
6. Front : service, intercepteur, garde, routes et pages (OTP en 6 cases, jauge de force).
7. Tests unitaires et d'intégration, compilation, documentation finale (endpoints et variables d'environnement).
