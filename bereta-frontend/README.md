# BERETA — frontend

Interface Angular de l’application de gestion des cotisations associatives BERETA.

## Prérequis

- Node.js 20.19 ou une version compatible avec Angular 21
- npm

## Installer et démarrer

Depuis ce dossier :

```bash
npm install
npm start
```

L’application de développement est disponible sur <http://localhost:4200/>.

## Compiler

```bash
npm run build
```

Les fichiers optimisés sont générés dans `dist/bereta/`.

## Développement local

Démarrer le backend sur le port `8081`, puis lancer Angular :

```bash
npm start
```

Le proxy Angular (`proxy.conf.json`) transmet les appels `/api` vers le backend. Si Spring Boot écoute sur un autre port, adapter la cible du proxy.

## État actuel

Le projet utilise Angular standalone, TypeScript strict, le routage Angular et SCSS. Le tableau de bord reste responsive mobile-first et ses indicateurs financiers sont encore des exemples. Les membres sont chargés depuis le backend ; on peut les rechercher, ajouter et modifier. **Gérer les caisses** permet de créer les caisses principales, caisses locales et campagnes. Le bouton **Inscrire** associe un membre à une campagne et à la caisse locale correspondante.

Les prochaines fonctionnalités seront ajoutées progressivement, en commençant par le parcours de création et de consultation des membres.
