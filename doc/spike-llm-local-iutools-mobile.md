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
   - Tusaalanga Glossary — https://tusaalanga.ca/glossary (recherche par lettre :
     `?l=N` retourne tous les mots commençant par `N`, confirmé par Alain — voir
     Phase 3)
   - Uqausiit / Inuktut Grammar Dictionary (inclut un dictionnaire d'affixes, pertinent
     en complément de la décomposition morphologique) — https://uqausiit.ca
   - Dictionnaire Spalding, sur Inuktitut Computing (même site que l'outil de
     recherche Hansard ci-dessous) —
     https://www.inuktitutcomputing.ca/Spalding/index.php?lang=en (URL confirmée par
     Alain ; voir Phase 3 pour pourquoi c'est le point de départ recommandé)
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
- Si l'interface d'une ressource change, le code Kotlin qui la consulte va se casser —
  contrairement à un LLM autonome, il ne peut pas s'adapter tout seul. Décision retenue :
  **afficher un message d'erreur** quand une source échoue (voir « Fetchers par source »
  dans la spec technique), plutôt qu'une dégradation silencieuse. Le but premier n'est
  pas une belle UX pour un futur utilisateur final, mais d'avertir **les développeurs**
  qu'un fetcher est cassé et doit être réparé — donc affiché seulement dans les builds
  debug (`BuildConfig.DEBUG`, même mécanisme déjà utilisé pour le panneau d'inspection
  du prompt système), pas dans une éventuelle build release. En attendant le mécanisme
  de notification serveur prévu à terme (voir « Notification de panne », toujours hors
  scope de ce spike lui-même).
- On renonce à l'heuristique agentique « arrêter dès qu'une réponse semble bonne » —
  l'appli interroge systématiquement toutes les sources d'un palier donné, plutôt que de
  chercher séquentiellement et de s'arrêter dès qu'un résultat semble suffisant.
  **Exception délibérée, en deux paliers** : toutes les sources dictionnaires
  (Spalding/Uqausiit/Tusaalanga/Asuilaak, en parallèle) sont interrogées d'abord ; si
  **au moins une** retourne une définition pour le mot exact recherché (pas une
  approximation), l'appli s'arrête là — pas de deuxième palier (Hansard, pages
  bilingues du Nunavut), et **pas d'appel à Claude non plus**, puisqu'il n'y a alors
  rien à deviner. Sinon, l'appli interroge le deuxième palier (toujours en parallèle),
  puis soumet tout ce qui a été trouvé (les deux paliers) à Claude. C'est une règle
  déterministe (correspondance exacte trouvée ou non, palier par palier), pas une
  heuristique floue de type « ça semble suffisant » — la distinction qui justifie de la
  garder même si on a écarté l'heuristique agentique équivalente.

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
Asuilaak si son URL est confirmée) via un petit module Kotlin par source (« fetcher »),
qui retourne le texte pertinent pour le mot recherché (définition trouvée, ou absence de
résultat). Les résultats de toutes les sources sont ensuite ajoutés au message envoyé à
Claude, en plus de la décomposition morphologique (Phase 2) — Claude ne fait plus
aucune recherche lui-même à cette étape, il ne fait que lire et interpréter ce qui lui
est fourni.

**Court-circuit sur correspondance exacte, en deux paliers** : toutes les sources
dictionnaires activées (Spalding, puis Uqausiit et Tusaalanga une fois codés) sont
interrogées **en parallèle**, et l'appli attend qu'elles aient toutes répondu — pas
d'annulation anticipée dès la première réponse. Si **au moins une** a retourné une
définition pour le mot exact recherché (pas une approximation ni un terme apparenté),
l'appli s'arrête là : elle affiche directement la ou les définitions trouvées, **sans
appeler Claude du tout** — rien à deviner quand un dictionnaire donne déjà la réponse.
Sinon, l'appli passe au deuxième palier (Hansard en Phase 4, pages bilingues du
gouvernement du Nunavut en Phase 5, elles aussi en parallèle entre elles), puis soumet
à Claude tout ce qui a été trouvé aux deux paliers pour qu'il en dégage une ou des
hypothèses de sens plausibles.

