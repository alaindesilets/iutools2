# Spike : « Guess Meaning » dans iutools-mobile

## Contexte

iutools-mobile est une appli Android (Kotlin) qui embarque déjà un port de l'analyseur
morphologique inuktitut Uqailaut (développé au CNRC par Benoît Farley). L'appli permet
de décomposer un mot inuktitut en morphèmes avec leur sens.

On travaille à ajouter une nouvelle fonctionnalité, « Guess Meaning » : deviner le sens
d'un mot inuktitut inconnu, potentiellement avec son contexte (mots environnants), en
combinant plusieurs heuristiques :

1. Décomposition morphologique (Uqailaut) du mot et des mots qui l'entourent
2. Recherche dans des dictionnaires inuktitut en ligne. Candidats identifiés à inclure
   dans le prompt système sous forme de liste d'URLs :
   - Tusaalanga Glossary — https://tusaalanga.ca/glossary
   - Uqausiit / Inuktut Grammar Dictionary (inclut un dictionnaire d'affixes, pertinent
     en complément de la décomposition morphologique) — https://uqausiit.ca
   - Dictionnaire Spalding, sur Inuktitut Computing (même site que l'outil de
     recherche Hansard ci-dessous) — https://www.inuktitutcomputing.ca/Spalding/index.php
   - Asuilaak Living Dictionary (gouvernement du Nunavut, ~80 000 termes) — candidat
     avec la couverture la plus large, mais **URL fonctionnelle non confirmée** au
     moment de la rédaction de ce plan ; à vérifier avant de l'inclure. Contexte : le
     gouvernement du Nunavut aurait été mal servi par la compagnie engagée pour
     l'implanter — les données étaient hébergées sur les serveurs de cette compagnie
     dans un format binaire propriétaire (Microsoft) opaque ; le contrat a pris fin et
     le gouvernement se retrouverait avec des données dans ce format qu'il ne peut
     plus lire. Probablement pourquoi l'outil n'est plus facilement trouvable en ligne.
3. Recherche dans le Hansard du Nunavut (transcriptions bilingues de l'Assemblée
   législative, une version alignée a été publiée gratuitement par le CNRC) — objectif
   de le charger et l'indexer localement à terme. Pour le spike, la recherche se fera
   plutôt via l'outil de recherche déjà existant sur
   https://www.inuktitutcomputing.ca/NunavutHansard/ (développé par Benoît Farley au
   CNRC, dans le cadre du même projet UQAILAUT que l'analyseur morphologique) — pas
   d'indexation locale du corpus pour ce spike.
4. Recherche sur des sites bilingues (gouvernement du Nunavut, etc.)

On arrête dès qu'une hypothèse de sens semble bonne.

*(Note : cette heuristique d'arrêt anticipé décrivait le comportement du LLM dans la
simulation manuelle originale, où c'était Claude qui décidait où chercher, une source à
la fois. Le mécanisme retenu pour ce spike ne cherche plus séquentiellement — voir
« Décision de séquencement » plus bas — donc elle ne s'applique plus telle quelle au
mécanisme de recherche ; le compromis assumé à la place est expliqué dans cette même
section.)*

L'ordre exact des heuristiques et le prompt système ont été validés manuellement lors
d'une simulation avec Claude (rôle-jeu : Claude demandait une décomposition, l'humain
la fournissait en collant le résultat d'Uqailaut, Claude enchaînait avec des recherches
web réelles). Ça fonctionne bien conceptuellement. Ce spike vise à automatiser cette
simulation directement dans l'appli — génération automatique du prompt initial plutôt
que du copier-coller manuel.

## Décision de séquencement (révisée)

**Décision initiale** : faire tourner le LLM localement sur le téléphone par défaut
(pas de dépendance à un service cloud), avec une option future pour que l'utilisateur
pointe l'appli vers sa propre souscription à une IA en ligne (Claude, ChatGPT).

