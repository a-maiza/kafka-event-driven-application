# Claude Code – Plan Mode, Ask Mode & Edit Automatically

Ce document résume l’utilisation stratégique des différents **modes de Claude Code** pour accélérer et structurer le développement logiciel, depuis la planification jusqu’à l’implémentation et les tests.

---

## Présentation de Claude Code

Claude Code est un assistant de développement utilisable :
- dans le **terminal**
- dans **VS Code** via une extension dédiée  

Il peut analyser un codebase, proposer des plans, modifier des fichiers et accompagner les workflows Git, selon le **mode actif**.

Le changement de mode se fait avec **Shift + Tab**.

---

## Les différents modes

### 1. Plan Mode

**Objectif : réfléchir et structurer sans modifier le code.**

Claude peut :
- analyser le code existant
- proposer des architectures
- découper le travail en tâches
- produire des timelines et roadmaps

#### Cas d’usage typiques
- Démarrer un nouveau projet
- Comprendre un codebase existant
- Préparer une implémentation complexe multi-fichiers
- Explorer une architecture avant toute modification

#### Exemples de prompts
- *Give me an overview of this codebase*
- *Explain the main architecture patterns*
- *What are the key data models?*
- *Find the files that handle user authentication*
- *Create a list of tasks from requirements.md*

Les plans peuvent être exportés dans des fichiers Markdown servant de référence (ex : `tasks.md`).

---

### 2. Ask Mode (Ask Before Edits – mode par défaut)

**Objectif : implémenter avec contrôle humain.**

Dans ce mode :
- Claude propose des **diffs**
- Chaque modification doit être **explicitement approuvée**
- Les commandes bash et Git nécessitent validation

#### Avantages
- Contrôle fin sur chaque changement
- Idéal pour :
  - initialisation de projet
  - création de fichiers
  - configuration Git
  - premières implémentations

#### Exemple
- *Go ahead and complete task one from the tasks file*
- Création des dossiers frontend/backend
- Génération de `.gitignore`
- Initialisation du repo Git
- Commit et push vers GitHub

---

### 3. Edit Automatically Mode

**Objectif : accélérer l’édition de code.**

Dans ce mode :
- Claude modifie les fichiers **automatiquement**
- Toujours pas d’exécution bash ou Git sans validation
- Idéal pour enchaîner rapidement des tâches définies

#### Bonnes pratiques
- Être très précis dans les prompts
- Demander explicitement :
  - création de branches
  - pull requests par tâche
  - portée exacte de l’implémentation

#### Exemple
- *Implement the next task from tasks*
- *Create a new branch and a pull request for this task*

---

## Exemple pratique : projet WellTrack

### Contexte
- Application pour personnes atteintes de maladies chroniques
- Fonctionnalités : suivi des symptômes, humeurs, médicaments, habitudes, tendances
- Stack :
  - Frontend : React
  - Backend : Node.js, Express
  - Base de données : PostgreSQL

### Workflow utilisé
1. **Plan Mode**
   - Génération d’un plan de tâches depuis `requirements.md`
   - Découpage adapté à un développeur intermédiaire
   - Timeline ~12 semaines jusqu’au beta launch

2. **Ask Mode**
   - Implémentation de la tâche 1 (initialisation projet)
   - Mise en place du repo Git
   - Premier commit

3. **Edit Automatically Mode**
   - Implémentation des tâches suivantes
   - Création automatique de branches et PR
   - Suivi de progression dans `tasks.md`

---

## Tests et vérification

Pour valider le code généré :
1. Passer en **Plan Mode** pour définir une stratégie de tests
2. Demander :
   - complétion des tâches restantes
   - écriture de tests unitaires et d’intégration
3. Passer en **Ask Mode** pour exécuter le plan et valider les changements

---

## Points clés à retenir

- **Plan Mode** → réfléchir, comprendre, structurer
- **Ask Mode** → implémenter avec contrôle
- **Edit Automatically** → aller vite sur des tâches bien définies
- Les fichiers Markdown (plans, tâches) servent de contrat entre vous et Claude
- La qualité des résultats dépend directement de la **précision des prompts**

---

En combinant intelligemment ces modes, Claude Code devient un véritable copilote de développement, du cadrage initial jusqu’aux tests et à l’automatisation du workflow.



# /init et le fichier CLAUDE.md

## Introduction

Lorsque nous utilisons Claude Code pour planifier et exécuter des tâches, un problème revient souvent :  
nous devons sans cesse répéter les mêmes instructions.

- Créer une branche pour chaque tâche  
- Ne pas oublier d’écrire des tests  
- Respecter les conventions du projet  
- Suivre un workflow Git précis  

Et si Claude pouvait **se souvenir automatiquement** de tout cela ?  
C’est exactement ce que permettent la commande **/init** et le fichier **CLAUDE.md**.