**Gestion des échecs** : si l'accès à une source échoue (site inaccessible, changement de
structure qui casse le parsing, timeout), l'appli **affiche un message d'erreur**. Le
but n'est pas une belle UX pour un futur utilisateur final, mais d'avertir **les
développeurs** qu'un fetcher est cassé — affiché seulement en build debug
(`BuildConfig.DEBUG`), comme le panneau d'inspection du prompt système. Avec une seule
source active (le cas de départ ci-dessous), un échec bloque tout : rien à envoyer à
Claude sans aucune donnée. Une fois plusieurs sources actives, un échec partiel affiche
l'erreur (debug) pour la source concernée mais n'empêche pas de continuer avec celles
qui ont réussi, ni de passer au palier suivant si besoin.

**Un test par fetcher** : en plus du message d'erreur en build debug, chaque fetcher
devrait avoir un test dédié qui l'interroge pour de vrai (pas de simulation) sur un mot
connu, et vérifie le résultat attendu — pas juste que ça compile. En roulant les tests
régulièrement, ça permet de détecter rapidement qu'un fetcher a cessé de fonctionner
suite à un changement d'interface de la source, avant même qu'un utilisateur ne tombe
dessus — un filet de sécurité qui complète (sans le remplacer) le message d'erreur au
runtime et le mécanisme de notification serveur prévu à terme. Pour Spalding (recherche
locale dans le JSON embarqué), ce test tourne comme n'importe quel autre test JVM du
projet. Pour les fetchers réseau (Uqausiit, Tusaalanga, Hansard, gov.nu.ca), le test a
besoin d'un accès Internet réel — voir la répartition des tests dans AGENTS.md
(« Division of labor: what the AI runs vs. what the human runs ») : ce ne sont pas des
tests que l'IA peut rouler elle-même depuis ce bac à sable, contrairement à celui de
Spalding.

**✅ Spalding, sur Inuktitut Computing — fait, premier dictionnaire implémenté.**
Alain a confirmé que cette page (contrairement aux autres candidats) est une **seule
longue page HTML statique** — pas de formulaire de recherche par mot du tout. Ça change
complètement l'approche et en a fait le point de départ le plus simple :
- Pas de mécanisme de requête à deviner ni à inspecter (contrairement à Tusaalanga,
  Uqausiit, ou le Hansard en Phase 4) — aucun risque de formulaire JS/POST à reproduire.
