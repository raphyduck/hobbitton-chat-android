# Analyse d'écart — avant l'onglet Tasks et l'onglet Code

**Date :** 20 août 2026 · **Phase 5 du brief** (`hobbitton-chat-server/docs/BRIEF.md`)

Le brief impose ce document **avant toute UI** : « `docs/GAP_ANALYSIS.md` avant
toute UI (endpoints memory/projects/skills vs client) ». L'idée est simple —
savoir ce que le serveur offre déjà et ce que le client sait déjà en faire,
avant de dessiner des écrans qui supposeraient l'un ou l'autre.

Ce qui suit compare trois surfaces serveur au client tel qu'il est aujourd'hui
(`e87a8d5`, v2026.08.2).

---

## 0. Le blocage à traiter en premier

**L'application ne peut plus se connecter au serveur.**

La phase 4 a mis Authelia devant `chat.hobbitton.at` en politique `two_factor`.
Authelia intercepte **toutes** les requêtes de l'hôte, `/api/*` comprise, et
répond `302` vers le portail à qui n'a pas de cookie de session. Or le client
Android s'authentifie par `POST /api/auth/login` et porte ensuite un JWT
LibreChat : il n'a pas de navigateur, pas de cookie Authelia, et rien dans son
pile réseau ne sait franchir un formulaire de connexion.

Vérifié : `curl https://chat.hobbitton.at/` → `302 → auth.hobbitton.at`.

Ce n'est pas un défaut de la phase 4 — c'est la conséquence attendue d'avoir
mis une porte devant un hôte dont un client machine dépend. Mais **aucun écran
de la phase 5 ne sert à rien tant que ce n'est pas tranché**, et c'est le genre
de chose qu'on découvre au premier lancement sur téléphone si on ne l'écrit
pas ici.

Trois options, à arbitrer avant d'écrire une ligne d'UI :

| | Ce que ça donne | Ce que ça coûte |
|---|---|---|
| **A. Exempter `/api/*` d'Authelia** | Le client retrouve son parcours actuel : login LibreChat, JWT, 2FA applicative (LibreChat a la sienne — `api/auth/2fa/*` est déjà implémenté côté client) | La surface API n'est plus derrière le SSO. Le brief dit « SSO + 2FA partout » ; on s'appuierait sur le fait que `/api` porte **déjà** une authentification et un second facteur, mais ce n'est pas *le même* SSO |
| **B. Session Authelia dans l'app** | Une WebView pour la connexion, puis le cookie est réinjecté dans le client Ktor | Deux authentifications empilées pour l'utilisateur (Authelia *puis* LibreChat), un cookie à faire vivre hors du navigateur, et une WebView à maintenir. C'est la solution la plus fidèle au brief et la plus lourde |
| **C. Un hôte dédié aux clients** | `app.hobbitton.at` sert le même LibreChat sans `forward_auth` | Une deuxième porte d'entrée à surveiller, et la tentation permanente de l'utiliser pour tout le reste |

**Recommandation : A**, parce que `/api` n'est pas une surface anonyme — elle
exige déjà un mot de passe et un second facteur, gérés par LibreChat, que le
client sait faire. La règle Authelia deviendrait : `two_factor` sur l'hôte,
`bypass` sur `/api/*`. C'est un choix à consigner dans `DECISIONS.md` du dépôt
serveur, pas ici.

---

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

1. **Trancher le §0** (Authelia et `/api`). Rien ne sert sans cela.
2. Générer le client Kotlin (`make clients` côté serveur) et le versionner.
3. Côté serveur : lancement ponctuel avec plafonds, et route de livrables (§5).
4. `applicationId` + clé de signature + CI. Tôt, pour la raison donnée au §4.
5. Onglet Tasks, puis onglet Code. Chats n'est pas touché.

## 7. Décisions en attente

- **D-A** — Authelia devant `/api` : option A, B ou C du §0.
- **D-B** — l'application parle-t-elle au planificateur (plafonds garantis) ou
  au moteur (plus direct, plafonds contournés) ?
- **D-C** — qui sert les livrables, et sous quelle authentification ?

Ces trois-là appartiennent au dépôt serveur : elles iront dans son
`docs/DECISIONS.md`, pas ici.
