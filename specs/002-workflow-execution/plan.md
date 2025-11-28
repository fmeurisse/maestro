# Implementation Plan: Workflow Execution with Input Parameters

**Branch**: `002-workflow-execution` | **Date**: 2025-11-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-workflow-execution/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

The Workflow Execution feature enables triggering workflow runs via API with typed input parameters, synchronous execution in the orchestrator, and persistent execution state tracking in the database. Users provide a workflow revision ID and input parameters matching the revision's parameter schema (types: string, integer, float, boolean). The system validates parameters, executes all workflow steps sequentially, persists execution state and step results in real-time, and provides APIs to query execution status and history. This feature makes workflows actionable and provides complete observability for debugging and auditing.

**Technical Approach**: NEEDS CLARIFICATION - research required for: workflow step execution engine design, parameter validation strategy, execution state persistence pattern, execution context propagation, error handling and step failure recovery, execution logging granularity and storage.

## Technical Context

**Language/Version**: Kotlin 2.2.0+ on Java 21 JVM (consistent with 001-workflow-management)
**Primary Dependencies**: Quarkus 3.29.3+ (REST, CDI, Kotlin support), Jackson (JSON parsing), JDBI 3.x (database access), Zalando Problem (RFC 7807)
**Storage**: PostgreSQL 18 with execution state tables (workflow_executions, execution_step_results) using JSONB for step outputs and error details
**Testing**: JUnit 5 + Kotlin Test (unit/integration tests with *UnitTest.kt and *IntegTest.kt naming), REST Assured (API contract tests), Testcontainers (PostgreSQL for integration tests)
**Target Platform**: JVM server (Linux/MacOS/Windows)
**Project Type**: Backend API extension (part of existing multi-module web application)
**Performance Goals**: <2s for execution initiation (SC-001), <1s for status queries (SC-003), <500ms for parameter validation (SC-004), support 50+ sequential steps (SC-005)
**Constraints**: Synchronous execution only (no async workers), execution logs must survive system restarts (SC-002), 100% state persistence before API response, 90+ day log retention (SC-006)
**Scale/Scope**: Support concurrent executions of same workflow revision, handle workflows with up to 50 sequential steps, maintain 95% success rate (SC-007)

**Architecture Requirements**:
- **Execution Engine**: NEEDS CLARIFICATION - design pattern for step execution (Visitor? Interpreter? Command?)
- **Parameter Validation**: NEEDS CLARIFICATION - schema validation approach (JSON Schema? Custom validator? Bean Validation?)
- **Execution Context**: NEEDS CLARIFICATION - how to pass input parameters and step outputs through execution chain
- **State Persistence**: NEEDS CLARIFICATION - transaction boundaries (per-step? whole execution? optimistic updates?)
- **Error Handling**: NEEDS CLARIFICATION - step failure recovery strategy (fail-fast? rollback? compensating transactions?)
- **Execution ID Generation**: NEEDS CLARIFICATION - UUID strategy (UUID v4? ULID? Database sequence?)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Domain-Driven Design with Clean Architecture (NON-NEGOTIABLE)
✅ **PASS**: Feature follows strict layering and UseCase pattern:
- `model` module: WorkflowExecution entity, ExecutionStepResult entity, ExecutionStatus enum (pure domain)
- `core` module: ExecuteWorkflowUseCase, QueryExecutionUseCase, repository interfaces (IWorkflowExecutionRepository)
- `api` module: REST resources for execution endpoints
- `plugins/postgres` module: PostgresWorkflowExecutionRepository implementation with Liquibase migrations

UseCase pattern enforced: API → ExecuteWorkflowUseCase → Domain Logic → IWorkflowExecutionRepository → Plugin Implementation

### II. Test-First Development (NON-NEGOTIABLE)
✅ **PASS**: Plan includes comprehensive TDD:
- Unit tests for use cases with mocked repositories (*UnitTest.kt naming)
- Unit tests for parameter validation logic
- Unit tests for execution engine step traversal
- Integration tests for repository with Testcontainers (*IntegTest.kt naming)
- Contract tests for API endpoints with REST Assured