- Page récupérée par Alain (`curl`, depuis son Mac — ce bac à sable de développement
  bloque l'accès Internet général) et parsée par
  `tools/parse_spalding_dictionary.py` (vérifié contre la page source, jamais de
  retranscription manuelle — même logique que les données linguistiques CSV, voir
  AGENTS.md « Preserving data integrity ») en **8373 entrées** (chaque bloc `<div
  class=entry>` du site regroupe souvent un mot principal et ses variantes ; chacune
  pointe vers le texte complet du bloc plutôt que d'essayer de découper la prose par
  mot). Résultat embarqué dans `composeApp/src/main/res/raw/spalding.json`.
- La recherche Spalding (`SpaldingDictionary.kt`) est une consultation locale dans une
  `Map` en mémoire, chargée une seule fois depuis ce JSON embarqué — **aucun appel
  réseau au runtime**, contrairement aux autres sources (dictionnaires ou non) qui
  restent de vrais appels HTTP. C'est délibérément *local*, pas un « fetcher » au sens
  réseau du terme, même si le plan utilise ce mot comme catégorie générale pour tout
  module par source.
- Étant locale et quasi instantanée, elle revient forcément la première parmi le palier
  des dictionnaires (voir « Court-circuit » plus bas) — sans qu'il soit nécessaire
  d'écrire une logique d'ordonnancement particulière, l'appli attend simplement que
  toutes les sources du palier aient répondu avant de décider.
- Limite à garder en tête (différente d'une panne réseau) : le JSON embarqué peut
  devenir désuet si la page Spalding change — un problème de fraîcheur des données, pas
  de fiabilité réseau ; pas couvert par le mécanisme de « Notification de panne »
  ci-dessous (pensé pour des fetchers réseau qui cassent, pas du contenu qui vieillit).
  Ré-exécuter `tools/parse_spalding_dictionary.py` (et revoir le diff) si la page
  source change un jour.

**TODO (pas encore fait) : respecter le script d'affichage choisi (Roman/Syllabique/tel
qu'entré) pour le mot-vedette de Spalding.** Les mots de Spalding sont écrits en romain
dans la source ; si l'utilisateur a choisi l'affichage syllabique dans les Réglages, le
mot-vedette affiché (`entry.word` dans `DictionaryResultSection`) devrait être converti
en syllabique lui aussi — actuellement toujours affiché tel quel (romain), sans passer
par `displayForm()` (la fonction déjà utilisée pour les formes de surface et formes
canoniques dans le tableau de décomposition). Portée volontairement limitée au
mot-vedette lui-même : le texte de la définition (`entry.meaning`) reste en anglais
(prose du dictionnaire), y compris ses renvois croisés vers d'autres mots inuktitut
intégrés dans cette prose (ex. « cf. ii ») — les convertir aussi demanderait de détecter
quels mots dans le texte anglais sont des mots inuktitut, nettement plus complexe, hors
scope pour l'instant. Note : contrairement au tableau de décomposition, une recherche
Spalding n'a pas toujours de `DecomposeState.Success.enteredScript` disponible (le
lookup Spalding tourne même quand la décomposition échoue) — il faudra calculer le
script d'entrée séparément, probablement via `TransCoder.textScript(word)` appliqué
directement au mot tapé.

**Test à écrire pour ça** : un test qui confirme que le mot-vedette affiché pour une
entrée Spalding change effectivement de script (romain ↔ syllabique) selon le réglage
choisi — même esprit que `LanguageSwitchUiTest.kt` pour la langue de l'interface, mais
pour le script d'affichage.

Uqausiit et Tusaalanga restent de vrais fetchers réseau à implémenter ensuite. Pour
Tusaalanga, Alain a confirmé le mécanisme de requête : `https://tusaalanga.ca/glossary?l=N`
retourne la liste complète des mots commençant par la lettre `N` — une URL à
paramètres (pas de formulaire JS/POST à reproduire), mais organisée par lettre plutôt
que par mot exact. Le fetcher devrait donc : déterminer la première lettre du mot
recherché, récupérer la page de cette lettre, puis chercher le mot exact dans la liste
retournée. Reste à inspecter en direct : la structure HTML de cette liste, et combien de
lettres/pages ça représente au total — si le glossaire est petit (comme Spalding),
**la même approche « parser une fois, embarquer en JSON »** pourrait s'appliquer ici
aussi plutôt que de refaire un appel réseau par lettre à chaque mot recherché ; à
confirmer une fois la taille réelle connue. Uqausiit, lui, reste entièrement à inspecter
(mécanisme de requête inconnu) ; à faire après Tusaalanga (son dictionnaire d'affixes
complète bien la décomposition morphologique, mais Tusaalanga est plus proche d'être
prêt à coder).

**On commence par une seule source** : valider d'abord le mécanisme complet (parsing +
JSON embarqué pour Spalding, court-circuit sur correspondance exacte, gestion d'erreur,
injection dans le message, interprétation par Claude) avant de brancher les autres. Si
quelque chose bloque, ça isole si le problème vient du mécanisme général ou d'une
particularité d'une source donnée. Ajouter les dictionnaires restants devient ensuite un
travail mécanique : un nouveau fetcher + une entrée dans la liste des sources à
interroger.

