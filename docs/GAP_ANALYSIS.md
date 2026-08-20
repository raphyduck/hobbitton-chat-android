# Analyse d'écart — avant l'onglet Tasks et l'onglet Code

**Date :** 20 août 2026 · **Phase 5 du brief** (`hobbitton-chat-server/docs/BRIEF.md`)

Le brief impose ce document **avant toute UI** : « `docs/GAP_ANALYSIS.md` avant
toute UI (endpoints memory/projects/skills vs client) ». L'idée est simple —
savoir ce que le serveur offre déjà et ce que le client sait déjà en faire,
avant de dessiner des écrans qui supposeraient l'un ou l'autre.

Ce qui suit compare trois surfaces serveur au client tel qu'il est aujourd'hui
(`e87a8d5`, v2026.08.2).

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

## 2. Onglet Tasks — tout est à faire

Le brief : « création : description + profil + cases connecteurs + ponctuelle ou
cron ; stream de progression ; livrables téléchargeables ; pause/reprise ».

**Côté client : rien.** Aucun module `feature/tasks`, aucun client du moteur
Agent, aucune référence à OpenCode. Vérifié par recherche sur l'ensemble des
sources.

**Côté serveur : tout existe et tourne.** Ce qu'il faut consommer :

| Besoin de l'écran | Ce que le serveur offre | Où |
|---|---|---|
| liste des profils | `GET /agent` | moteur Agent, port 4096 |
| créer une mission ponctuelle | `POST /session` puis `POST /session/{id}/prompt_async` | moteur Agent |
| cases à cocher des connecteurs | règles `{permission, pattern, action}` passées à la création de session | moteur Agent |
| plafonds durée / jetons | **n'existent pas dans le moteur** — ils sont tenus par `scheduler/moteur.py` côté serveur | à exposer, voir §5 |
| suivi de progression | `GET /session/{id}/message`, `GET /session/status` | moteur Agent |
| mission récurrente (cron) | outils MCP `planifier`, `lister`, `prochaines`, `historique`, `lancer`, `activer`, `desactiver`, `supprimer` | planificateur, port 8090 |
| pause / reprise | `desactiver` / `activer` | planificateur |
| livrables | espace de travail du moteur (`agent_workspaces`) — **aucune route de téléchargement** | à créer, voir §5 |

---

## 3. Onglet Code — tout est à faire

Le brief : « sessions, stream, diff, approbations ».

Le moteur expose ce qu'il faut : `/session/{id}/diff`, `/session/{id}/revert`,
`/session/{id}/permissions/{permissionID}` pour les approbations,
`/session/{id}/event` pour le flux. Le client, lui, n'a rien.

Bonne nouvelle : le client possède **déjà un parseur SSE maison** sur
`ByteReadChannel` (`core/network/.../sse/`), écrit pour le chat LibreChat et
éprouvé sur iOS comme sur Android. Le flux d'événements du moteur Agent est du
SSE lui aussi : c'est un point d'appui, pas un chantier neuf.

---

## 4. Identité, signature, distribution

| | État | À faire |
|---|---|---|
| `applicationId` | `com.garfiec.librechat` | → `at.hobbitton.chat` |
| clé de signature | seule la `debug.keystore` du dépôt | créer une clé dédiée, **la déposer dans Bitwarden** |
| CI release | `.github/workflows/release.yml` décode déjà un keystore depuis les secrets | adapter au nouveau keystore |
| Obtainium | — | dépend d'une release GitHub signée, donc du point précédent |

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

Trois trous réels, découverts en faisant cet inventaire :

1. **Les plafonds de mission ne sont pas dans le moteur.** Le brief marque
   NON NÉGOCIABLE « timeout et budget tokens par mission » (§4.3). Aujourd'hui
   ils sont tenus par `services/scheduler/scheduler/moteur.py`, qui surveille et
   avorte. Une application qui parlerait **directement** au moteur Agent
   contournerait donc les deux plafonds. → **L'application doit passer par le
   planificateur, pas par le moteur**, ou le serveur doit exposer un lancement
   ponctuel qui applique les plafonds.

2. **Aucune route pour récupérer un livrable.** Les missions produisent des
   fichiers dans le volume `agent_workspaces` ; rien ne les sert. « Livrables
   téléchargeables » demande une route, avec la question de l'authentification
   qui va avec.

3. **Le client Kotlin généré n'existe pas.** `scripts/generate-clients.sh` est
   écrit (phase 2) mais n'a jamais tourné — le moteur ne démarrait pas avant
   hier. Il produit `clients/kotlin/` depuis l'OpenAPI ; c'est le premier geste
   de la phase, avant toute UI, et il donne du même coup la liste exacte des
   routes plutôt que celle, approximative, de ce document.

---

## 6. Ordre proposé

1. ~~Trancher le §0~~ — fait.
2. **Activer le second facteur LibreChat** (§0). C'est ce qui rend l'exemption
   défendable ; sans lui, elle est un trou.
3. Générer le client Kotlin (`make clients` côté serveur) et le versionner.
4. Côté serveur : lancement ponctuel avec plafonds, et route de livrables (§5).
5. `applicationId` + clé de signature + CI. Tôt, pour la raison donnée au §4.
6. Onglet Tasks, puis onglet Code. Chats n'est pas touché.

## 7. Décisions en attente

- ~~**D-A** — Authelia devant `/api`~~ → tranché le 20 août, option A (D-024).
- **D-B** — l'application parle-t-elle au planificateur (plafonds garantis) ou
  au moteur (plus direct, plafonds contournés) ?
- **D-C** — qui sert les livrables, et sous quelle authentification ?

Ces trois-là appartiennent au dépôt serveur : elles iront dans son
`docs/DECISIONS.md`, pas ici.
