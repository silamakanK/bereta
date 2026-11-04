# Analyse du projet BERETA

*Analyse statique du dépôt au 03/11/2026 (branche `main`, avec le travail non commité). Aucun fichier source n'a été modifié.*

> ⚠️ **Le cahier des charges `docs/cahier_charges_bereta.md` est absent du dépôt.** L'analyse s'appuie sur le résumé du contexte fourni.

---

## 1. Résumé de l'état du projet

1. Le backend Spring Boot 3.5 / Java 21 (≈ 110 classes, 13 migrations Flyway) est **nettement plus avancé que le frontend**.
2. **Environ 80 % du code n'est pas commité** (V5→V13, admin, associations, paiements, remises) : risque de perte immédiat.
3. Le modèle **multi-association est solide** : `association_id` partout, clés étrangères composites `(id, association_id)`, en-tête `X-Association-Id` vérifié par un intercepteur.
4. Ce qui marche : comptes et sessions (CSRF), associations, caisses principales et locales, invitations de trésoriers, membres, campagnes, inscriptions, paiements partiels sans trop-perçu, remises trésorier → responsable.
5. Ce qui manque : dépenses, import des cahiers, exports et bilans, réaffectation des membres, annulation des paiements, identifiant téléphone.
6. Le cahier des charges n'est pas suivi sur trois points : la caisse principale n'est **pas créée automatiquement** avec l'association, le trésorier est identifié **par e-mail** et non par téléphone, et les actions métier de l'admin **ne sont pas tracées**.
7. Le frontend Angular 21 (standalone, signals) repose sur **un composant racine monolithique** (`app.ts`, 564 lignes). Le routage est minimal et l'écran admin existe mais **n'est branché sur aucune route**.
8. Bug bloquant probable : les pipes `date` et `currency` utilisent `'fr-FR'` alors que cette locale n'est jamais enregistrée, ce qui ferait planter le tableau de bord.
9. Tests : 27 tests d'intégration backend MockMvc sur H2, **tous verts**, et 1 seul test unitaire côté frontend.
10. Il n'existe **ni Docker, ni CI/CD, ni profils dev/prod**. Les dossiers `bin/` et `target/` (fichiers `.class`) sont versionnés.

---

## 2. Fonctionnalités du cahier des charges → statut