⚠️ **La structure de la liste par lettre de Tusaalanga, et le mécanisme de requête
d'Uqausiit au complet, restent à inspecter en direct** (outils de développement du
navigateur, même démarche que prévue pour le Hansard en Phase 4) avant d'écrire leurs
fetchers — impossible à faire depuis le bac à sable de développement de ce spike, qui
bloque l'accès Internet général.

**Exécution en parallèle** : une fois plus d'une source réseau active, lancer leurs
fetchers en parallèle (coroutines Kotlin, `async`/`awaitAll`) plutôt qu'en séquence,
pour garder une latence totale raisonnable malgré le nombre de sources consultées à
chaque mot. Pas besoin d'annulation anticipée : l'appli attend simplement toutes les
réponses du palier en cours avant de décider (voir « Court-circuit sur correspondance
exacte » plus haut) — plus simple à coder qu'une logique de course avec annulation.

### Phase 4 — Recherche dans le Hansard

**Deuxième palier, atteint seulement si aucun dictionnaire (Phase 3) n'a trouvé le mot
exact** (voir « Court-circuit sur correspondance exacte » en Phase 3). Même principe
qu'en Phase 3 sinon : un fetcher Kotlin dédié pour
https://www.inuktitutcomputing.ca/NunavutHansard/, dont le résultat est ajouté au
message envoyé à Claude, avec les résultats de gov.nu.ca (Phase 5) une fois cette
dernière codée — les deux fetchers de ce palier tournent en parallèle entre eux, comme
ceux des dictionnaires.

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

**Complète le deuxième palier commencé en Phase 4** (Hansard) — atteint seulement si
aucun dictionnaire n'a trouvé le mot exact en Phase 3, comme le Hansard. Même principe
qu'aux Phases 3-4 sinon : un fetcher Kotlin qui interroge (probablement via une
recherche restreinte au domaine, ex. `site:gov.nu.ca ...`, ou le moteur de recherche
interne du site s'il y en a un) et retourne les pages bilingues pertinentes trouvées ;
résultat ajouté au message envoyé à Claude avec celui du Hansard — la quatrième
heuristique du workflow d'origine.

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
- Pas d'heuristique d'arrêt anticipé floue de type « ça semble suffisant » — l'appli
  interroge systématiquement toutes les sources activées à chaque mot. Seule exception,
  volontairement simple et déterministe (pas une heuristique) : le court-circuit sur
  correspondance exacte décrit en Phase 3, qui arrête tout et saute même l'appel à
  Claude quand un dictionnaire donne déjà la réponse exacte
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
- **Comportement du bouton « Guess Meaning »** : au clic, l'appli :
  1. Appelle l'analyseur morphologique Kotlin natif sur le mot (Phase 2)
  2. À partir de la Phase 3, interroge en parallèle toutes les sources dictionnaires
     activées (palier 1 : Spalding, Uqausiit, Tusaalanga) et attend qu'elles aient
     toutes répondu
  3. Si au moins une a trouvé une définition pour le mot exact, affiche directement
     cette définition — pas d'appel à Claude
  4. Sinon, à partir des Phases 4-5, interroge en parallèle le palier 2 (Hansard,
     gov.nu.ca), puis génère **automatiquement** le contenu du champ de saisie du chat,
     en combinant : les instructions préétablies (dans le champ `system` de la requête,
     séparé du message utilisateur), la décomposition morphologique, et les résultats
     bruts des deux paliers

  Le message **n'est pas envoyé automatiquement** — l'utilisateur peut relire/modifier
  ce texte généré avant de l'envoyer manuellement (bouton « Envoyer » séparé), pour
  ajuster le prompt à la volée pendant les tests, sans avoir à tout retaper ni à
  copier-coller depuis une autre source.
