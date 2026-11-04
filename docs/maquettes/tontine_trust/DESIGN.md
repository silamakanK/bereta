---
name: Tontine & Trust
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#404940'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#707a6f'
  outline-variant: '#bfc9bd'
  surface-tint: '#1f6c3a'
  primary: '#004c22'
  on-primary: '#ffffff'
  primary-container: '#166534'
  on-primary-container: '#93e0a2'
  inverse-primary: '#8bd79b'
  secondary: '#0051d5'
  on-secondary: '#ffffff'
  secondary-container: '#316bf3'
  on-secondary-container: '#fefcff'
  tertiary: '#653400'
  on-tertiary: '#ffffff'
  tertiary-container: '#874700'
  on-tertiary-container: '#ffc292'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#a6f4b5'
  primary-fixed-dim: '#8bd79b'
  on-primary-fixed: '#00210b'
  on-primary-fixed-variant: '#005226'
  secondary-fixed: '#dbe1ff'
  secondary-fixed-dim: '#b4c5ff'
  on-secondary-fixed: '#00174b'
  on-secondary-fixed-variant: '#003ea8'
  tertiary-fixed: '#ffdcc3'
  tertiary-fixed-dim: '#ffb77d'
  on-tertiary-fixed: '#2f1500'
  on-tertiary-fixed-variant: '#6e3900'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-bold:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 26px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  amount-display:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 44px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 2rem
  margin: 1rem
  margin-tablet: 2rem
  margin-desktop: 3rem
  space-xs: 0.375rem
  space-sm: 0.75rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style

Ce système de design incarne la solidarité, la rigueur communautaire et l'intégrité financière. Conçu pour digitaliser et sécuriser les cotisations associatives et tontines traditionnelles, il s'adresse à un public multigénérationnel, comprenant des membres parfois éloignés des codes numériques complexes.

L'expérience visuelle allie **Minimalisme Fonctionnel** et **Chaleur Tactile**. L'interface privilégie une lisibilité sans concession, une hiérarchie immédiate et un sentiment d'apaisement : chaque action financière doit inspirer une confiance absolue sans provoquer d'anxiété technique. Les éléments interactifs sont francs, spacieux et accueillants, évitant tout artifice visuel superflu au profit d'un guidage explicite pas à pas.

## Colors

La palette s'articule autour de teintes terriennes et statutaires franches, garantissant un contraste conforme aux normes d'accessibilité WCAG AAA pour les textes critiques.

- **Primaire (`#166534`)** : Vert forêt profond, emblème de prospérité, de stabilité et de responsabilité financière. Utilisé pour l'identité d'en-tête, les actions de validation fondamentales et la navigation principale.
- **Secondaire (`#2563EB`)** : Bleu d'information réassurance, réservé aux opérations de virement, à la caisse centrale et aux flux bancaires.
- **Tertiaire (`#D97706`)** : Ambre solaire, dédié exclusivement aux cotisations partielles ou aux versements en attente de validation.
- **Neutre & Surfaces** : 
  - Toile de fond globale : `#F8FAFC` (ardoise ultra-claire pour reposer la vue).
  - Cartes et conteneurs : `#FFFFFF` pur pour détacher les informations.
  - Texte principal : `#0F172A` (ardoise sombre, jamais de noir pur, pour une lecture durable sans fatigue).
  - Texte secondaire : `#475569`.
  - Bordures structurelles : `#E2E8F0`.

### Règle d'or sémantique
Aucun statut ne doit être communiqué uniquement par la couleur :
- **Soldé / Réglé** : Vert émeraude (`#15803D`) avec fond teinté (`#DCFCE7`).
- **Partiel / Échelonné** : Ambre (`#D97706`) avec fond teinté (`#FEF3C7`).
- **Retard / Impayé** : Rouge rubis (`#DC2626`) avec fond teinté (`#FEE2E2`).
- **Information / Caisse** : Bleu (`#2563EB`) avec fond teinté (`#DBEAFE`).

## Typography

Le choix de **Plus Jakarta Sans** apporte de la rondeur, des contreformes ouvertes et une clarté optique remarquable sur écrans mobiles d'entrée et milieu de gamme.

- **Taille plancher** : Le corps de texte ne descend jamais sous **16px** sur mobile pour éliminer tout effort d'accommodation visuelle.
- **Interlignage généreux** : Fixé entre 150% et 160% de la taille du caractère pour séparer distinctement les lignes de montants, de dates et de noms de participants.
- **Différenciation par le poids** : Emploi marqué des graisses SemiBold (`600`) et Bold (`700`) face au Regular (`400`) pour hiérarchiser instantanément le nom du cotisant, la somme due et l'échéance sans surcharger l'écran.
- **Affichage monétaire (`amount-display`)** : Dédié au solde global et aux montants de versement avec une graisse `800` affirmée pour une transparence immédiate.

## Layout & Spacing

Le système applique une approche **mobile-first** avec un rythme vertical aéré fondé sur un pas de 4px/8px.

