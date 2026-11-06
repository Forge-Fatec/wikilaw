# WikiLaw — Documentation

This repository contains the documentation, project guidelines, and organizational materials for the **WikiLaw** project.

The main project repository can be accessed here:

**[WikiLaw — Main Repository](https://github.com/Forge-Fatec/wikilaw)**

---

## 📌 Project

WikiLaw is a legal information platform developed as part of the FATEC project, focused on providing accessible and reliable access to legal information such as:

* Jurisprudence
* Legal precedents
* Legal doctrine

This repository is dedicated specifically to the **documentation and organizational materials** of the project.

---

## 🔄 Development Methodology

The WikiLaw team follows **Agile development practices using Scrum**.

The project is organized around:

* Sprints
* User Stories
* Tasks
* Acceptance Criteria
* Sprint Planning
* Daily Meetings
* Sprint Reviews
* Retrospectives

All team members are expected to follow the agreed Scrum workflow and keep project documentation up to date.

### Weekly Workload

Each team member is expected to dedicate **10 hours per week** to the WikiLaw project.

These hours may be distributed according to the team's sprint planning and the member's assigned responsibilities.

---

## ⏱️ Work Schedule

Project activities are carried out exclusively on **business days**.

The team does **not** work on:

* Saturdays
* Sundays
* Public holidays

Team members are not expected to perform project activities during weekends or public holidays.

The 10 weekly hours should therefore be organized throughout the available business days according to each member's assigned tasks and sprint responsibilities.

---

## 🌙 Meetings

Project meetings will take place **only during the evening period**, preferably during the scheduled college class hours.

Meetings may take place through:

* **Discord** — for remote meetings and collaboration.
* **FATEC classrooms** — for in-person meetings during scheduled classes.

Meetings outside the established evening/class schedule should not be expected from team members unless previously agreed upon by the entire team.

---

## 💬 Communication

### WhatsApp

**WhatsApp** is the team's primary communication channel.

It should be used for:

* General communication
* Quick questions
* Notifications
* Task coordination
* Availability updates
* Important announcements

### Discord

**Discord** is primarily used for:

* Scrum meetings
* Remote team meetings
* Voice communication
* Collaborative work sessions

### College Classrooms

FATEC classrooms are also part of the project's working environment.

When the team is physically together during scheduled classes, project discussions, planning, and meetings may be conducted in the classroom.

---

## 💤 Availability & Ongoing Participation

Team members may occasionally have periods in which they are unable to perform project activities.

If a member knows that they will be **temporarily idle or unavailable**, they should notify the team in the **WhatsApp group** beforehand.

Examples include:

* Academic workload
* Personal commitments
* Temporary lack of availability
* Other previously known circumstances that prevent project participation

### Prior Notice

A member who **properly communicates their temporary unavailability in the group will not receive a strike for the period covered by the notice**.

The purpose of this rule is to encourage transparency and communication rather than penalize legitimate periods of unavailability.

Members should:

1. Notify the team as soon as they know they will be unavailable.
2. Clearly communicate the expected period of inactivity when possible.
3. Keep the team informed if the situation changes.
4. Resume their responsibilities when they become available again.

Failure to communicate prolonged inactivity may be considered when applying the team's permanence rules.

---

## 🌿 Git Branching Strategy

The WikiLaw project follows a simple branch-based workflow centered around two permanent branches and temporary feature branches.

### Main Branches

| Branch    | Purpose                                                          |
| --------- | ---------------------------------------------------------------- |
| `main`    | Production-ready code.                                           |
| `develop` | Main development branch for the current project work and sprint. |

### Feature Branches

All new features and development tasks must originate from the `develop` branch.

The workflow is:

```text
main
  │
  └── develop
        │
        ├── feature/task-1
        │
        ├── feature/task-2
        │
        └── feature/task-3
```

Once a feature or task is completed, its branch must be merged back into `develop`.

```text
feature/task
      │
      ▼
   develop
```

### Sprint Completion

At the end of each sprint, the completed and validated work in `develop` is merged into `main`.

The deployment is then performed from `main`.

```text
feature/*
    │
    ▼
 develop
    │
    │  End of Sprint
    ▼
  main
    │
    ▼
 Deploy
```

### Branch Rules

1. `main` must always contain production-ready code.
2. `develop` is the primary integration branch during development.
3. Feature branches must be created from `develop`.
4. Feature branches must be merged into `develop` after completion.
5. Features should not be merged directly into `main`.
6. At the end of the sprint, `develop` is merged into `main`.
7. Deployment is performed after the sprint merge into `main`.
8. Feature branches should be removed after they have been successfully merged.

### Example

```bash
git checkout develop
git pull

git checkout -b feature/user-search
```

After completing the feature:

```text
feature/user-search → develop
```

At the end of the sprint:

```text
develop → main → deploy
```

---

## 📝 Commit Rules

All commits in this repository **must be written in English**.

The commit message must follow this format:

```text
(tag: action)
```

The `action` must be written in English and clearly describe what was done.

### Examples

```text
(docs: add scrum guidelines)
```

```text
(feat: add jurisprudence search)
```

```text
(fix: correct documentation links)
```

```text
(refactor: reorganize documentation)
```

```text
(chore: update project structure)
```

### Rules

1. Commit messages must be written **exclusively in English**.
2. The commit must follow the `(tag: action)` format.
3. The action must be concise and describe the actual change.
4. Avoid vague messages such as:

   * `(update: stuff)`
   * `(fix: things)`
   * `(change: files)`
5. Each commit should represent a meaningful change whenever possible.

---

## ⚠️ Team Permanence Rule

To maintain team organization, commitment, and participation, the project follows a **3-strike rule**.

A strike may be assigned when a member repeatedly fails to comply with established project responsibilities or rules.

Examples may include:

* Repeatedly failing to complete assigned tasks without justification.
* Consistently failing to participate in Scrum activities.
* Ignoring established repository or documentation rules.
* Repeatedly submitting work that does not follow agreed standards.
* Failing to meet agreed project responsibilities.
* Prolonged inactivity without notifying the team.
* Other actions that significantly affect the team's progress.

### Three-Strike System

| Strikes | Status                               |
| ------- | ------------------------------------ |
| 0       | Regular participation                |
| 1       | First warning                        |
| 2       | Final warning                        |
| 3       | Maximum number of strikes reached    |
| >3      | Member may be removed from the group |

A member who **exceeds 3 strikes may be removed from the project group**.

Prior communication of temporary unavailability through the WhatsApp group will **not result in a strike** for the communicated period.

The purpose of this rule is not to punish legitimate difficulties, but to ensure accountability, communication, and commitment to the project.

---

## 🤝 Team Responsibilities

Every team member is responsible for:

* Dedicating **10 hours per week** to the project.
* Working only during established business days.
* Following the project's Scrum workflow.
* Completing assigned tasks within the agreed sprint.
* Keeping documentation updated when necessary.
* Following repository and commit conventions.
* Participating actively in scheduled meetings.
* Communicating blockers or difficulties to the team.
* Informing the WhatsApp group about periods of temporary unavailability.
* Contributing to the overall progress of the project.

---

## 📂 Documentation Repository

This repository should contain project-related documentation such as:

* Scrum documentation
* Team rules
* Project decisions
* Meeting records
* Technical documentation
* Process documentation
* Other documents relevant to the development of WikiLaw

The main source code and application development are maintained in the **[WikiLaw Main Repository](https://github.com/Forge-Fatec/wikilaw)**.

---

## 🔗 Repositories

* **Main Project:** https://github.com/Forge-Fatec/wikilaw
* **Documentation:** This repository

---

## 📜 General Rule

By contributing to the project, team members agree to follow the rules defined in this document.

These rules exist to keep the project organized, transparent, and aligned with the team's Agile/Scrum workflow.

**Communication is preferred over silent inactivity.**