- Claude ne fait plus de recherche lui-même — au plus un appel API par mot (pas de
  boucle d'outils), et même cet appel est évité complètement en cas de correspondance
  exacte
- Un indicateur de chargement pendant la collecte des sources (recherche locale +
  fetchers réseau en parallèle) puis, si nécessaire, pendant l'appel réseau à Claude
- Un message d'erreur visible si une source réseau échoue (voir « Gestion des échecs »
  en Phase 3 et « Fetchers par source » ci-dessous)

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

**Fetchers par source (Phases 3-5)** : un module Kotlin par source, responsable de
retourner le texte pertinent pour le mot recherché — soit via une recherche locale dans
un JSON embarqué (Spalding, voir Phase 3), soit via un appel HTTP réel (Tusaalanga,
Uqausiit, Hansard, gov.nu.ca) — y compris détecter une correspondance exacte avec le mot
recherché pour les dictionnaires (voir le court-circuit en Phase 3). Organisés en deux
paliers : palier 1 (dictionnaires, Phase 3), palier 2 (Hansard + gov.nu.ca, Phases 4-5),
atteint seulement si le palier 1 n'a rien trouvé d'exact. Les fetchers d'un même palier
sont lancés en parallèle (coroutines), qui attendent toutes les réponses avant de
décider. Chaque fetcher réseau est indépendant : l'échec d'un seul (site indisponible,
structure changée) affiche un message d'erreur **en build debug seulement**
(`BuildConfig.DEBUG` — le but est d'avertir les développeurs, pas de présenter une
erreur à un futur utilisateur final) pour cette source, et n'empêche pas les autres de
fonctionner ni l'appel à Claude si au moins une autre source a réussi (voir « Gestion
des échecs » en Phase 3). La source locale (Spalding) ne peut essentiellement pas
échouer au runtime de la même façon. Chaque fetcher a aussi son propre test dédié,
interrogeant la vraie source (voir « Un test par fetcher » en Phase 3).

**Notification de panne (idée retenue, pas construite dans ce spike)** : quand un
fetcher réseau échoue de façon inattendue (ex. la structure HTML d'un site a changé et
le parsing casse), l'idée à terme est que l'appli envoie une notification à un serveur
sur iutools.org, qui avertirait Alain pour corriger le code du fetcher concerné. Une
fois corrigé et publié, l'appli afficherait un message recommandant une mise à jour.
**Hors scope pour ce spike** — aucun serveur n'existe encore ; pour l'instant, un
fetcher en échec se contente d'afficher son message d'erreur (voir « Gestion des
échecs » en Phase 3) et éventuellement de logger l'erreur localement. Prévoir malgré
tout un point d'accroche clair dans le code (ex. une fonction
`reportFetcherFailure(source, error)` avec une implémentation vide/log pour l'instant)
pour que brancher le vrai mécanisme plus tard soit un ajout, pas une réécriture.

**Hansard (Phase 4 seulement)** : voir la note dans cette phase — le fetcher doit
reproduire l'appel réel du site si celui-ci n'accepte pas de recherche par URL.

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
- Le script de parsing Spalding produit un JSON embarqué correct (vérifié contre la
  page source, pas juste "ça compile") ; le fetcher local le recherche correctement et
  gère proprement l'absence de résultat
- Le court-circuit sur correspondance exacte fonctionne au niveau du palier
  dictionnaires au complet : l'appli attend toutes les réponses (Spalding, puis
  Uqausiit et Tusaalanga une fois codés) avant de décider ; un mot trouvé tel quel dans
  au moins l'une d'elles affiche directement sa ou ses définitions, sans appel à Claude
- Un échec d'une source réseau (simulé) affiche un message d'erreur clair en build
  debug, sans faire planter l'appli
- Quand aucun dictionnaire n'a de correspondance exacte, le résultat des sources
  consultées est correctement inclus dans le message envoyé à Claude, et Claude
  l'interprète correctement dans sa réponse
- Les fetchers réseau (Uqausiit, Tusaalanga, Asuilaak si confirmé) suivent le même
  pattern et sont exécutés en parallèle sans ralentir excessivement le temps de réponse
  total