---

## Qu’est-ce que la commande `/init` ?

La commande `/init` sert à **initialiser le contexte d’un projet pour Claude**.

Lorsqu’elle est exécutée :
- Claude analyse la structure du projet
- Il inspecte les fichiers (package.json, configurations, arborescence, etc.)
- Il déduit le type de projet, la stack technique et l’architecture
- Il génère un fichier `CLAUDE.md` à la racine du projet (avec votre accord)

---

## Le fichier `CLAUDE.md`

Le fichier `CLAUDE.md` est un fichier Markdown généré automatiquement par Claude.  
Il contient notamment :

- Une vue d’ensemble du projet
- La stack technique
- Les commandes courantes
- L’architecture
- Les endpoints d’API
- Le modèle de données

Mais attention : **ce n’est pas qu’une simple documentation**.

---

## Pourquoi `CLAUDE.md` est si important ?

Claude fonctionne avec une **fenêtre de contexte** (sa mémoire de travail).

Cette mémoire :
- Inclut la conversation en cours
- Les fichiers ouverts
- Les réponses de Claude
- A une taille limitée (environ 100 000 à 200 000 tokens)

Sans `CLAUDE.md` :
- Chaque conversation démarre avec un contexte vide
- Il faut réexpliquer la stack, les conventions et les workflows
- Claude peut oublier des informations au fil de la discussion

Avec `CLAUDE.md` :
- Le fichier est **chargé en premier**, avant toute conversation
- Il est **ancré en haut de la mémoire** de Claude
- Le contexte du projet est toujours disponible, même dans des discussions longues

---

## Une analogie simple

Considérez `/init` comme l’onboarding d’un nouveau développeur :

- On lui fait visiter le codebase
- On lui explique comment l’équipe travaille
- On lui montre les conventions et les règles

C’est exactement ce que fait `/init` pour Claude.

---

## Personnaliser `CLAUDE.md`

Le fichier généré automatiquement n’est qu’un **point de départ**.

Claude se base uniquement sur ce qu’il peut déduire du code, mais :
- Vous connaissez les conventions internes
- Les décisions d’architecture
- Les workflows spécifiques
- Les attentes de l’équipe

👉 **Il est fortement recommandé d’éditer `CLAUDE.md` manuellement** pour y ajouter ce contexte.

---

## Exemple : automatiser le workflow Git

Dans notre projet, nous devions régulièrement rappeler à Claude :
- De créer une branche par tâche
- De faire des commits atomiques
- De créer une Pull Request
- De mettre à jour le fichier `TASKS.md`

Au lieu de le répéter à chaque prompt, nous avons ajouté une section **Workflow Git** dans `CLAUDE.md` :

- Création d’une branche avec une convention de nommage
- Commits atomiques
- Création automatique d’une PR en fin de tâche
- Mise à jour de `TASKS.md`

Résultat :
- Claude applique le workflow automatiquement
- Plus d’oublis
- Des commits cohérents
- Des PR prêtes pour la revue de code

---

## Workflow recommandé

1. Exécuter `/init` pour générer une base
2. Ouvrir et enrichir `CLAUDE.md` avec le contexte spécifique du projet
3. Committer `CLAUDE.md` dans le dépôt
4. Partager le fichier avec toute l’équipe

---

## Bénéfices clés

- ✅ Contexte projet persistant
- ✅ Moins de répétitions dans les prompts
- ✅ Workflows cohérents
- ✅ Gain de temps
- ✅ Comportement uniforme de Claude pour toute l’équipe

---

## Conclusion

Le fichier `CLAUDE.md` est bien plus qu’une documentation :  
c’est une **configuration de comportement** pour Claude.

Vous définissez les règles une seule fois,  
et Claude les suit **à chaque conversation**.

C’est toute la puissance de `/init`.


# Test-driven Iteration avec Claude Code

## Introduction

L’un des patterns les plus puissants avec Claude Code est la **test-driven iteration**  
(itération pilotée par les tests).

Le principe est volontairement simple, mais extrêmement efficace :
1. Claude écrit le code
2. Claude écrit les tests
3. Claude exécute les tests
4. Si un test échoue, Claude corrige le code
5. Le cycle recommence jusqu’à ce que tout passe

👉 **Écrire → Tester → Corriger → Répéter**  
C’est ainsi que l’on obtient du code fiable avec Claude.

---

## Pourquoi l’itération pilotée par les tests est essentielle

Sans ce cycle :
- Le code *a l’air* correct
- Des bugs subtils peuvent passer inaperçus
- Les comportements limites ne sont pas couverts

Avec ce cycle :
- Le code est réellement validé
- Les régressions sont évitées
- Les changements sont sécurisés par les tests

C’est la différence entre du code plausible et du code réellement fonctionnel.

---

## Configuration dans le fichier `CLAUDE.md`

