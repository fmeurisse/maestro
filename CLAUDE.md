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
mvn test                   # Run all tests
mvn test -pl core          # Run tests for specific module
```

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
  - Defines task abstractions and implementations (`Step`, `OrchestrationTask`, `Sequence`, `If`, `WorkTask`, `LogTask`)
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
  - YAML configuration (quarkus-config-yaml)
- Configuration file: `api/src/main/resources/application.yml`
- Default server port: 8080 (configurable in application.yml)