| Fonctionnalité | Statut | Commentaire | Fichiers principaux |
|---|---|---|---|
| Inscription libre du responsable | 🟡 | Fonctionne, mais le compte reçoit le rôle global `TRESORIER`. Le rôle réel est déduit des adhésions. | `auth/UserRegistrationService.java`, `auth/register.component.ts` |
| Création d'association | ✅ | Le créateur devient automatiquement responsable. | `associations/AssociationAccessService.java#create` |
| Caisse principale auto (même transaction) | ❌ | Création manuelle via `POST /api/caisses-principales`. Rien n'empêche d'en créer plusieurs par association. | `funds/PrimaryFundService.java` |
| Caisses locales | 🟡 | Création et lecture OK. Pas de renommage ni de désactivation. « 1 trésorier par caisse » n'est pas imposé (unicité seulement sur `(user, caisse)`). | `funds/PrimaryFundService.java`, `V12` |
| Compte trésorier créé par le responsable (identifiant = téléphone) | 🟡 | Invitation par e-mail avec un jeton valable 7 jours, ou affectation d'un compte existant. Le téléphone n'est pas géré. | `auth/TreasurerInvitationService.java`, `funds/FundAccessService.java#assignTreasurer` |
| Trésorier ne voit que sa caisse | ✅ | Testé. Bug si le trésorier a plusieurs caisses locales (voir §4). | `funds/FundAccessService.java`, `FundAccessControllerTest` |
| Administrateur (supervision, traçabilité) | 🟡 | API `/api/admin/**` avec audit et promotion par l'option CLI `--bereta.admin.promote-email`. Pas de lien avec Supabase. Écran non routé. Actions métier non tracées. | `admin/*`, `admin/admin.component.ts` |
| Membres (création, modification) | ✅ | | `members/*` |
| Désactivation d'un membre | ❌ | Ni colonne ni endpoint. | — |
| Réaffectation d'un membre sans toucher l'historique | ❌ | `created_in_local_fund_id` est figé. Pas d'historique d'affectation. | `members/MemberEntity.java` |
| Campagnes annuelles / événementielles | 🟡 | Création et lecture OK. Pas de modification, de clôture ni d'archivage. | `funds/Campaign*` |
| Inscription d'un membre à une campagne | ✅ | Unicité `(membre, campagne)`. | `funds/MemberCampaign*` |
| Paiements partiels | ✅ | Verrou pessimiste sur l'inscription. | `funds/PaymentService.java` |
| Statuts à payer / partiel / payé / en retard | 🟡 | Calculés côté API (`PaymentEnrollmentResponse`) mais peu exploités dans l'interface. « En retard » masque « partiel ». | `funds/PaymentEnrollmentResponse.java` |
| Trop-perçu refusé | ✅ | Contrôle applicatif uniquement, sans contrainte en base. | `PaymentService#record` |
| Annulation traçable d'un paiement | ❌ | Aucun moyen de corriger une erreur de saisie. | — |
| Remises trésorier → responsable (hors cotisations) | ✅ | Déclarer, confirmer, annuler avec motif. Contrainte CHECK d'audit en base. | `funds/CashRemittance*`, `V9` |
| Dépenses | ❌ | | — |
| Import photos / PDF / Excel des cahiers + validation | ❌ | Le bouton « Importer mon cahier » ne fait rien. | `app.html:267` |
| Exports PDF / Excel, bilans | ❌ | Le bouton « Consulter mon bilan » ne fait rien. Seules existent des synthèses JSON. | `app.html:272`, `PaymentService#summary` |
| Séparation stricte des associations | ✅ | Clés étrangères composites, intercepteur, test d'isolation. | `V12`, `AssociationContextInterceptor.java`, `AssociationIsolationControllerTest` |
| Aucune suppression physique | 🟡 | Aucun `DELETE` réel (`DELETE /acces` ne fait que désactiver). En revanche, ni désactivation ni annulation pour les membres et les paiements. | — |
| Archivage d'association | 🟡 | Réservé à l'admin. Une association archivée devient totalement inaccessible, même en lecture. | `admin/AdminService.java` |
| Mot de passe oublié | ✅ | Hors périmètre initial. Jeton SHA-256 valable 30 min, envoi SMTP optionnel. | `auth/PasswordResetService.java` |
| Docker, CI/CD, Kubernetes | ❌ | | — |

---

## 3. Problèmes de sécurité (par gravité)

Points positifs constatés : BCrypt (coût 12), CSRF par cookie avec gestionnaire SPA, session `HttpOnly` + `SameSite=Lax`, jetons stockés hachés, filtre `AccountStatusFilter` qui coupe la session d'un compte désactivé, filtrage systématique par `association_id`. Le fichier `.env` est ignoré et n'apparaît dans aucun commit.

### 🔴 Haute
1. **Actions métier de l'admin non tracées.** `isAssociationManager()` et `requireAccess()` donnent tous les droits à un `ADMIN` dans n'importe quelle association (paiements, confirmation de remises, membres…), sans aucune écriture dans `admin_audit_log`. Cela contredit la règle « actions tracées ». Fichiers : `funds/FundAccessService.java`, `associations/AssociationAccessService.java`.
2. **Aucune limitation des tentatives** sur `/api/auth/login`, `/register`, `/password/forgot` et `/invitations/accept`, alors que le mot de passe minimum n'est que de 6 caractères. Le brute-force est donc trivial une fois l'application exposée.

### 🟠 Moyenne
3. **Ajout d'un co-responsable sans consentement** (`POST /api/associations/{id}/responsables`). N'importe quel responsable peut donner les pleins pouvoirs à n'importe quel compte, et rien ne permet de retirer un responsable. Même problème pour `assignTreasurer`.
4. **Énumération des comptes par e-mail** : les réponses 404 « Aucun compte actif… » et 409 « Un compte existe déjà » révèlent si une adresse est inscrite (ajout de responsable, affectation de trésorier, invitation).
5. **Cookie de session sans `Secure`** et aucune configuration HTTPS ni proxy (`server.forward-headers-strategy`). À corriger avant toute mise en ligne.
6. **L'admin peut modifier l'e-mail de n'importe quel compte** (`PUT /api/admin/users/{id}`), ce qui revient à prendre le compte via « mot de passe oublié ». L'action est tracée, mais elle mérite une confirmation ou une restriction.