Pour activer ce comportement automatiquement, il suffit de le définir **une seule fois** dans le fichier `CLAUDE.md`.

Nous avons ajouté une section dédiée aux tests, par exemple :

### Exigences de tests

- Toute fonctionnalité doit être couverte par des tests
- Les tests doivent être exécutés avant de marquer une tâche comme terminée
- **Règle clé : corriger le code, pas les tests**
  - Les tests ne doivent être modifiés que s’ils sont incorrects
  - Il est interdit d’affaiblir un test simplement pour le faire passer

Cette règle est fondamentale.

---

## Une règle essentielle : corriger le code, pas les tests

Lorsqu’un test échoue, la tentation est grande de :
- Assouplir l’assertion
- Supprimer le test
- Modifier le test pour qu’il passe

Mais cela annule complètement l’intérêt des tests.

👉 **Le test définit le comportement attendu.**  
👉 **Le code doit s’y conformer.**

Claude doit donc :
- Identifier la cause réelle de l’échec
- Corriger l’implémentation
- Relancer les tests
- Répéter jusqu’à validation complète

---

## Exemple concret

Nous avons demandé à Claude :

> Implémenter l’endpoint `POST /api/auth/register` (tâche 1.3)  
> et s’assurer que tous les tests passent avant de marquer la tâche comme terminée.

### Résultat

Claude a automatiquement :
- Créé des fonctions utilitaires
- Ajouté la validation des données
- Implémenté la route d’authentification
- Écrit **8 nouveaux tests**
- Exécuté **16 tests au total**
- Vérifié que **tous les tests passent**

Une fois la validation terminée :
- Claude a créé le commit
- Puis ouvert la Pull Request correspondante

Aucune intervention manuelle n’a été nécessaire.

---

## Le vrai avantage

Le plus puissant dans ce workflow, c’est que :
- Vous n’avez qu’à demander **une seule fois** :  
  *« Assure-toi que tous les tests passent »*
- Claude gère ensuite tout le cycle automatiquement
- Le comportement est constant d’une tâche à l’autre

Tout cela fonctionne parce que les règles sont définies dans `CLAUDE.md`.

---

## Résumé du cycle d’itération

1. Implémenter la fonctionnalité
2. Écrire les tests
3. Exécuter les tests
4. Corriger le code si nécessaire
5. Recommencer jusqu’à succès
6. Créer le commit et la PR

---

## Bénéfices clés

- ✅ Code réellement testé
- ✅ Moins de bugs subtils
- ✅ Comportement reproductible
- ✅ Automatisation complète du workflow
- ✅ Confiance accrue dans les changements

---

## Conclusion

La **test-driven iteration** est la clé pour obtenir du code fiable avec Claude Code.

Sans elle, vous obtenez du code qui *semble* correct.  
Avec elle, vous obtenez du code **validé, testé et prêt pour la production**.

Et grâce au fichier `CLAUDE.md`,  
ce workflow devient automatique, cohérent et partagé par toute l’équipe.


# Limites de contexte de Claude Code

## Comprendre la fenêtre de contexte

Claude fonctionne avec une **fenêtre de contexte de taille fixe**, d’environ **200 000 tokens**,  
ce qui représente approximativement **600 pages de texte**.

Cette fenêtre contient tout ce que Claude peut utiliser pour raisonner :
- La conversation en cours
- Les fichiers chargés
- Les réponses précédentes
- Les fichiers mémoire comme `CLAUDE.md`

---

## Analogie : un bureau de travail

On peut comparer la fenêtre de contexte à un **bureau** :

- Vous ne pouvez poser qu’un nombre limité de documents
- Si vous en ajoutez trop, certains tombent
- Plus vous chargez de fichiers ou de messages, moins il reste de place
- Les éléments anciens peuvent être expulsés pour laisser place aux nouveaux

À mesure que le projet et les conversations grandissent, ces limites deviennent inévitables.

---

## Vérifier l’utilisation du contexte

Claude Code permet d’inspecter l’état du contexte avec la commande :

```bash
claude --resume
/context
```
## Détails du rapport /context

### Le rapport /context fournit les informations suivantes :

-  Modèle utilisé
-  Contexte total consommé Exemple : 21k / 200k tokens
- Ventilation du contexte par catégories

### System prompt & system tools

- Paramètres système internes
- Non modifiables par l’utilisateur

### Memory files

- Fichiers CLAUDE.md
- Possibilité d’avoir plusieurs fichiers CLAUDE.md par sous-dossier
- Toujours chargés automatiquement au démarrage d’une conversation

### Reference files

- Fichiers comme TASKS.md
- Chargés uniquement lorsqu’ils sont explicitement demandés

### Messages

- Historique de la conversation
- Augmente rapidement lors de sessions longues

### Autocompact buffer