**Révision, après réflexion** : cette vision reste la cible à long terme, mais l'ordre
d'implémentation du spike est inversé. Un modèle local (Gemma via MediaPipe) mélange
deux risques très différents dans une seule étape : (1) est-ce que le *workflow*
Guess Meaning fonctionne du tout (bon ordre d'heuristiques, bon prompt, bonne
interprétation des résultats de recherche), et (2) est-ce qu'un petit modèle embarqué
peut l'*exécuter* de façon fiable (tool-calling, mémoire limitée, raisonnement multi-
étapes). Ces deux risques sont plus faciles à démêler séparément.

**Nouvelle approche** : les Phases 1 à 5 valident le workflow au complet avec un modèle
de pointe en ligne (**Claude**, via l'API Anthropic). Une fois cette base établie, la
**Phase 6** revisite la question du modèle local, avec le workflow et le prompt déjà
validés comme point de comparaison.

**Pourquoi Claude spécifiquement (pas ChatGPT/Gemini en parallèle) pour commencer** :
c'est déjà le modèle utilisé pour la simulation manuelle qui a validé le concept, et
son code d'intégration peut être écrit de façon fiable dès maintenant. Comparer les
trois fournisseurs en parallèle triplerait le travail de la Phase 1 pour un gain
marginal à ce stade — envisageable plus tard si utile.

**Révision de l'architecture de recherche (après coup, en observant les coûts réels du
spike)** : le plan avait d'abord prévu de déclarer les outils natifs `web_search`/
`web_fetch` d'Anthropic et de laisser Claude piloter lui-même la recherche de façon
agentique (décider où chercher, interpréter, décider s'il faut chercher ailleurs) — avec
`allowed_domains` appliqué par Anthropic elle-même, pas juste suggéré dans le prompt.
Cette approche a un vrai avantage : si l'interface d'une ressource change, un LLM
autonome peut potentiellement s'adapter tout seul. Mais deux inconvénients sont devenus
évidents dès les Phases 1-2 : (1) chaque aller-retour d'outil coûte des tokens de sortie
(5× plus chers que les tokens d'entrée sur Opus), donc un comportement agentique
multi-étapes est probablement le plus gros facteur de coût du spike ; (2) ça reproduit
exactement le risque déjà identifié pour la Phase 6 — le tool-calling agentique est
nettement moins fiable sur un petit modèle local que sur Claude, donc bâtir tout le
workflow autour de ça aurait inutilement compliqué le futur saut vers un modèle embarqué.

**Nouvelle approche : recherche pilotée par l'appli, pas par le LLM.** L'appli Kotlin
va elle-même chercher dans toutes les sources pertinentes (en parallèle, pour limiter la
latence totale), rassembler les résultats bruts, et les inclure au complet dans un seul
message envoyé à Claude, qui rend un verdict en un seul appel — aucun outil agentique,
aucun aller-retour. Avantages : coût prévisible (un appel par mot, pas une boucle
ouverte), et **ça élimine le principal risque de la Phase 6** — un modèle local n'a plus
besoin de tool-calling fiable, juste de lire un bloc de texte déjà rassemblé et de
répondre, exactement comme Claude : une tâche de lecture/synthèse, largement à la portée
d'un petit modèle.

Deux compromis assumés avec ce changement :
- Si l'interface d'une ressource change, le code Kotlin qui la consulte va se casser
  silencieusement plutôt que de s'adapter tout seul — voir « Notification de panne » dans
  la spec technique pour le mécanisme prévu (à terme, hors scope de ce spike) pour
  détecter et corriger ça rapidement.
- On renonce à l'heuristique « arrêter dès qu'une réponse semble bonne » — l'appli va
  toujours chercher dans toutes les sources activées, systématiquement, à chaque mot ;
  plus simple à coder (aucune logique d'arrêt anticipé à écrire), et le coût des tokens
  d'entrée « inutiles » qui en résulte reste probablement bien inférieur au coût des
  tokens de sortie agentiques qu'on évite en retour.

Ce n'est **pas** du MCP : c'est du code Kotlin ordinaire (client HTTP + extraction de
texte) par source, pas un protocole d'outils — MCP reste hors scope pour tout le spike
(voir la note dans « Idées pour des implémentations futures »).

## Objectif de ce spike

Ajouter un bouton dans iutools-mobile qui ouvre un écran de type « fenêtre de chat LLM »
permettant de rejouer, de façon largement automatisée, le workflow « Guess Meaning » —
directement dans l'appli, sans copier-coller manuel des résultats d'analyse
morphologique, en s'appuyant sur l'API Claude.

**Approche par phases** : plutôt que d'implémenter tout le spike d'un bloc, on procède
en 6 phases incrémentales, chacune isolant une nouvelle source de risque technique.
Chaque phase doit être fonctionnelle et testée avant de passer à la suivante.

### Phase 1 — Intégration de base à l'API Claude

Le bouton « Guess Meaning » (ou nom similaire) ouvre la fenêtre de chat — sans aucune
injection de contenu. Objectif : valider que l'appli peut appeler l'API Claude depuis
Kotlin/Android, gérer la clé API de façon sécuritaire, et afficher une conversation
simple, avant d'ajouter la moindre logique métier.

**Profiter de cette phase pour établir une base de référence de performance** : latence
par requête, coût par requête (tokens d'entrée/sortie), fiabilité réseau sur appareil
mobile (gestion des coupures, timeouts). Ces chiffres serviront de point de comparaison
en Phase 6 pour un modèle local (temps de réponse, coût nul vs coût par requête,
fiabilité hors ligne vs en ligne).

### Phase 2 — Injection de l'analyse morphologique

Le clic sur « Guess Meaning » appelle maintenant l'analyseur morphologique (code Kotlin
natif), et génère automatiquement un prompt combinant : l'analyse morphologique du mot
+ des instructions demandant à Claude de deviner le sens **à partir de la morphologie
seule** (pas encore de recherche web à ce stade). Le message n'est pas envoyé
automatiquement — voir « Comportement du bouton » dans la spec technique ci-dessous.

### Phase 3 — Recherche dans les dictionnaires en ligne (fetchers Kotlin par source)

L'appli interroge elle-même chaque dictionnaire (Tusaalanga, Uqausiit, Spalding —
Asuilaak si son URL est confirmée) via un petit module Kotlin par source (« fetcher ») :
construit la requête (URL de recherche ou appel équivalent, selon ce que le site
accepte), récupère la page/réponse, et en extrait le texte pertinent (définition(s)
trouvée(s) pour le mot, ou absence de résultat). Les résultats de tous les dictionnaires
sont ensuite ajoutés au message envoyé à Claude, en plus de la décomposition
morphologique (Phase 2) — Claude ne fait plus aucune recherche lui-même à cette étape,
il ne fait que lire et interpréter ce qui lui est fourni.

**Suggestion** : valider d'abord le mécanisme complet (fetcher, extraction, injection
dans le message, interprétation par Claude) sur **un seul dictionnaire** (Tusaalanga
suggéré) avant de brancher les autres. Si quelque chose bloque, ça isole si le
problème vient du mécanisme général ou d'une particularité d'un site donné. Ajouter
les dictionnaires restants devient ensuite un travail mécanique : un nouveau fetcher
+ une entrée dans la liste des sources à interroger.

**Exécution en parallèle** : une fois plus d'une source active, lancer les fetchers en
parallèle (coroutines Kotlin, `async`/`awaitAll`) plutôt qu'en séquence, pour garder une
latence totale raisonnable malgré le nombre de sources consultées à chaque mot.

### Phase 4 — Recherche dans le Hansard

Même principe qu'en Phase 3 : un fetcher Kotlin dédié pour
https://www.inuktitutcomputing.ca/NunavutHansard/, dont le résultat est ajouté au
message envoyé à Claude.

Avant de l'implémenter, il faut **inspecter comment ce site traite les requêtes**
(outils de développement du navigateur) :
- **Si le formulaire de recherche accepte une URL avec paramètres de requête** (ex.
  `?q=motrecherche`), le fetcher se limite à construire cette URL et à parser la
  réponse HTML.
- **Sinon** (formulaire JS/POST sans équivalent URL), le fetcher doit reproduire
  l'appel réel (méthode POST, en-têtes attendus, etc.) — un peu plus de travail, mais
  toujours du code HTTP ordinaire, pas d'automatisation d'interface graphique.

Cette phase reste volontairement après les dictionnaires (avant la Phase 5, qui est
plus simple) : contrairement aux dictionnaires (définition assez directe), le Hansard
retourne des **paires de phrases iu-en** — Claude doit repérer lui-même l'équivalent
anglais du mot inuktitut recherché à l'intérieur de la phrase anglaise correspondante,
à partir du texte brut que lui fournit le fetcher. C'est un raisonnement d'alignement
plus exigeant qu'une simple lecture de définition, mais qui reste un problème de
lecture/interprétation pour Claude, pas de recherche.

💡 Piste si cette étape d'alignement s'avère difficile en pratique : inclure aussi,
dans le message envoyé à Claude, la décomposition morphologique des mots inuktitut
environnants dans la phrase source (pas juste le mot cible) — ça donnerait des indices
lexicaux supplémentaires. À n'ajouter que si l'observation empirique montre que c'est
nécessaire.

### Phase 5 — Recherche sur les sites du gouvernement du Nunavut

Même principe qu'aux Phases 3-4 : un fetcher Kotlin qui interroge (probablement via une
recherche restreinte au domaine, ex. `site:gov.nu.ca ...`, ou le moteur de recherche
interne du site s'il y en a un) et retourne les pages bilingues pertinentes trouvées ;
résultat ajouté au message envoyé à Claude — la quatrième heuristique du workflow
d'origine.

Contrairement au Hansard (Phase 4), ces pages ne sont généralement pas des paires de
phrases alignées mot à mot — plutôt des pages de contenu bilingue (politiques,
programmes, glossaires ponctuels) où le mot peut apparaître dans des contextes variés.
Le défi ici est plutôt de **trouver** une page pertinente que d'aligner des phrases.

**L'heuristique de qualité de recherche** (maximiser les chances de tomber sur une page
ayant une traduction anglaise ou française du mot, pas juste une page qui contient le
mot isolément) se code maintenant directement dans le fetcher, en Kotlin. Une classe du
projet iutools original implémente déjà ce type d'heuristique — la retrouver et **porter
sa logique**, pas la réinventer.

