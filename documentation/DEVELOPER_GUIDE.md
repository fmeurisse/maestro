# Maestro Developer Guide

Last updated: 2025-11-25

This guide helps contributors set up, build, test, and extend Maestro — a Kotlin-based workflow orchestration system using a multi-module Maven layout and Quarkus for the API.

## Table of Contents
- Project Overview
- Prerequisites
- Quick Start
- Build, Run, Test
- Module Architecture
- Development Workflow
- Coding Standards
- API Development (Quarkus)
- Domain & Core Development
- UI Development
- Database & Persistence
- Troubleshooting

---

## Project Overview

Maestro organizes workflows composed of hierarchical tasks. The project is a Kotlin + Maven multi-module repository with the following dependency flow:

```
api → core → model
ui (independent, not yet implemented)
```

Key tech:
- Java 21, Kotlin 2.2.x
- Maven (multi-module)
- Quarkus 3.29.x (REST API, CDI, Kotlin support)
- UTF-8 encoding
- PostgreSQL 18 (for 001-workflow-management)

Core concepts (model module):
- `Step` (base), `OrchestrationTask`, `Sequence`, `If`
- Work tasks like `WorkTask`, `LogTask`
- Domain entities: `WorkflowRevision`, `WorkflowRevisionID`

Related docs: see `CLAUDE.md` and `README.md` in the repo root.

---

## Prerequisites

- Java 21 (set `JAVA_HOME` accordingly)
- Maven 3.9+
- Git
- Docker (optional, for containers / future Testcontainers use)
- IDE: IntelliJ IDEA (recommended) with Kotlin + Quarkus plugins

Verify versions:

```
java -version
mvn -v
```

---

## Quick Start

1) Clone the repo and open in IntelliJ IDEA.

2) Build everything:

```
mvn clean install
```

3) Run the API in dev mode (hot reload):

```
mvn quarkus:dev -pl api
```

The API will serve on http://localhost:8080 (configurable in `api/src/main/resources/application.properties`).

---

## Build, Run, Test

Common commands (from `CLAUDE.md` with additions):

Build
```
mvn clean install          # Build all modules
mvn clean install -pl api  # Build a specific module (api, core, model, ui, plugins/*)
mvn clean package          # Package without running tests
```

Run (API)
```
mvn quarkus:dev -pl api    # Quarkus dev mode from API module
mvn quarkus:dev            # Dev mode from repo root (aggregates)
java -jar api/target/quarkus-app/quarkus-run.jar  # Run production build
```

Testing
```
mvn test                            # Run all tests (unit + integration)
mvn test -pl core                   # Run tests in a specific module
mvn test -DskipIntegTests=true      # Run only unit tests
mvn test -DskipTests=true           # Skip all tests
```

Test naming conventions:
- Unit test files end with `UnitTest.kt` (e.g., `WorkflowRevisionUnitTest.kt`)
- Integration test files end with `IntegTest.kt` (e.g., `PostgresWorkflowRevisionRepositoryIntegTest.kt`)
- Test class name must match the file name

Execution phases:
1) unit-tests → runs `*UnitTest.kt`
2) integration-tests → runs `*IntegTest.kt` (may use Testcontainers)

---

## Module Architecture

- model
  - Pure Kotlin domain model (no external deps beyond stdlib)
  - Entities: `WorkflowRevision`, `WorkflowRevisionID`
  - Task abstractions: `Step`, `OrchestrationTask`, `Sequence`, `If`, and concrete tasks (`WorkTask`, `LogTask`)

- core
  - Depends on `model`
  - Business logic, domain services
  - Repository interfaces (e.g., `IWorkflowRevisionRepository`), use cases (e.g., `ActivateRevisionUseCase`, `DeactivateRevisionUseCase`)

- api
  - Depends on `core`
  - Quarkus application (REST endpoints, CDI, Kotlin)
  - Package root: `io.maestro.api`
  - Basic endpoint at `/` (Hello World)

- ui
  - Frontend placeholder. A React/Vite-based setup exists under `ui/src/main/frontend`
  - Contains Cypress component tests (example: `RevisionActions.cy.tsx`)

- plugins/postgres
  - Postgres integration module (WIP as of 001-workflow-management)

---

## Development Workflow

Branching & commits:
- Use feature branches from `main`: `feat/...`, `fix/...`, `chore/...`, `docs/...`
- Write conventional commit messages when possible: `feat(core): add XYZ`, `fix(api): handle 400 for ...`

Pull requests:
- Include description, screenshots (for UI), and test coverage notes
- Ensure `mvn clean install` passes on CI locally before opening PR