- Un échec d'une source réseau parmi plusieurs actives n'empêche pas les autres ni
  l'appel à Claude de fonctionner
- Chaque fetcher a son test dédié (voir « Un test par fetcher ») ; au moins celui de
  Spalding tourne dans la suite de tests JVM normale du projet

**Phase 4** :
- Le fetcher Hansard n'est appelé que lorsqu'aucun dictionnaire n'a trouvé le mot exact
  en Phase 3 (confirmé par un cas de test où un dictionnaire trouve le mot, et un autre
  où aucun ne le trouve)
- Confirmation documentée de comment le Hansard traite les requêtes (URL à paramètres
  vs formulaire JS/POST), et fetcher implémenté en conséquence
- Claude est capable de repérer l'équivalent anglais d'un mot inuktitut à l'intérieur
  d'une paire de phrases alignées retournée par la recherche Hansard
- Comparaison globale : pour un cas comme ᐱᕈᖅᐸᓪᓕᐊᕐᓂᕐᒥᒃ / piruqpalliarnirmik (déjà testé
  manuellement), la qualité/pertinence de l'hypothèse finale se compare raisonnablement
  à celle obtenue lors des simulations manuelles

**Phase 5** :
- Le fetcher gov.nu.ca retourne une ou des pages bilingues pertinentes pour un mot testé
- L'heuristique de qualité de recherche portée depuis le projet iutools original
  (privilégier les pages ayant une traduction) est appliquée dans le fetcher, pas
  laissée à l'appréciation de Claude
- Claude sait interpréter des pages de contenu variées (pas juste des paires de
  phrases) à partir de ce que le fetcher lui fournit
- Un échec du fetcher gov.nu.ca (parmi les autres sources actives) affiche une erreur
  pour cette source sans bloquer les autres

**Phase 6** :
- **Bloquant** : le modèle choisi (Gemma 3n E2B, repli Gemma 3 1B) charge et tourne sans
  crash sur l'appareil de test réel, avec une marge de mémoire libre raisonnable —
  sinon, documenter l'échec et arrêter cette phase plutôt que de forcer
- Le modèle local produit un verdict raisonnable à partir du même contexte pré-rassemblé
  (décomposition + résultats des fetchers réutilisés des Phases 3-5) que celui donné à
  Claude — aucun tool-calling requis, donc rien de spécifique à valider ici au-delà de
  la lecture/synthèse