### Phase 6 — Explorer un modèle local plutôt que Claude

Une fois le workflow validé de bout en bout avec Claude (Phases 1-5), revisiter la
proposition initiale : est-ce qu'un modèle plus petit, embarqué sur le téléphone, peut
exécuter le même workflow avec une qualité acceptable ?

**Simplification majeure apportée par la nouvelle architecture des Phases 3-5** :
comme la recherche est maintenant pilotée par l'appli Kotlin (fetchers) plutôt que par
le LLM (tool-calling agentique), cette phase **réutilise directement** les fetchers déjà
écrits et validés en Phases 3-5 — rien à reconstruire côté recherche. Le principal
risque qu'on redoutait pour cette phase (fiabilité du tool-calling natif de MediaPipe
pour de petits modèles Gemma) **disparaît presque entièrement** : le modèle local n'a
plus qu'à lire un bloc de texte déjà rassemblé et donner un verdict, exactement comme
Claude — une tâche de lecture/synthèse, pas d'orchestration d'outils.

**Ce qui reste différent ici par rapport aux Phases 1-5** :
- **SDK d'inférence** : MediaPipe LLM Inference API (`com.google.mediapipe:tasks-genai`)
  plutôt que l'API Anthropic.
- **Modèle recommandé pour commencer : Gemma 3n E2B** (repli sur Gemma 3 1B si même
  E2B ne rentre pas confortablement en mémoire). Pas Gemma E4B (4-5 Go) — trop gros
  pour un appareil de test comme le Galaxy A52 (variantes 4/6/8 Go de RAM totale ;
  Android + l'appli elle-même prennent déjà 2-3 Go avant de charger un modèle).
- **Test mémoire bloquant, pas juste indicatif** : si le modèle choisi ne rentre pas
  confortablement en mémoire libre sur l'appareil de test réel, c'est un blocage dur
  pour cette phase — pas un chiffre à noter et ignorer.
- **Comparaison directe avec les Phases 1-5** : pour un même mot testé dans les deux
  versions, comparer qualité de l'hypothèse finale, latence, et bien sûr le fait que
  l'appel au modèle tourne sans connexion réseau ni coût récurrent (les fetchers,
  eux, continuent d'appeler des sites externes dans les deux versions).

## Hors scope pour ce spike

- Pas d'indexation locale du corpus Hansard CNRC — voir Phase 4, ce spike utilise
  plutôt l'outil web pour piloter (ou lire directement, si l'URL le permet) l'interface
  de recherche existante sur inuktitutcomputing.ca
