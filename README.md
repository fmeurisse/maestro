<div align="center">

# Maestro

Workflow orchestration system built with Kotlin and Maven. API powered by Quarkus.

</div>

---

## Overview

Maestro manages workflows composed of hierarchical tasks. It uses a multi‑module Maven layout and domain‑driven design principles.

Module dependency flow:

```
api → core → model
ui (independent, WIP)
plugins (adapters, e.g., postgres)
```

Key technologies: Java 21, Kotlin 2.2.x, Maven, Quarkus 3.29.x.

For a deeper architecture and contributor guidance, see the documentation links below.

---

## Quick Start

Prerequisites: Java 21, Maven 3.9+, Git. Optional: Docker (for DB/testcontainers).

Build all modules:

```
mvn clean install
```

Run the API in development mode (hot reload):

```
mvn quarkus:dev -pl api
```

The API serves at http://localhost:8080. Configuration is in `api/src/main/resources/application.properties`.

---

## Try the API

Create a workflow (YAML):

```
curl -i \
  -H 'Content-Type: application/yaml' \
  --data-binary @workflow.yaml \
  http://localhost:8080/api/workflows
```

Activate a revision (requires `X-Current-Updated-At` header):

```
UPDATED_AT=$(curl -s -H 'Accept: application/yaml' \
  http://localhost:8080/api/workflows/my-ns/my-wf/1 | awk '/updatedAt:/ {print $2}')

curl -i -X POST \
  -H "X-Current-Updated-At: $UPDATED_AT" \
  http://localhost:8080/api/workflows/my-ns/my-wf/1/activate
```

More endpoints and examples are documented in the API User Guide below.

---

## Project Structure

- model: Pure domain model (entities and task abstractions)
- core: Business logic, use cases, repository interfaces
- api: Quarkus REST API exposing workflow operations
- ui: Frontend module (placeholder with Cypress component tests)
- plugins/postgres: PostgreSQL adapter (work in progress)

---

## Build, Run, Test

Common commands:

```
mvn clean install                # Build all modules
mvn clean install -pl api        # Build a specific module (api, core, model, ui, plugins/*)
mvn quarkus:dev -pl api          # Start API in dev mode (hot reload)
mvn test                         # Run all tests (unit + integration)
mvn test -DskipIntegTests=true   # Run only unit tests
mvn clean package                # Package without running tests
```

Test naming convention:
- Unit tests end with `UnitTest.kt`
- Integration tests end with `IntegTest.kt`

---

## Documentation

- Developer Guide: `documentation/DEVELOPER_GUIDE.md`
- API User Guide: `documentation/API_USER_GUIDE.md`
- Engineering notes for AI agents: `CLAUDE.md` and `AGENTS.md`
- Specs and contracts: `specs/001-workflow-management`

Please start with the Developer Guide for setup and contributor workflow, then the API User Guide for endpoint usage.

---

## Contributing

We welcome issues and pull requests.
- Follow the test naming conventions above.
- Keep the `model` module free of framework/database dependencies.
- Prefer small, focused PRs with passing `mvn clean install`.

See `documentation/DEVELOPER_GUIDE.md` for coding standards, branching, and review checklist.

---

## License

Licensed under the Apache License, Version 2.0.

- See the `LICENSE` file at the repository root for the full text.
- You may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.



Last updated: 2025-11-25