### Modèle de disposition
- **Mobile (< 768px)** : Flux à colonne unique centré sur une largeur de lecture naturelle. Les marges externes de `1rem` garantissent l'utilisation optimale de la largeur d'écran sans effleurement accidentel des bords.
- **Tablette (768px - 1024px)** : Grille fluide à 8 colonnes avec un espacement inter-colonnes de `1.5rem`. Les fiches récapitulatives passent en deux colonnes de synthèse.
- **Desktop (> 1024px)** : Grille à 12 colonnes contenue dans un conteneur maximal de `1200px` centré, offrant une vue partagée (liste des membres à gauche, détail des transactions et journal d'audit à droite).

### Rythme et respiration
Les blocs de cotisations disposent d'un espacement vertical minimal de `space-md` (`1rem`) entre les cartes afin d'éviter tout chevauchement perceptif ou mauvaise sélection du doigt.

## Elevation & Depth

L'élévation repose sur une combinaison de **bordures douces** et d'**ombres feutrées** pour maintenir un contraste net sous forte luminosité ambiante (extérieur, soleil).

- **Niveau 0 (Toile)** : Fond `#F8FAFC`, neutre, sans ombre.
- **Niveau 1 (Cartes de cotisation et blocs récapitulatifs)** : Surface `#FFFFFF`, bordure pleine de 1px en `#E2E8F0`, rehaussée d'une ombre discrète `0 1px 3px 0 rgba(15, 23, 42, 0.05)`.
- **Niveau 2 (Boutons flottants d'action rapide, fiches sélectionnées)** : Fond `#FFFFFF`, bordure `#CBD5E1`, ombre `0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -2px rgba(15, 23, 42, 0.04)`.
- **Niveau 3 (Modales de validation de versement, tiroirs de saisie)** : Fond `#FFFFFF`, ombre enveloppante `0 20px 25px -5px rgba(15, 23, 42, 0.12)`, séparée du reste par un rideau d'arrière-plan semi-transparent `#0F172A` à 40% d'opacité.

## Shapes

La géométrie privilégie un rayon d'arrondi de niveau 2 :
- **Composants interactifs (boutons, champs, alertes)** : Arrondi de `0.5rem` (8px). Cela offre un aspect amical et accessible tout en conservant la structure rassurante d'un grand registre comptable.
- **Cartes et fiches membres (`rounded-lg`)** : Rayon de `1rem` (16px), délimitant franchement chaque dossier de cotisation.
- **Pastilles d'état et badges (`rounded-full`)** : Profils cylindriques complets (pilule) pour se distinguer immédiatement des boutons rectangulaires d'action.

## Components

### Boutons
- **Dimensions tactiles** : Hauteur minimale absolue de **52px** sur l'ensemble des cibles interactives principales (dépasse les recommandations standards de 48px pour faciliter l'usage à une main ou avec de grands doigts).
- **Structure** : Toujours composés d'un pictogramme explicite à gauche (ex. : coche de validation, flèche de virement) suivi d'un libellé textuel clair en infinitif ou impératif direct ("Enregistrer le paiement", "Télécharger le reçu").
- **Primaire** : Fond `#166534`, texte blanc `#FFFFFF`, icône blanche.
- **Secondaire / Annulation** : Fond blanc, bordure 2px `#E2E8F0`, texte `#0F172A`.

### Badges d'état (Statuts de cotisation)
Chaque badge associe obligatoirement **trois repères simultanés** :
1. Une pastille circulaire de couleur pleine de 8px.
2. Une icône explicite (ex. : bouclier coché pour réglé, sablier pour partiel, triangle d'alerte pour retard).
3. Un libellé clair avec mention temporelle (ex. : *"En retard (Échéance : 05 Mars)"*).
- **Soldé** : Texte `#15803D`, fond `#DCFCE7`, bordure `#BBF7D0`.
- **Partiel** : Texte `#B45309`, fond `#FEF3C7`, bordure `#FDE68A`.
- **Retard** : Texte `#B91C1C`, fond `#FEE2E2`, bordure `#FECACA`.

### Champs de saisie (Inputs)
- Hauteur de **52px**, typographie intérieure à **16px** (évite le zoom automatique sur iOS).
- Bordure de 1.5px en `#CBD5E1`, fond `#FFFFFF`.
- État actif (`focus`) : Contour renforcé de 2px en `#166534` doublé d'un halo doux d'espacement.
- Les labels sont systématiquement extérieurs, persistants, au-dessus du champ en graisse SemiBold (pas de placeholder faisant office de label).

### Cartes de membre & Cotisation
- Cartes blanches sur fond gris clair, séparées par `16px`.
- Découpage interne en 3 niveaux visuels :
  1. En-tête : Avatar avec initiales contrastées + Nom complet en `body-bold`.
  2. Corps : Montant versé / Montant total en chiffres grand format.
  3. Pied de carte : Badge d'état étendu sur toute la largeur avec date d'action.

### Listes de pointage
- Séparateurs horizontaux de 1px `#F1F5F9`.
- Lignes d'une hauteur minimale de **64px** avec déclencheur tactile couvrant toute la surface de la ligne pour ouvrir la modalité d'encaissement.