- Pas d'outil pour afficher des messages de progrès dans l'UI (ex. « j'analyse le
  mot », « je cherche dans le dictionnaire X ») — utile éventuellement, pas nécessaire
  pour ce spike
- Pas de logique d'orchestration complète des heuristiques (ordre strict, arrêt
  anticipé formalisé) — le prompt système donne les instructions, on observe si le
  modèle les suit raisonnablement
- Pas de UI pour fournir le « contexte » (mots environnants) — un seul champ de texte
  libre suffit pour ce spike
- Pas d'écran de configuration pour que l'utilisateur entre sa propre clé API — pour ce
  spike, la clé du développeur suffit (voir « Gestion de la clé API » ci-dessous). Un
  écran de configuration utilisateur (et le choix cloud/local qui va avec) reste une
  itération future.
- Pas de MCP nulle part dans ce spike (ni Phases 1-5, ni Phase 6) — voir la note dans
  « Idées pour des implémentations futures » pour les conditions qui pourraient
  justifier de le reconsidérer plus tard.

## Spec technique proposée

**SDK** : SDK Java officiel d'Anthropic (`com.anthropic:anthropic-java`) — Kotlin
utilise le SDK Java. Modèle par défaut : `claude-opus-5` (pour établir la meilleure
démonstration possible que le concept fonctionne) ; `claude-sonnet-5` est une option
moins coûteuse si le volume d'itérations pendant le développement devient un enjeu.