- Comparaison documentée avec les résultats des Phases 1-5 : qualité des hypothèses,
  latence, et le compromis coût/connectivité (gratuit et hors-ligne pour l'appel au
  modèle vs payant et nécessitant une connexion) — notant que les fetchers eux-mêmes
  continuent d'appeler des sites externes dans les deux versions (sauf Spalding, local)

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
8. Obtenir un accès réel à https://www.inuktitutcomputing.ca/Spalding/index.php?lang=en
   et inspecter sa structure HTML (structure de chaque entrée, nombre approximatif
   d'entrées, pagination ou non) — pas faisable depuis le bac à sable de développement
   de ce plan
9. Écrire un script (jamais de retranscription manuelle) qui parse cette page en un
   JSON mot→définition, et vérifier le résultat contre la page source
10. Embarquer ce JSON dans les ressources de l'appli ; écrire le fetcher Spalding
    (recherche locale dans le JSON, incluant la détection de correspondance exacte)
11. Implémenter le court-circuit : attendre les réponses de tout le palier dictionnaires
    puis, si au moins une correspondance exacte a été trouvée, afficher directement la
    ou les définitions — pas d'appel à Claude
12. Implémenter la gestion d'erreur pour les futurs fetchers réseau (message d'erreur
    visible en build debug en cas d'échec)
13. Écrire le test dédié du fetcher Spalding (voir « Un test par fetcher »)
14. Brancher le résultat (si aucune correspondance exacte) dans le message envoyé à
    Claude, à la suite de la décomposition morphologique
15. Tester de bout en bout, puis répéter pour Uqausiit et Tusaalanga (et Asuilaak si son
    URL est confirmée) — ceux-ci nécessitent d'abord d'inspecter leur mécanisme de
    requête (voir la note dans le corps de la Phase 3) — en lançant tous les fetchers
    réseau du palier en parallèle (coroutines), sans annulation anticipée

**Phase 4** :
16. Inspecter comment https://www.inuktitutcomputing.ca/NunavutHansard/ traite les
    requêtes (outils de développement du navigateur)
17. Écrire le fetcher Kotlin en conséquence (URL à paramètres, ou reproduction de
    l'appel POST/formulaire sinon), et son test dédié (voir « Un test par fetcher »)
18. Mettre à jour le prompt système avec les instructions sur le Hansard et le défi
    d'alignement iu-en
19. Tester sur les cas déjà utilisés dans les simulations manuelles

**Phase 5** :
20. Retrouver et étudier la classe du projet iutools original qui implémente
    l'heuristique de recherche (maximisation des chances de tomber sur une page
    traduite) — porter sa logique dans le fetcher, pas la réinventer
21. Écrire le fetcher Kotlin pour gov.nu.ca, et son test dédié
22. Brancher son résultat dans le message envoyé à Claude
23. Tester sur les cas déjà utilisés dans les simulations manuelles

**Phase 6** :
24. Ajouter la dépendance MediaPipe GenAI au projet (sur une branche séparée, ou un
    module optionnel, pour ne pas alourdir l'appli principale si cette phase échoue)
25. Câbler le téléchargement + chargement d'un modèle `.task` (Gemma 3n E2B d'abord)
26. **Test bloquant** : mesurer la mémoire disponible réelle sur l'appareil de test
    avant d'aller plus loin ; si insuffisant, essayer Gemma 3 1B ; si toujours
    insuffisant, documenter l'échec et arrêter
27. Réutiliser directement les fetchers déjà écrits en Phases 3-5 pour rassembler le
    contexte (recherche locale Spalding + fetchers réseau) -- rien à reconstruire ici
28. Rebrancher le prompt système déjà validé en Phases 2-5 (adapté si nécessaire au
    format d'appel de MediaPipe)
29. Tester sur les mêmes cas que les phases précédentes et comparer

**Après les 6 phases** : documenter les résultats (temps de réponse par phase, coût par
requête pour la version Claude, qualité des hypothèses de sens, tout crash ou
limitation observée) pour informer la suite (est-ce qu'un modèle local est viable pour
ce cas d'usage, ou est-ce que la version cloud devient le chemin principal avec le
modèle local comme option secondaire).

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
- **Abstraction commune d'outils entre Claude et un modèle local, si l'appli redevient
  agentique un jour** : plus nécessaire dans l'architecture actuelle (Phases 3-6) — les
  fetchers sont déjà de simples fonctions Kotlin réutilisées telles quelles par les deux
  backends, aucun tool-calling nulle part. Pertinent seulement si une itération future
  redonne au LLM (cloud ou local) le contrôle direct de quand/quoi interroger via de
  vrais appels d'outils — auquel cas une petite couche d'abstraction interne (nom
  d'outil, schéma JSON, fonction `execute()`) traduite vers le format natif de chaque
  backend (function-calling de MediaPipe d'un côté, `tools` natif de l'API Claude de
  l'autre) donnerait le même bénéfice de portabilité multi-modèles que MCP sans la
  lourdeur du protocole client-serveur.
  **MCP ne serait à reconsidérer que si** l'appli avait un jour besoin de se connecter
  à un serveur MCP tiers déjà existant (ex. un serveur MCP officiel pour un
  dictionnaire ou une base terminologique inuktitut, si un tel service apparaît) —
  pas pour orchestrer des outils écrits et maintenus dans ce projet.