### 🟡 Faible
7. Le jeton d'invitation est renvoyé en clair au responsable, qui doit le transmettre lui-même (aucun e-mail n'est envoyé). Il reste valable 7 jours.
8. Code mort dangereux : `MemberRepository.assignUnownedMembersTo` est une requête native qui ne filtre pas par association. Elle n'est pas appelée, mais il faut la supprimer.
9. Des annotations `@CrossOrigin("http://localhost:4200")` sont dispersées alors que `http.cors()` n'est pas configuré : elles sont inertes et sèment la confusion.
10. Le rôle global (`RESPONSABLE` / `TRESORIER`) est incohérent avec les rôles par association : l'inscription produit `TRESORIER`, l'amorçage produit `RESPONSABLE`. Cela crée un risque de mauvaise décision d'autorisation à l'avenir.
11. `/api/bonjour` est un endpoint de démonstration, toujours actif.

---

## 4. Dette technique et bugs repérés

### Bugs probables
| # | Problème | Impact | Où |
|---|---|---|---|
| B1 | Les pipes `date:…:'fr-FR'` et `currency:…:'fr-FR'` sont utilisés sans `registerLocaleData(localeFr)` ni `LOCALE_ID`. Angular lève alors l'erreur NG0701 au rendu. | Tableau de bord et admin cassés (à confirmer en lançant l'application) | `app.html`, `admin.component.html`, `main.ts` |
| B2 | `AdminComponent` et `adminGuard` ne sont déclarés dans aucune route. Après connexion, l'admin est envoyé vers `/admin`, ce qui renvoie vers `/` par la route joker. | Interface admin inaccessible | `app.routes.ts`, `login.component.ts:70` |
| B3 | Un trésorier rattaché à plusieurs caisses locales n'en voit qu'une : `membership.getLocalFund()` ne prend que la première adhésion. | Données manquantes | `PrimaryFundService#findLocalFunds`, `CashRemittanceService#list/summary` |
| B4 | Les messages d'erreur du backend sont perdus : `server.error.include-message` n'est pas configuré et le front traduit seulement le code HTTP. Un trop-perçu affiche par exemple « Certains champs sont invalides ». | Mauvaise expérience pour un public peu technophile | `application.properties`, `app.ts:307`, `fund-setup.component.ts:373` |
| B5 | La migration `V12` ajoute des colonnes `association_id NOT NULL` sans remplir les lignes existantes. | Échec de migration si la base Supabase contenait déjà des données V1–V11 | `V12__add_association_scope.sql` |
| B6 | `FundAccessService.requireAccess` renvoie `null` pour un responsable, et les appelants doivent y penser. | Risque de `NullPointerException` | `FundAccessService.java`, `CashRemittanceService#list` |
| B7 | Le statut `EN_RETARD` masque `PARTIELLEMENT_PAYE`. | Information perdue | `PaymentEnrollmentResponse.java` |
| B8 | Une association archivée n'est plus visible du tout, même par ses responsables. | Consultation de l'historique impossible | `AssociationAccessService#requireAccess` |
| B9 | Le libellé « Développement de Niogoméra » est codé en dur. Les boutons notifications, import et bilan ne font rien. | Interface trompeuse | `app.ts:47`, `app.html:72,267,272` |
| B10 | La devise EUR est codée en dur, côté front comme dans les messages du backend (« € »). | À valider (FCFA ?) | `app.html`, `PaymentService`, `CashRemittanceService` |