All tests written BEFORE implementation per TDD discipline.

### III. Database Schema Evolution (NON-NEGOTIABLE)
✅ **PASS**: PostgreSQL schema changes managed via Liquibase:
- New tables: workflow_executions, execution_step_results
- Liquibase changesets in plugins/postgres module
- Immutable changesets once applied
- Changeset ID format: `20251125-HHMM-add-workflow-execution-tables`

### IV. Dual Storage Pattern for Flexibility
⚠️ **NOT APPLICABLE**: Execution data is system-generated, not user-edited. No YAML/original format preservation needed. Execution state stored as structured JSONB only.

### V. Plugin Architecture
✅ **PASS**: Execution repository follows plugin pattern:
- IWorkflowExecutionRepository interface in core module
- PostgresWorkflowExecutionRepository in plugins/postgres module
- InMemoryWorkflowExecutionRepository for unit tests (optional)
- CDI injection for runtime implementation selection

### VI. Kotlin + Quarkus + Maven Standards
✅ **PASS**: Consistent with existing technology stack:
- Kotlin 2.2+ for all new code
- Quarkus 3.29+ REST and CDI
- Maven multi-module structure (reuse existing modules)
- Java 21 JVM
- UTF-8 encoding

### VII. Performance and Data Transfer (DTO Usage)
✅ **PASS**: DTO usage justified:
- **WorkflowExecution entity** used directly between core and plugins (no DTO needed)
- **ExecutionRequestDTO** at API boundary: maps API request to use case input
- **ExecutionResponseDTO** at API boundary: hides internal IDs, adds hypermedia links
- No unnecessary DTOs between use cases and repositories

**GATE STATUS**: ✅ **ALL CHECKS PASS** - Proceed to Phase 0 research

---

## Post-Design Constitution Re-Check

*Re-evaluated after Phase 1 (Design & Contracts) completion*

### I. Domain-Driven Design with Clean Architecture ✅ **STILL VALID**
Design artifacts confirm strict layering:
- `data-model.md`: Domain entities in model module (WorkflowExecution, ExecutionStepResult, ExecutionContext)
- `research.md`: UseCase pattern with ExecuteWorkflowUseCase, GetExecutionStatusUseCase, GetExecutionHistoryUseCase
- `research.md`: Repository interface IWorkflowExecutionRepository in core, implementation in plugins/postgres
- `openapi.yaml`: REST endpoints in API layer with DTOs at boundary only

UseCase dependencies verified: ExecuteWorkflowUseCase → IWorkflowExecutionRepository (interface in core, impl in plugin)

### II. Test-First Development ✅ **STILL VALID**
`quickstart.md` documents TDD strategy:
- Unit tests: ExecuteWorkflowUseCaseUnitTest, ParameterValidatorUnitTest, WorkflowExecutorUnitTest
- Integration tests: PostgresWorkflowExecutionRepositoryIntegTest (Testcontainers), ExecutionResourceIntegTest (REST Assured)
- File naming convention enforced: *UnitTest.kt and *IntegTest.kt
- TDD workflow documented: Write test first, verify failure, implement, refactor

### III. Database Schema Evolution ✅ **STILL VALID**
`data-model.md` specifies Liquibase approach:
- Changeset ID: `20251125-1400-add-workflow-execution-tables`
- Tables: workflow_executions, execution_step_results with proper constraints
- Indexes for query performance
- Foreign key to workflow_revisions table from 001-workflow-management
- Location: `plugins/postgres/src/main/resources/db/changelog/`

### IV. Dual Storage Pattern ⚠️ **STILL NOT APPLICABLE**
No change. Execution data is system-generated, not user-edited. Single storage (JSONB) remains appropriate.