- Zone tampon réservée
- Sert de seuil de déclenchement pour la compaction automatique


## Compaction automatique (Autocompact)

Lorsque l’historique de la conversation devient trop volumineux :

- Claude détecte que les messages empiètent sur le buffer autocompact
- Les parties les plus anciennes de la conversation sont automatiquement résumées
- De l’espace est libéré dans la fenêtre de contexte

Exemple

- Les messages atteignent environ 120k tokens (≈ 60 % du contexte total)
- Claude déclenche automatiquement la compaction
- L’historique est résumé
- Les messages redescendent à environ 40k tokens

Ce mécanisme permet de continuer à travailler sans perdre totalement le contexte.


## Effets négatifs d’un contexte trop large

Un contexte excessivement chargé peut entraîner :

- 📈 Une forte consommation de tokens
- 🎯 Une baisse de la qualité ou de la pertinence des réponses
- 🔁 Des comportements répétitifs ou des erreurs
- 🐢 Des temps de réponse plus lents

## Stratégies pour travailler avec les limites de contexte
### 1. Utiliser efficacement CLAUDE.md

- Générer le fichier avec la commande /init
- L’enrichir progressivement avec :
- L’architecture du projet
- Les conventions
- Les workflows

Ce fichier est toujours chargé en priorité dans le contexte

### 2. Travailler par tâches ciblées

Éviter les demandes trop larges ou vagues.

- ❌ Refactoriser tout le backend
- ✅ Refactoriser uniquement le module d’authentification

Les tâches petites et bien définies produisent des résultats plus fiables.

### 3. Inclure explicitement les fichiers nécessaires

Utiliser la syntaxe @fichier pour indiquer précisément les fichiers concernés.

Avantages :

- Réduction du bruit contextuel
- Plus d’espace disponible pour le raisonnement
- Meilleure concentration de Claude sur le problème à résoudre

### 4. Créer des résumés par module

Pour les parties complexes du codebase :

- Générer un résumé du module
- Décrire :
   - Les fichiers impliqués
   - Leurs relations
   - Les patterns utilisés

Ces résumés peuvent être réutilisés dans de futures conversations.

### 5. Démarrer de nouvelles conversations

- Fractionner les gros chantiers en plusieurs sessions
- Éviter l’accumulation d’historique inutile
- Repartir avec un contexte propre lorsque nécessaire

## Principe clé

Ne luttez pas contre la fenêtre de contexte.
Travaillez avec elle.

Des prompts ciblés, une bonne documentation et des références précises sont bien plus efficaces que charger l’intégralité du codebase à chaque échange.

## Conclusion

Les limites de contexte de Claude sont réelles, mais maîtrisables.

En combinant :

- CLAUDE.md
- Des tâches bien découpées
- Des fichiers explicitement référencés
- Des résumés intelligents

Vous obtenez des conversations plus rapides, plus fiables et plus efficaces.

👉 Moins de bruit, plus de signal.

























prompts:
1 - Can you create a list of tasks to implement the technical requirements from @REQUIREMENTS.md that an intermediate developer would be comfortable with? (Optional), Put those tasks in a Tasks.md file.

2- Go ahead and complete the first task from Tasks.md

3- Can you first create a .gitignore for the tech staks being used and initialize a git repository in this deirectory ? also set yhe origin to -  https://github.com/a-maiza/kafka-event-driven-application.git

4- Create an initial commit.

5 - we have a new develpper joining the team and i want to show them the authentication process.Can you analysze the authetfication flow across all related files. Trace how a login request flows from the rout handler throuth to the database and back to thr response. incluse all filles involved.

I'd also like the show the new developer what compenet depend on the User model. Show me the full dependency chain- everything that import or uses User directly or inderectly.

 ## Git Worflow
 when compeleting tasks from TAsks.md:
 1. Create a new branch named `<branch-type>/<task-number>-<brief-description> befor starting work
 2. Make atomic commit with
conventional commit message:
   -feature: for new features
   -fix: for bug fixes
   -docs: for documentation
   -test: for test
   -refactor: for refactoring
3. After compelting a task, create a pull request with :
   -A descriptive title matching the task
   -A summary of changes made
   -Any testing note or considiration

4. Update the task chechbox in Tasks.md to mark it complete 

## Testing Requirements
Befor marking any task as complete: 
1. Whrite unit tets for new functionality
2. Run the full test suite with : `mvn test`
3. If tets fail : 
   - Analyse the failer output
   - Fix the code (no the tets, unless tests are incorrect)
   - Re-run tests untail all pass


## Documentation Requirement
### README.md
Keep updated with 
   - Quick start instruction (clone, install, run)
   - Environement variable table with descriptions
   - Available Services and what that they do

Update README when
   - Adding new Feature or endpoints
   - Changing environement variable
   - Adding the dependencies


