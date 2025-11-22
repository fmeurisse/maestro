# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Maestro is a workflow orchestration system built with Kotlin and Maven. It uses a multi-module Maven structure with domain-driven design principles.

## Build and Development Commands

### Build
```bash
mvn clean install          # Build all modules
mvn clean install -pl api  # Build specific module (api, core, model, ui)
mvn clean package          # Package without running tests
```

### Run
```bash
mvn quarkus:dev -pl api    # Start the Quarkus API server in dev mode (with hot reload)
mvn quarkus:dev            # Start from root directory
java -jar api/target/quarkus-app/quarkus-run.jar  # Run production build
```

### Testing
```bash
mvn test                            # Run all tests (unit + integration)
mvn test -pl core                   # Run tests for specific module
mvn test -DskipIntegTests=true      # Run only unit tests (skip integration tests)
mvn test -DskipTests=true           # Skip all tests
```

**Test Naming Convention**:
- Unit test files MUST end with `UnitTest.kt` (e.g., `WorkflowRevisionUnitTest.kt`)
- Integration test files MUST end with `IntegTest.kt` (e.g., `PostgresWorkflowRevisionRepositoryIntegTest.kt`)
- Test class names MUST match the file name

**Test Execution**:
- Tests are executed in two separate phases:
  1. **unit-tests** phase: Runs all `*UnitTest.kt` files (fast, no external dependencies)
  2. **integration-tests** phase: Runs all `*IntegTest.kt` files (slower, uses Testcontainers)
- Use `-DskipIntegTests=true` to skip only integration tests during development
- Use `-DskipTests=true` to skip all tests for quick builds

### Code Compilation
```bash
mvn compile                # Compile all modules
mvn kotlin:compile         # Compile Kotlin sources
```

## Architecture

### Module Structure

The project follows a layered architecture with clear separation of concerns:

- **model**: Core domain model with no external dependencies
  - Contains workflow domain entities (`WorkflowRevision`, `WorkflowRevisionID`)
  - Defines task abstractions and implementations (`Step`, `OrchestrationStep`, `Sequence`, `If`, `WorkTask`, `LogTask`)
  - Pure Kotlin module with only kotlin-stdlib dependency

- **core**: Business logic layer
  - Depends on `model` module
  - Contains repository interfaces (`IWorkflowRevisionRepository`)
  - Implements domain services

- **api**: REST API layer
  - Depends on `core` module
  - Quarkus application (Kotlin)
  - REST resources in `io.maestro.api` package
  - Currently has basic "Hello World" endpoint at `/`

- **ui**: Frontend module (placeholder)
  - Currently empty, reserved for future UI implementation

### Dependency Flow
```
api → core → model
ui (independent, not yet implemented)
```

### Task Model

The workflow system uses a hierarchical task model:
- `Step`: Base interface for all executable steps
- `OrchestrationTask`: Interface for tasks that orchestrate other steps (extends Step)
- `Sequence`: Concrete orchestration that executes tasks sequentially
- `If`: Conditional execution task
- `WorkTask`, `LogTask`: Concrete work tasks

### Technology Stack

- Java 21
- Kotlin 2.2.0
- Maven (multi-module)
- Quarkus 3.29.3 (API layer)
- UTF-8 encoding throughout

## Development Notes

- The project uses Kotlin for `model`, `core`, and `api` modules
- Kotlin compilation is configured with the all-open plugin for Quarkus CDI and JAX-RS annotations
- Source directories: `src/main/kotlin` for Kotlin code
- API module uses Quarkus with:
  - JAX-RS REST endpoints (quarkus-rest)
  - CDI dependency injection (quarkus-arc)
  - Kotlin support (quarkus-kotlin)
- Configuration file: `api/src/main/resources/application.properties`
- Default server port: 8080 (configurable in application.properties)

## Active Technologies
- Kotlin 2.2.0+ on Java 21 JVM + Quarkus 3.29.3+ (REST, CDI, Kotlin support), Jackson (YAML/JSON parsing), JDBI or Exposed (database access), RFC 7807 JSON Problem library (001-workflow-management)
- PostgreSQL 18 with JSONB for parsed workflow steps and TEXT for original YAML (001-workflow-management)

## Recent Changes
- 001-workflow-management: Added Kotlin 2.2.0+ on Java 21 JVM + Quarkus 3.29.3+ (REST, CDI, Kotlin support), Jackson (YAML/JSON parsing), JDBI or Exposed (database access), RFC 7807 JSON Problem library