**Gestion de la clé API** : la clé du développeur pour ce spike, jamais commitée dans
le dépôt :
- Stockée dans un fichier local ignoré par git (`local.properties` ou équivalent — ce
  projet en a déjà un pour le SDK Android) ou une variable d'environnement
- Injectée au build via `BuildConfig`, jamais codée en dur dans une chaîne de
  caractères Kotlin
- ⚠️ Limite à garder en tête pour une itération future (pas un blocage pour ce spike) :
  une clé API embarquée dans un APK distribué reste extractible par rétro-ingénierie.
  Acceptable pour un spike que le développeur seul fait tourner sur son propre
  appareil ; pas une architecture à envoyer à de vrais utilisateurs (demanderait un
  serveur relais détenant la clé, pas le client mobile directement).

**Nouvel écran** (Activity ou Composable, selon ce qui est déjà utilisé ailleurs dans
l'appli) :

- Un bouton « Simuler Guess Meaning » (ou nom similaire) ajouté à l'écran principal
  existant, qui ouvre ce nouvel écran
- Une interface de chat classique : champ de saisie + zone d'affichage de la
  conversation (tours utilisateur/modèle/appels d'outils)
- **Comportement du bouton « Guess Meaning »** : au clic, l'appli génère
  **automatiquement** le contenu du champ de saisie du chat, en combinant :
  1. Les instructions préétablies (le prompt/les heuristiques — idéalement dans le
     champ `system` de la requête, séparé du message utilisateur, plutôt que mélangé)
  2. Le résultat de l'analyse morphologique du mot (appel direct à l'analyseur Kotlin
     natif)

  Le message **n'est pas envoyé automatiquement** — l'utilisateur peut relire/modifier
  ce texte généré avant de l'envoyer manuellement (bouton « Envoyer » séparé), pour
  ajuster le prompt à la volée pendant les tests, sans avoir à tout retaper ni à
  copier-coller depuis une autre source.
- Une fois le message envoyé, Claude doit pouvoir invoquer les outils `web_search`/
  `web_fetch` de façon autonome, sans intervention manuelle, pour appliquer les
  heuristiques de recherche décrites dans le prompt
- Un indicateur de chargement pendant l'appel réseau (latence typique d'une requête
  Claude avec plusieurs allers-retours d'outils — peut prendre plusieurs secondes)

**Cache par mot (nouvel item, à faire après la Phase 2)** : si l'utilisateur soumet à
nouveau un mot déjà analysé précédemment (même mot, même réglage lenient/strict), l'appli
n'appelle pas Claude une deuxième fois — elle réaffiche directement la conversation
précédente pour ce mot dans le chat, sans requête réseau. Objectif double : éviter de
payer/attendre pour une réponse déjà obtenue, et permettre de comparer facilement les
résultats d'un mot testé à plusieurs reprises pendant le développement (plusieurs des mots
cités dans ce plan comme cas de test, ex. ᐃᓕᓐᓂᐊᕐᓂᖅ/ilinniarniq, reviendront probablement
souvent). Si cette conversation ne satisfait plus l'utilisateur, il peut ajouter une
précision dans le champ de saisie et l'envoyer normalement : le nouveau message est alors
soumis à Claude avec la conversation précédente comme contexte (historique complet), et le
`system` prompt **actuel** — qui peut avoir changé depuis le premier appel (ex. après un
ajustement des instructions) — plutôt que celui utilisé à l'origine.

