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
