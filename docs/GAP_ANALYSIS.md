# Analyse d'écart — avant l'onglet Tasks

**Date :** 20 août 2026 · **Phase 5 du brief, v9** (`hobbitton-chat-server/docs/BRIEF.md`)

Le brief impose ce document **avant toute UI** : « `docs/GAP_ANALYSIS.md` avant
toute UI (endpoints memory/projects/skills vs client) ». L'idée est simple —
savoir ce que le serveur offre déjà et ce que le client sait déjà en faire,
avant de dessiner des écrans qui supposeraient l'un ou l'autre.

Ce qui suit compare trois surfaces serveur au client tel qu'il est aujourd'hui
(`e87a8d5`, v2026.08.2).

**Mis à jour pour le brief v9** (20 août) : Code et Tasks fusionnent en un seul
onglet, et l'application parle directement à l'API OpenCode. Les §2, §5 et §6 en
tiennent compte.

---

## 0. Le blocage — tranché et levé le 20 août

**L'application ne pouvait plus se connecter.** La phase 4 avait mis Authelia
devant `chat.hobbitton.at` en `two_factor`, et Authelia intercepte **toutes**
les requêtes de l'hôte, `/api/*` comprise. Or le client s'authentifie par
`POST /api/auth/login` puis porte un JWT LibreChat : ni navigateur, ni cookie
de session, rien dans sa pile réseau pour franchir un formulaire.

**Décision prise : `/api/*` est exempté d'Authelia** (`DECISIONS.md` du dépôt
serveur, D-024). Cette surface n'est pas anonyme — elle exige un mot de passe
et un second facteur, gérés par LibreChat, que le client sait présenter.

Mesuré après application :

| Requête | Résultat |
|---|---|
| `GET /` | `302` → portail — le SSO tient |
| `GET /api/config` | `200` — l'exemption tient |
| `GET /api/user` | `401` sans jeton — exempté ≠ ouvert |
| `POST /api/auth/login` | réponse identique à celle de LibreChat en direct |

**Ce que cette décision laisse à faire, et qui appartient à cette phase.** Le
second facteur de l'application **n'est pas celui d'Authelia** : c'est celui de
LibreChat, et il **n'est pas encore activé**. Tant qu'il ne l'est pas,
l'application n'exige qu'un mot de passe, et « SSO + 2FA partout » (§9.6) ne
tient que sur le web.

Le client implémente déjà tout le nécessaire — `api/auth/2fa/enable`,
`/confirm`, `/verify`, `/disable`, plus les codes de secours. Il s'agit donc
d'activer, pas de construire. À faire **avant** de considérer le critère
d'acceptation n°6 comme atteint, et de préférence avant le premier lancement sur
téléphone : une application qui se connecte avec un simple mot de passe prend
vite l'habitude de le faire.

## 1. Ce que le client sait déjà faire — le chat

L'onglet Chats reste **intact** (exigence du brief). L'inventaire ci-dessous
sert surtout à vérifier qu'on n'a rien à y reconstruire.

| Surface serveur | Client | Verdict |
|---|---|---|
| `api/memories` (CRUD + préférences) | `MemoriesApi` — 5 routes | couvert |
| `api/projects` (liste, CRUD, rattachement de conversation) | `ProjectsApi` — 5 routes | couvert |
| `api/skills` (CRUD, fichiers, import, actifs) | `SkillsApi` — 13 routes | couvert |
| `api/agents` (CRUD, duplication, révision) | `AgentsApi` — 9 routes | couvert |
| `api/mcp` (outils, serveurs, statut de connexion) | `McpApi` — 206 lignes | couvert |

**Rien à faire ici.** Le brief supposait des écarts sur memory/projects/skills ;
il n'y en a pas. Le client est en avance sur ce que le document anticipait.

⚠️ Une nuance qui compte pour la suite : `api/memories` est la mémoire **interne
de LibreChat**, qui est explicitement **désactivée** dans notre configuration
(`memory.disabled: true`) au profit du Memory MCP. Ces cinq routes sont donc
couvertes et **inutiles** chez nous. Le cerveau n'est atteint que par MCP,
c'est-à-dire par les outils que le modèle appelle pendant une conversation —
il n'y a pas, et il n'a pas à y avoir, d'écran « mémoire » dans l'application.

---

## 2. L'onglet Tasks — tout est à faire