**Outils natifs `web_search`/`web_fetch`** : déclarés directement dans le tableau
`tools` de chaque requête, avec `allowed_domains` restreint aux sites pertinents à la
phase en cours (voir Phases 3-5). C'est Anthropic qui exécute la recherche/récupération
côté serveur et applique la restriction de domaines — pas de code côté appli pour ça,
au-delà de la déclaration de l'outil. Cette restriction satisfait toujours l'objectif de
sécurité du plan original (limiter la surface d'accès du modèle à des sources de
confiance, minimiser le risque de prompt injection depuis une page non fiable) — c'est
juste Anthropic qui l'applique plutôt que du code écrit dans ce projet.

**Hansard (Phase 4 seulement)** : voir la note dans cette phase — outil client
personnalisé possible si le site n'accepte pas de recherche par URL.

## Critères de succès par phase

**Phase 1** :
- L'appli arrive à appeler l'API Claude depuis Android et afficher une réponse dans le
  chat, sans crash
- La clé API n'apparaît nulle part dans le code commité (vérifier `git status`/`git
  diff` avant tout commit)
- Métriques de référence capturées : latence par requête, tokens consommés (donc coût
  approximatif) par requête

**Phase 2** :
- Le clic sur « Guess Meaning » génère bien un prompt initial correct (instructions +
  résultat d'analyse morphologique), éditable avant l'envoi
- Claude produit une hypothèse de sens raisonnable à partir de la morphologie seule,
  pour des cas déjà testés manuellement (ex. ᐃᓕᓐᓂᐊᕐᓂᖅ / ilinniarniq)

**Phase 3** :
- Claude invoque correctement `web_search`/`web_fetch` (bonne URL ou bonne requête) sur
  le premier dictionnaire testé, puis sur les autres
- Claude interprète correctement les vrais résultats retournés pour continuer son
  raisonnement
- Claude sait décider de ne pas chercher davantage quand il juge avoir assez
  d'information
- La restriction `allowed_domains` fonctionne : une tentative de recherche hors de
  l'allowlist ne retourne rien hors de ces domaines — à tester explicitement

**Phase 4** :
- Confirmation documentée de comment le Hansard traite les requêtes (URL à paramètres
  vs formulaire JS/POST), et implémentation en conséquence (outil natif ou outil
  personnalisé)
- Claude est capable de repérer l'équivalent anglais d'un mot inuktitut à l'intérieur
  d'une paire de phrases alignées retournée par la recherche Hansard
- Comparaison globale : pour un cas comme ᐱᕈᖅᐸᓪᓕᐊᕐᓂᕐᒥᒃ / piruqpalliarnirmik (déjà testé
  manuellement), la qualité/pertinence de l'hypothèse finale se compare raisonnablement
  à celle obtenue lors des simulations manuelles

**Phase 5** :
- Claude invoque `web_search` restreint à `gov.nu.ca` pour rechercher une page bilingue
  pertinente
- Claude sait interpréter des pages de contenu variées (pas juste des paires de
  phrases)
- Claude applique l'heuristique de qualité de recherche décrite en langage naturel dans
  le prompt (dérivée de la classe iutools originale) pour privilégier les pages ayant
  une traduction
- Claude continue de savoir arrêter la recherche dès qu'une hypothèse de sens semble
  bonne, même avec cette 5e source disponible

**Phase 6** :
- **Bloquant** : le modèle choisi (Gemma 3n E2B, repli Gemma 3 1B) charge et tourne sans
  crash sur l'appareil de test réel, avec une marge de mémoire libre raisonnable —
  sinon, documenter l'échec et arrêter cette phase plutôt que de forcer
- Le tool-calling natif de MediaPipe fonctionne de façon fiable pour ce modèle (testé
  tôt, isolément, avant de rebrancher tout le reste du workflow)
- L'outil `web_access` personnalisé (avec allowlist codée en dur) fonctionne pour au
  moins les dictionnaires ; Hansard si le temps le permet
- Comparaison documentée avec les résultats des Phases 1-5 : qualité des hypothèses,
  latence, fiabilité du tool-calling, et le compromis coût/connectivité (gratuit et
  hors-ligne vs payant et nécessitant une connexion)

## Étapes d'implémentation suggérées (détail technique par phase)

**Phase 1** :
1. Ajouter la dépendance `com.anthropic:anthropic-java` au projet
2. Mettre en place la gestion de la clé API (`local.properties`/variable
   d'environnement → `BuildConfig`, rien de commité)
3. Ajouter le bouton + le nouvel écran de chat (UI minimale)
4. Câbler un appel de base à `client.messages.create(...)` (conversation simple, sans
   injection, sans outils)
5. Capturer les métriques de référence (voir critères de succès)

**Phase 2** :
6. Câbler la génération automatique du prompt initial (instructions + résultat de
   l'analyse morphologique) au clic sur « Guess Meaning », avec envoi manuel (voir
   « Comportement du bouton » dans la spec technique)
7. Tester avec quelques cas déjà utilisés dans les simulations manuelles

**Phase 3** :
8. Déclarer l'outil `web_search_20260209` (et/ou `web_fetch_20260209`) avec
   `allowed_domains` limité à Tusaalanga d'abord
9. Mettre à jour le prompt système pour instruire Claude sur l'usage de ce dictionnaire
10. Tester de bout en bout, puis étendre `allowed_domains` et le prompt à Uqausiit et
    Spalding (et Asuilaak si son URL est confirmée)

**Phase 4** :
11. Inspecter comment https://www.inuktitutcomputing.ca/NunavutHansard/ traite les
    requêtes (outils de développement du navigateur)
12. Selon le résultat : ajouter `inuktitutcomputing.ca` à `allowed_domains` (si URL à
    paramètres) ou implémenter un outil client personnalisé pour ce site (sinon)
13. Mettre à jour le prompt système avec les instructions sur le Hansard et le défi
    d'alignement iu-en
14. Tester sur les cas déjà utilisés dans les simulations manuelles

**Phase 5** :
15. Ajouter `gov.nu.ca` à `allowed_domains` sur l'outil `web_search`
16. Retrouver et étudier la classe du projet iutools original qui implémente
    l'heuristique de recherche (maximisation des chances de tomber sur une page
    traduite) — traduire sa logique en instructions de prompt en langage naturel, sans
    la réimplémenter en Kotlin
17. Mettre à jour le prompt système avec les instructions sur cette heuristique
18. Tester sur les cas déjà utilisés dans les simulations manuelles

**Phase 6** :
19. Ajouter la dépendance MediaPipe GenAI au projet (sur une branche séparée, ou un
    module optionnel, pour ne pas alourdir l'appli principale si cette phase échoue)
20. Câbler le téléchargement + chargement d'un modèle `.task` (Gemma 3n E2B d'abord)
21. **Test bloquant** : mesurer la mémoire disponible réelle sur l'appareil de test
    avant d'aller plus loin ; si insuffisant, essayer Gemma 3 1B ; si toujours
    insuffisant, documenter l'échec et arrêter
22. Tester le tool-calling natif de MediaPipe isolément (un seul outil simple, avant de
    rebrancher le reste du workflow)
23. Implémenter l'outil `web_access` personnalisé avec l'allowlist codée en dur
    (réutiliser ce qui a été découvert en Phase 4 pour le Hansard)
24. Rebrancher le prompt système déjà validé en Phases 2-5, l'adapter si nécessaire au
    style de tool-calling de MediaPipe
25. Tester sur les mêmes cas que les phases précédentes et comparer

**Après les 6 phases** : documenter les résultats (temps de réponse par phase, coût par
requête pour la version Claude, fiabilité du tool-calling pour les deux versions,
qualité des hypothèses de sens, tout crash ou limitation observée) pour informer la
suite (est-ce qu'un modèle local est viable pour ce cas d'usage, ou est-ce que la
version cloud devient le chemin principal avec le modèle local comme option
secondaire).

## Idées pour des implémentations futures (hors scope de ce spike)

- **Mémoire persistante des heuristiques efficaces** : donner au LLM un mécanisme de
  mémoire persistante pour se souvenir de quelles heuristiques (dictionnaires,
  Hansard, etc.) semblent mieux fonctionner selon les cas rencontrés. Permettrait aux
  développeurs d'expérimenter et d'ajuster le prompt système fourni par défaut, en se
  basant sur ce que cette mémoire révèle au fil du temps. Pas dans le scope de ce
  spike.
- **Écran de configuration utilisateur** (cloud avec clé personnelle vs modèle local,
  choix du modèle) — mentionné comme hors scope plus haut, mais vaut la peine d'être
  noté ici comme prochaine itération naturelle une fois les Phases 1-6 complétées.
- **Abstraction commune d'outils entre Claude et un modèle local** : si la Phase 6
  s'avère concluante et qu'on veut supporter les deux (cloud et local) à long terme,
  envisager une petite couche d'abstraction interne (nom d'outil, schéma JSON,
  fonction `execute()`) traduite vers le format natif de chaque backend — le
  function-calling de MediaPipe d'un côté, le `tools` natif de l'API Claude de l'autre.
  Ça donnerait le même bénéfice de portabilité multi-modèles que MCP sans la lourdeur
  du protocole client-serveur, puisque tous les outils restent internes à l'appli.
  **MCP ne serait à reconsidérer que si** l'appli avait un jour besoin de se connecter
  à un serveur MCP tiers déjà existant (ex. un serveur MCP officiel pour un
  dictionnaire ou une base terminologique inuktitut, si un tel service apparaît) —
  pas pour orchestrer des outils écrits et maintenus dans ce projet.