### V. Plugin Architecture ✅ **STILL VALID**
`data-model.md` and `research.md` confirm plugin pattern:
- IWorkflowExecutionRepository interface defined in core module
- PostgresWorkflowExecutionRepository implements interface in plugins/postgres module
- CDI injection for runtime binding
- Optional InMemoryWorkflowExecutionRepository for unit tests mentioned in quickstart.md

### VI. Kotlin + Quarkus + Maven Standards ✅ **STILL VALID**
All artifacts use consistent stack:
- Kotlin 2.2+ confirmed in plan.md Technical Context
- Quarkus 3.29+ for REST/CDI confirmed
- Maven multi-module reuse (no new modules)
- Java 21 JVM, UTF-8 encoding
- CLAUDE.md updated with active technologies via update-agent-context.sh

### VII. Performance and Data Transfer (DTO Usage) ✅ **STILL VALID**
`openapi.yaml` confirms DTO usage at API boundary only:
- ExecutionRequestDTO, ExecutionResponseDTO, ExecutionDetailResponseDTO (API layer)
- Domain entities (WorkflowExecution, ExecutionStepResult) used directly between core and plugins
- No unnecessary DTOs between use cases and repositories
- Performance: Per-step commits ~1s overhead for 50 steps (within SC-001 budget)

**FINAL GATE STATUS**: ✅ **ALL CONSTITUTIONAL PRINCIPLES SATISFIED POST-DESIGN**

No violations introduced during design phase. Architecture remains compliant with all seven constitutional principles.

## Project Structure

### Documentation (this feature)

```text
specs/002-workflow-execution/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── openapi.yaml     # OpenAPI 3.1 spec for execution endpoints
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
model/
└── src/main/kotlin/io/maestro/model/
    ├── execution/
    │   ├── WorkflowExecution.kt          # Domain entity (immutable data class)
    │   ├── WorkflowExecutionID.kt        # Value object (UUID wrapper)
    │   ├── ExecutionStatus.kt            # Enum: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    │   ├── ExecutionStepResult.kt        # Step execution outcome
    │   ├── StepStatus.kt                 # Enum: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    │   └── ExecutionContext.kt           # Context data (input params + step outputs)
    └── steps/
        ├── Step.kt                        # Add execute(context) method to interface
        ├── Sequence.kt                    # Implement execute() - iterate children
        ├── If.kt                          # Implement execute() - evaluate condition
        ├── LogTask.kt                     # Implement execute() - perform logging
        └── WorkTask.kt                    # Implement execute() - delegate to task logic

core/
└── src/main/kotlin/io/maestro/core/
    └── execution/
        ├── repository/
        │   └── IWorkflowExecutionRepository.kt  # Repository interface
        ├── usecases/
        │   ├── ExecuteWorkflowUseCase.kt        # Calls step.execute() on workflow steps
        │   ├── GetExecutionStatusUseCase.kt     # Query execution by ID
        │   └── GetExecutionHistoryUseCase.kt    # Query executions for workflow
        └── validation/
            ├── ParameterValidator.kt            # Validate input parameters
            └── ParameterTypeValidator.kt        # Type checking logic

api/
└── src/main/kotlin/io/maestro/api/
    └── execution/
        ├── ExecutionResource.kt                 # REST endpoints
        ├── dto/
        │   ├── ExecutionRequestDTO.kt           # POST request body
        │   ├── ExecutionResponseDTO.kt          # Execution details
        │   └── ExecutionHistoryResponseDTO.kt   # List of executions
        └── errors/
            └── ExecutionProblemTypes.kt         # RFC 7807 problem types

plugins/postgres/
└── src/main/kotlin/io/maestro/plugins/postgres/
    ├── execution/
    │   └── PostgresWorkflowExecutionRepository.kt  # JDBI repository impl
    └── resources/db/changelog/
        └── 20251125-1400-add-workflow-execution-tables.xml  # Liquibase changeset
```

**Structure Decision**: Multi-module Maven architecture (existing). Feature extends existing modules (model, core, api, plugins/postgres) with new execution package. No new modules needed. Follows established pattern from 001-workflow-management.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitutional violations. All principles followed.