Le brief v9 : un seul onglet pour ce qui était Tasks **et** Code. Une mission =
objectif + profil + connecteurs cochés + mode d'exécution, `autonome`
(ponctuelle ou cron) ou `interactif` (flux suivi, approbations). Liste unifiée,
et une vue de détail qui s'adapte au livrable.

**Côté client : rien.** Aucun module `feature/tasks`, aucun client du moteur
Agent, aucune référence à OpenCode. Vérifié par recherche sur l'ensemble des
sources.

**Côté serveur : tout existe et tourne.** Ce qu'il faut consommer, en direct
sur l'API de sessions — pas de façade (D-026) :

| Besoin de l'écran | Route du moteur Agent |
|---|---|
| liste des profils | `GET /agent` |
| créer une mission | `POST /session` puis `POST /session/{id}/prompt_async` |
| connecteurs cochés | règles `{permission, pattern, action}` à la création de session |
| suivi, mode autonome | `GET /session/{id}/message`, `GET /session/status` |
| flux, mode interactif | `GET /session/{id}/event` (SSE) |
| approbations | `POST /session/{id}/permissions/{permissionID}` |
| diff | `GET /session/{id}/diff`, `POST /session/{id}/revert` |
| livrables | l'API de sessions elle-même (D-027) |
| récurrentes : créer, suspendre, reprendre, historique | outils MCP du planificateur, port 8090 |

**Un point d'appui inattendu :** le client possède déjà un **parseur SSE maison**
sur `ByteReadChannel` (`core/network/.../sse/`), écrit pour le chat LibreChat et
éprouvé sur iOS comme sur Android. Le flux d'événements du moteur est du SSE :
c'est un point d'appui, pas un chantier neuf.

⚠️ **Deux préalables serveur avant la première mission lancée depuis le
téléphone :**

1. **`agent.hobbitton.at` n'est pas exposé.** Le moteur n'écoute que sur
   `127.0.0.1:4096` ; sa règle Authelia est écrite mais inactive (D-020).
   L'ouvrir est un geste de la phase 5, pas un acquis.
2. **Le timeout de mission n'a plus de point d'application.** Voir §5.

---

## 4. Identité, signature, distribution

| | État au 21/08 | Reste |
|---|---|---|
| `applicationId` | **`at.hobbitton.chat`** | — |
| clé de signature | **créée** : PKCS12, RSA 4096, alias `hobbitton`, valide jusqu'en 2054 ; empreinte et mots de passe dans Bitwarden | joindre le `.jks` à la fiche Bitwarden |
| CI release | `.github/workflows/release.yml` décode déjà un keystore depuis les secrets ; **aucune exécution à ce jour** | renseigner les 4 secrets `SIGNING_*` |
| Obtainium | — | dépend d'une première release signée |

⚠️ Le brief est explicite et mérite d'être répété : **clé perdue = plus aucune
mise à jour installable** sur les téléphones qui ont déjà l'application. Ce
n'est pas récupérable, à aucun prix. La clé va dans Bitwarden le jour où elle
est créée, pas « bientôt ».

Changer l'`applicationId` d'une application déjà installée équivaut à publier
une **application différente** : les installations existantes ne se mettent pas
à jour, elles cohabitent. À faire maintenant, tant que le parc se compte sur
les doigts d'une main.

---

## 5. Ce qui manque côté serveur, et que la phase 5 ne peut pas inventer

1. **Le timeout de mission n'a plus de point d'application.** Le brief marque
   NON NÉGOCIABLE « timeout et budget tokens par mission » (§4.3). Le budget est
   désormais couvert par les clés virtuelles LiteLLM, par lesquelles **tout**
   appel modèle transite — y compris celui d'une session créée en `curl`. Le
   timeout, non : le profil d'agent OpenCode borne le nombre d'itérations
   (`steps`), pas la durée, et un pas peut durer longtemps. La seule horloge
   réelle était celle du scheduler — celle qui a effectivement avorté la mission
   du 20 août — et elle sort du chemin critique en v9. À trancher avant la
   première mission (D-026 en donne trois pistes).

2. **Les clés virtuelles exigent PostgreSQL.** La gateway tourne aujourd'hui en
   mode « config seule » (D-007). L'ajout était prévu pour ce moment précis :
   un overlay Compose, zéro code.