Code review checklist:
- Clear separation across modules
- Tests follow naming conventions and cover edge cases
- No leaking of API/web frameworks into `model` and minimal into `core`
- Configuration documented in this guide or `README.md`

---

## Coding Standards

Kotlin style:
- Prefer immutable data structures; use `val` by default
- Use null-safety consciously; avoid platform types
- Keep functions small and focused; prefer expression bodies when clear
- Use data classes for value objects

Project conventions:
- UTF-8 everywhere
- Source in `src/main/kotlin`, tests in `src/test/kotlin`
- Quarkus CDI and JAX-RS annotations are all-open configured for Kotlin

Static analysis (recommended):
- ktlint or Detekt (not enforced unless configured later)

---

## API Development (Quarkus)

Where:
- `api/src/main/kotlin/io/maestro/api/...`
- Config: `api/src/main/resources/application.properties`

Run dev mode:
```
mvn quarkus:dev -pl api
```

Adding an endpoint:
1) Create a resource under `io.maestro.api` using JAX-RS (`@Path`, `@GET`, `@POST`, ...)
2) Inject services from `core` via CDI (`@Inject`)
3) Return DTOs or domain objects (prefer DTOs for public boundaries)
4) Add unit tests and API contract tests in `api/src/test/kotlin/...`

Testing the API:
- Use REST-assured or Quarkus test facilities (see existing tests like `WorkflowActivationAPIContractTest.kt`, `WorkflowDeleteAPIContractTest.kt`)

Packaging & running prod:
```
mvn clean package -pl api -DskipTests=true
java -jar api/target/quarkus-app/quarkus-run.jar
```

---

## Domain & Core Development

Where:
- Domain model in `model/...`
- Use cases, and repositories in `core/...`

Guidelines:
- Keep `model` pure (no Quarkus/DB/web dependencies)
- Put orchestrations, policies, and rules in `core`
- Define repository interfaces in `core`; provide implementations in adapter modules (e.g., `plugins/postgres`) or the API layer where appropriate
- Add comprehensive unit tests in `model`/`core` with clear naming

---

## UI Development

Location:
- `ui/src/main/frontend` (React + TypeScript + Cypress component tests)

Common tasks:
- Component tests example: `cypress/component/RevisionActions.cy.tsx`
- Components example: `src/components/YamlEditor.tsx`

Run frontend dev server (if configured in the module; commands may vary):
- Typically `npm install` then `npm run dev` inside `ui/src/main/frontend`
- For tests: `npm run test` or `npx cypress open` depending on setup

Note: The UI module is described as a placeholder; check package.json for scripts.

---

## Database & Persistence

PostgreSQL 18 is the target DB in the 001-workflow-management spec. Persistence modules and migrations may live under `plugins/postgres` or API adapters.

Recommendations:
- Use Testcontainers for integration tests (`*IntegTest.kt`)
- Prefer JSONB for parsed workflow steps and TEXT for original YAML (as per specs)
- Keep SQL in adapters; keep domain free of DB concerns

Local development tips:
- Run a local Postgres (Docker) if needed
- Configure connection in `application.properties` for dev profile

Example Quarkus properties (adjust as needed):
```
quarkus.http.port=8080
# quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/maestro
# quarkus.datasource.username=maestro
# quarkus.datasource.password=maestro
```

---

## Troubleshooting

Maven builds are slow:
- Use `-DskipIntegTests=true` during inner-loop development
- Use module builds: `mvn -pl api -am clean install`

Quarkus dev mode fails on Kotlin reflection/annotations:
- Ensure the all-open plugin is active via Maven Quarkus Kotlin setup
- Clean build after changing annotations: `mvn clean compile`

Port 8080 already in use:
- Change `quarkus.http.port` in `api/src/main/resources/application.properties`

Tests not running:
- Verify file/class naming matches `*UnitTest.kt` or `*IntegTest.kt`

IDE cannot resolve symbols across modules:
- Re-import Maven projects and enable Kotlin in each module

---

## Appendix: Useful Paths

- API config: `api/src/main/resources/application.properties`
- API tests: `api/src/test/kotlin/io/maestro/api/...`
- Core use cases: `core/src/main/kotlin/io/maestro/core/usecase/...`
- Model domain: `model/src/main/kotlin/io/maestro/model/...` (package may vary)
- UI frontend: `ui/src/main/frontend`
- Specs and contracts: `specs/001-workflow-management`

If something is unclear or missing in this guide, please open an issue or submit a PR updating `documentation/DEVELOPER_GUIDE.md`.