### Dette technique
- **Git** : l'essentiel du travail n'est pas commité. Les dossiers `bin/` et `target/` (fichiers `.class` et `application.properties` copié) sont versionnés. Le fichier `bereta-backend/Task` est vide. Le `node_modules/` racine n'est couvert par aucun `.gitignore` racine.
- **Backend, architecture** : le découpage par fonctionnalité est correct, mais le package `funds` est un fourre-tout (caisses, campagnes, paiements, remises, accès). Les DTO sont des `record` avec une méthode `from()` statique, sans mappers. `ResponseStatusException` est utilisé partout, sans `@RestControllerAdvice` ni `ProblemDetail`. La logique d'autorisation est éparpillée dans les services au lieu d'être centralisée (par exemple dans un `@PreAuthorize` avec un bean).
- **Performance** : requêtes N+1. `hasLocalAccess` et `isAssociationManager` sont appelés pour chaque ligne (2 à 4 requêtes par ligne) dans l'historique et les synthèses. `AccountStatusFilter` lit la base à chaque requête. Aucune pagination côté métier.
- **Base de données** : pas de contrainte contre le trop-perçu ni contre « 1 caisse principale par association ». L'audit métier se limite aux remises.
- **Tests** : H2 n'est pas PostgreSQL (Testcontainers recommandé). Rien ne teste l'admin, la réinitialisation réussie, les associations archivées, ni un trésorier sur plusieurs caisses. Le front n'a qu'un seul spec (`register-validation.spec.ts`).
- **Frontend** : `app.ts` et `app.html` sont un « god component » qui mélange tableau de bord, membres, inscriptions et paiements. Pas de garde d'authentification ni d'intercepteur d'erreur (une session expirée ne renvoie pas vers la connexion). `window.confirm` est utilisé dans l'admin. Les templates sont des lignes HTML très longues, difficiles à maintenir.
- **Mobile** : des media queries existent (600, 360 px) et les libellés sont en français simple, ce qui est positif. Il faudrait encore tester sur un vrai téléphone, vérifier que les zones tactiles font au moins 44 px, et envisager un mode hors ligne ou une PWA, utile sur le terrain.
- **Transverse** : pas de profils `dev` / `prod`, pas d'actuator ni de healthcheck, pas de Dockerfile, pas de `docker-compose`, pas de pipeline CI, pas de `.env.example`.

---

## 5. Feuille de route priorisée (petites étapes livrables)

| # | Étape | Livrable vérifiable |
|---|---|---|
| 0 | **Sécuriser le travail** : commit sur une branche, suppression de `bin/` et `target/` de l'index, `.gitignore` racine | Dépôt propre, CI possible |
| 1 | Corriger B1 (locale fr) et B2 (route `/admin` + `adminGuard`), puis ajouter une garde d'authentification et un intercepteur 401 | Application utilisable pour les 3 rôles |
| 2 | `@RestControllerAdvice` + `ProblemDetail` avec messages en français, affichés tels quels par le front (B4) | Erreurs compréhensibles |
| 3 | Sécurité : audit des actions métier de l'admin, limitation des tentatives de connexion, cookie `Secure`, profil `prod` | Fermeture des problèmes de sécurité 🔴 et 🟠 |
| 4 | Création automatique de la caisse principale dans la transaction de l'association, avec une contrainte d'unicité | Conforme au cahier des charges |
| 5 | Annulation traçable des paiements (statut, motif, auteur) et désactivation des membres | Règle « aucune suppression » respectée |
| 6 | Corriger B3 (trésorier multi-caisses) ou imposer 1 trésorier ↔ 1 caisse, selon la décision prise (§6) | — |
| 7 | Réaffectation des membres via une table d'historique d'affectations | Anciens encaissements intacts |
| 8 | Identifiant trésorier par téléphone (création directe par le responsable) | Conforme au cahier des charges |
| 9 | Dépenses (modèle, API, écran) | — |
| 10 | Bilans, puis exports PDF et Excel | Remplacement du bouton factice |
| 11 | Import des cahiers : téléversement, tables de brouillon, écran de validation obligatoire | — |
| 12 | Dockerfile backend et frontend, `docker-compose`, CI (build + tests + Testcontainers) | Pipeline vert |
| 13 | Refonte du front : découpage d'`app.ts` en routes par rôle | Maintenabilité |
| 14 | Kubernetes (manifests ou Helm) et secrets gérés | — |

---

## 6. Questions ouvertes à trancher