3. **`agent.hobbitton.at` reste interne.** Rien de l'onglet Tasks ne fonctionne
   depuis un téléphone tant que le moteur n'est pas publié derrière Authelia.

4. **Le client Kotlin généré n'existe pas.** `scripts/generate-clients.sh` est
   écrit (phase 2) mais n'a jamais tourné — le moteur ne démarrait pas avant le
   19 août. Il produit `clients/kotlin/` depuis l'OpenAPI ; c'est le premier
   geste de la phase, et il donne la liste exacte des routes plutôt que celle,
   approximative, de ce document.

---

## 6. Ordre proposé

1. ~~Trancher le §0~~ — fait.
2. ~~**Activer le second facteur LibreChat** (§0)~~ — fait le 21 août, et
   vérifié depuis l'extérieur : `POST /api/auth/login` rend `twoFAPending`.
3. ~~Générer le client Kotlin (`make clients` côté serveur) et le versionner~~ —
   fait : `clients/kotlin`, 162 routes. L'application n'en embarque rien et
   écrit ses neuf routes à la main (D-033).
4. ~~Côté serveur : PostgreSQL + clés virtuelles, trancher le timeout, publier
   `agent.hobbitton.at`~~ — fait (§8).
5. ~~`applicationId` + clé de signature~~ — faits. **CI de release : pas
   encore** — les quatre secrets `SIGNING_*` manquent, donc aucune release
   signée, donc pas d'Obtainium.
6. ~~L'onglet Tasks~~ — livré en PR #10 pour les missions ponctuelles et
   interactives. Chats n'a pas été touché. Restent les récurrentes et la vue de
   détail (§8).

## 7. Décisions en attente

- ~~**D-A** — Authelia devant `/api`~~ → tranché, option A (D-024).
- ~~**D-B** — planificateur ou moteur ?~~ → tranché : **le moteur, en direct**
  (D-026). Le garde-fou ne devient pas obligatoire en rendant obligatoire le
  composant qui le porte : il se déplace là où rien ne passe à côté.
- ~~**D-C** — qui sert les livrables ?~~ → tranché : l'API de sessions (D-027).

~~**Reste ouvert, et bloquant :** le timeout de mission (§5.1).~~ → tranché :
un **chien de garde** dans le planificateur (D-028), faute d'une borne de durée
dans le moteur — `steps` compte des itérations, pas des secondes. Vérifié en
abattant une vraie session à 7 s pour un plafond de 5 s. Le budget, lui, est
descendu sur les clés virtuelles LiteLLM (D-029), par lesquelles **tout** appel
modèle transite.

---

## 8. Où en est cet écart au 21 août

Ce document a été écrit avant l'UI, comme le brief l'exige. Voici ce qu'il
reste une fois l'onglet livré — les quatre manques serveur du §5 d'abord :

| §5 | État au 21 août |
|---|---|
| 1. timeout sans point d'application | **levé** — chien de garde (D-028), mesuré |
| 2. clés virtuelles → PostgreSQL | **levé** — overlay `keys.yml`, deux clés (D-029), dépense et 429 `budget_exceeded` mesurés |
| 3. `agent.hobbitton.at` interne | **levé** — publié derrière Authelia. Le jeton voyage en `Proxy-Authorization`, en-tête *hop-by-hop* que **Caddy supprimait** : ré-injecté par label, mesuré (D-031) |
| 4. client Kotlin inexistant | **levé** — `clients/kotlin`, régénéré à chaque montée de version du moteur |

Et ce que l'onglet ne fait pas encore :

1. **Les missions récurrentes.** Le brief en fait l'un des deux modes autonomes
   (§6, phase 5). Elles passent par les outils MCP du planificateur, **qui n'est
   pas publié** : le port 8090 n'est joignable que depuis le serveur. Tant que
   c'est le cas, une récurrente se crée depuis le serveur, pas depuis le
   téléphone.
2. **La vue de détail.** Le brief la veut adaptée au livrable : visionneuse de
   diff et approbations quand la sortie est un dépôt, fichiers téléchargeables
   sinon. Les routes sont écrites (`GET /session/{id}/diff`,
   `POST /session/{id}/permissions/{id}`) et non appelées ; la liste montre
   l'état et la raison d'échec, pas le contenu.
3. **La distribution.** `release.yml` sait déjà décoder un keystore depuis les
   secrets ; il n'a jamais tourné. Voir §4.