1. **Cahier des charges** : pouvez-vous ajouter `docs/cahier_charges_bereta.md` ?
2. **Devise** : euro, FCFA, ou plusieurs selon l'association ?
3. **Trésorier identifié par téléphone** : faut-il supprimer complètement l'e-mail ? Comment se fait alors la réinitialisation du mot de passe (SMS, réinitialisation par le responsable) ?
4. **Cardinalités** : une seule caisse principale par association ? Un seul trésorier par caisse locale ? Un trésorier peut-il tenir plusieurs caisses ou plusieurs associations ?
5. **Co-responsables** : sont-ils autorisés ? Faut-il une acceptation explicite et une possibilité de retrait ?
6. **Administrateur « via Supabase »** : faut-il utiliser Supabase Auth, ou garder l'authentification Spring actuelle avec une promotion par commande ? L'admin a-t-il le droit d'écrire dans les associations, ou seulement de les consulter ?
7. **Association archivée** : consultation en lecture seule par ses responsables, ou invisible ?
8. **Retard** : un membre en retard et partiellement payé doit-il afficher les deux informations ? Faut-il des relances ?
9. **Dépenses** : sont-elles rattachées à la caisse locale ou à la principale ? Qui les valide ? Peuvent-elles rendre le solde négatif ?
10. **Import des cahiers** : où stocker les fichiers (Supabase Storage) ? Faut-il un OCR automatique ou une saisie assistée ? Qui valide ?
11. **Inscription aux campagnes** : faut-il inscrire automatiquement tous les membres actifs à une campagne annuelle ?
12. **Base Supabase existante** : contient-elle des données antérieures à la migration V12 (voir B5) ?

---

## Annexe : endpoints REST existants

Tous les endpoints exigent une session, sauf indication contraire. Ceux qui ne sont ni `/api/auth`, ni `/api/admin`, ni `/api/associations` exigent en plus l'en-tête `X-Association-Id`.

| Méthode | URL | Droit requis |
|---|---|---|
| GET | `/api/auth/csrf` | public |
| POST | `/api/auth/login`, `/register`, `/password/forgot`, `/password/reset`, `/invitations/accept` | public |
| POST | `/api/auth/logout` | — |
| GET | `/api/auth/me` | connecté |
| GET / POST | `/api/associations` | connecté (POST : devient responsable) |
| GET / POST | `/api/associations/{id}/responsables` | responsable |
| GET / POST | `/api/caisses-principales` | lecture : membre de l'association ; création : responsable |
| GET | `/api/caisses-principales/{id}` | accès à la caisse |
| GET / POST | `/api/caisses-principales/{id}/caisses-locales` | lecture : accès ; création : responsable |
| GET | `/api/caisses-principales/{id}/caisses-locales/{localId}` | accès à la caisse locale |
| GET / POST | `/api/caisses-principales/{id}/acces` | responsable |
| POST | `/api/caisses-principales/{id}/acces/invitations` | responsable |
| DELETE | `/api/caisses-principales/{id}/acces/{membershipId}` | responsable (désactivation) |
| GET / POST | `/api/campagnes`, GET `/api/campagnes/{id}` | lecture : accès ; création : responsable |
| GET / POST / PUT | `/api/membres`, `/api/membres/{id}` | accès à la caisse locale du membre |
| GET / POST | `/api/membres/{id}/inscriptions` | accès à la caisse locale |
| GET | `/api/paiements`, `/inscriptions-a-payer`, `/synthese`, `/membres/{id}` | périmètre des caisses locales accessibles |
| POST | `/api/paiements/inscriptions/{enrollmentId}` | accès à la caisse locale |
| POST | `/api/caisses-locales/{id}/remises` | trésorier de la caisse uniquement |
| GET | `/api/caisses-locales/{id}/remises/solde` | accès à la caisse locale |
| GET | `/api/caisses-principales/{id}/remises`, `/remises/synthese` | accès à la caisse |
| POST | `/api/caisses-principales/{id}/remises/{rid}/confirmer`, `/annuler` | responsable |
| GET / POST / PUT / PATCH | `/api/admin/**` (overview, users, associations, archive, responsables, audit) | `ROLE_ADMIN` |
| GET | `/api/bonjour` | connecté (démo) |
