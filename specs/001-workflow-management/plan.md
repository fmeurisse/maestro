# Implementation Plan: Workflow Management

**Branch**: `001-workflow-management` | **Date**: 2025-11-21 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-workflow-management/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

The Workflow Management feature provides complete lifecycle management for workflows and their revisions in the Maestro orchestration system. It enables users to create workflows with YAML definitions, manage multiple sequential revisions, control activation state for deployment strategies (including canary deployments and A/B testing), update inactive revisions, and delete workflows. The system enforces immutable versioning, preserves original YAML with comments, and stores both YAML and parsed JSON representations for efficient querying.

**Technical Approach**: Multi-module Kotlin/Maven architecture with domain-driven design. Two-entity model (WorkflowRevision/WorkflowRevisionWithSource) for memory efficiency, YAML parsing via Jackson with runtime step type registration (plugin-friendly), RESTful API with JSON Problem error responses, PostgreSQL dual storage (TEXT for YAML + JSONB for revision data + computed columns), and React UI with Monaco YAML editor and Cypress testing.

## Technical Context

**Language/Version**: Kotlin 2.2.0+ on Java 21 JVM
**Primary Dependencies**: Quarkus 3.29.3+ (REST, CDI, Kotlin support), Jackson (YAML/JSON parsing with runtime polymorphism), JDBI 3.x (database access), Zalando Problem (RFC 7807)
**Storage**: PostgreSQL 18 with dual storage (yaml_source TEXT + revision_data JSONB) + GENERATED ALWAYS computed columns for indexing
**Testing**: JUnit 5 + Kotlin Test (unit/integration), REST Assured (API tests), Cypress (UI component tests)
**Target Platform**: JVM server (Linux/MacOS/Windows), React web UI
**Project Type**: Web application with backend API and frontend UI
**Performance Goals**: <500ms p95 for workflow creation, <200ms p95 for queries, <300ms p95 for activation, 100+ concurrent operations
**Constraints**: Dual storage (TEXT + JSONB), two entity types (with/without YAML source), preserve YAML formatting, support plugin step types via ServiceLoader, support 10K+ workflows and 1K+ revisions per workflow
**Scale/Scope**: 10,000 workflows across namespaces, 1,000 revisions per workflow, 100 concurrent operations, <1MB YAML definitions

**Architectural Pattern**: Two-entity design pattern
- **WorkflowRevision**: Core entity without YAML source (lightweight, for queries/activation/execution)
- **WorkflowRevisionWithSource**: Extended entity with YAML source (for create/update/view operations)
- **Repository Methods**: Dual API (findById vs findByIdWithSource) to control YAML source loading

**User Input Constraints**:
- REST API MUST use RFC 7807 JSON Problem format for all error responses
- UI MUST be developed in React in `ui/src/main/frontend` directory
- UI MUST use Cypress for component testing
- UI MUST include a YAML text editor with syntax highlighting/coloration

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Domain-Driven Architecture (NON-NEGOTIABLE)
✅ **PASS**: Feature follows strict layering:
- `model` module: WorkflowRevision entity, Step interfaces (pure domain, kotlin-stdlib only)
- `core` module: Use cases (CreateWorkflowUseCase, ActivateRevisionUseCase), repository interfaces
- `api` module: REST resources, JSON Problem error handling, YAML parsing/validation
- `ui` module: React frontend (independent)

Unidirectional dependencies maintained: api → core → model, ui (independent).

### II. YAML-First Workflow Definition
✅ **PASS**: Feature requirement FR-001, FR-002, FR-003 mandate:
- YAML as primary user-facing format
- Preserve original YAML with comments/formatting
- Dual storage: YAML TEXT + JSON JSONB in PostgreSQL

This is the core feature requirement and directly implements the constitutional principle.

###III. Immutable Versioning
✅ **PASS**: Feature requirements FR-006, FR-008, FR-013 enforce:
- Sequential version numbers (1, 2, 3...) with no gaps
- Version, namespace, id, createdAt fields are immutable (FR-013)
- Inactive revisions can be updated (FR-010), active cannot (FR-011)
- Repository layer enforces version assignment and immutability

Aligns with constitutional principle with modification: inactive revisions are updatable per user requirements.

### IV. Technology Consistency
✅ **PASS**: Implementation uses constitutional stack:
- Kotlin 2.2.0+ for all backend modules
- Maven multi-module build
- Quarkus 3.29.3+ (quarkus-rest, quarkus-arc, quarkus-kotlin, quarkus-config-yaml)
- Jackson for YAML/JSON (jackson-dataformat-yaml, jackson-module-kotlin)
- UTF-8 encoding
- Java 21 JVM runtime

Additional dependencies justified:
- PostgreSQL driver (persistence requirement)
- JDBI or Exposed (database access - need to research which)
- JSON Problem library (user requirement for error format)
- React + Cypress (user requirement for UI)

### V. Test-Driven Development
✅ **PASS**: Plan includes comprehensive testing:
- Unit tests for use cases, validation, parsing (core module)
- Integration tests for repository implementations
- Contract tests for API endpoints (REST Assured)
- UI component tests (Cypress per user requirement)

Test categories align with constitutional TDD principle.

**GATE STATUS**: ✅ **ALL CHECKS PASS** - Proceed to Phase 0 research

## Project Structure

### Documentation (this feature)

```text
specs/001-workflow-management/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── openapi.yaml     # OpenAPI 3.1 spec with JSON Problem schemas
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
model/
└── src/main/kotlin/io/maestro/model/
    ├── workflow/
    │   ├── WorkflowRevision.kt         # Domain entity (immutable data class)
    │   └── WorkflowRevisionID.kt       # Value object (namespace + id + version)
    └── steps/
        ├── Step.kt                      # Base interface (existing)
        ├── OrchestrationStep.kt         # Orchestration interface (existing)
        ├── Task.kt                      # Task interface (existing)
        └── [existing step implementations]

core/
└── src/main/kotlin/io/maestro/core/
    ├── workflow/
    │   ├── repository/
    │   │   └── IWorkflowRevisionRepository.kt  # Repository interface
    │   ├── usecases/
    │   │   ├── CreateWorkflowUseCase.kt        # Create first revision
    │   │   ├── CreateRevisionUseCase.kt        # Create new revision
    │   │   ├── UpdateRevisionUseCase.kt        # Update inactive revision
    │   │   ├── ActivateRevisionUseCase.kt      # Activate revision
    │   │   ├── DeactivateRevisionUseCase.kt    # Deactivate revision
    │   │   ├── DeleteRevisionUseCase.kt        # Delete single revision
    │   │   └── DeleteWorkflowUseCase.kt        # Delete all revisions
    │   └── validation/
    │       ├── YamlWorkflowParser.kt           # Parse YAML to Step model
    │       ├── WorkflowValidator.kt            # Validate business rules
    │       └── StepValidator.kt                # Validate step tree

api/
├── pom.xml                                     # Add JSON Problem dependency
└── src/main/kotlin/io/maestro/api/
    ├── workflow/
    │   ├── WorkflowResource.kt                 # REST endpoints
    │   ├── dto/
    │   │   ├── WorkflowRequest.kt              # Request DTOs
    │   │   └── WorkflowResponse.kt             # Response DTOs
    │   └── errors/
    │       ├── JsonProblemExceptionMapper.kt   # JAX-RS exception mapper
    │       └── WorkflowProblemTypes.kt         # RFC 7807 problem types
    └── config/
        └── JacksonConfig.kt                    # YAML/JSON ObjectMapper config

plugins/
└── postgres-repository/                        # New module for PostgreSQL
    ├── pom.xml                                 # PostgreSQL driver + JDBI/Exposed
    └── src/main/kotlin/io/maestro/plugins/postgres/
        ├── PostgresWorkflowRevisionRepository.kt  # IWorkflowRevisionRepository impl
        ├── schema/
        │   └── workflow_revisions.sql          # DDL with JSONB + TEXT columns
        └── config/
            └── DatabaseConfig.kt               # JDBI/Exposed configuration

ui/
├── pom.xml                                     # Add frontend-maven-plugin
└── src/main/frontend/                          # React application
    ├── package.json                            # React, Monaco Editor, Cypress deps
    ├── cypress/
    │   └── component/
    │       └── YamlEditor.cy.tsx               # Cypress component tests
    ├── src/
    │   ├── components/
    │   │   ├── YamlEditor.tsx                  # Monaco Editor with YAML syntax
    │   │   ├── WorkflowList.tsx                # List workflows
    │   │   ├── WorkflowRevisions.tsx           # Show revisions
    │   │   └── RevisionActions.tsx             # Activate/deactivate/delete
    │   ├── services/
    │   │   └── workflowApi.ts                  # API client (fetch)
    │   └── App.tsx                             # Main application
    └── cypress.config.ts                       # Cypress configuration

tests/
└── integration/
    └── kotlin/io/maestro/integration/
        └── WorkflowManagementIT.kt            # End-to-end API tests
```

**Structure Decision**: Web application with multi-module Maven backend (model → core → api → plugins) and React frontend in ui module. This structure:
- Follows Maestro's existing multi-module architecture per CLAUDE.md
- Separates domain (model), business logic (core), infrastructure (api, plugins), and UI (ui)
- Enables independent testing at each layer
- PostgreSQL plugin as separate module for clean dependency management (api doesn't depend on DB directly)

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitutional violations detected. All gates pass.

---

## Post-Design Constitution Re-Check

*Re-evaluation after Phase 1 design (research, data-model, contracts, quickstart)*

### I. Domain-Driven Architecture (NON-NEGOTIABLE)
✅ **PASS - CONFIRMED**: Design maintains strict layering:
- **model**: `WorkflowRevision`, `WorkflowRevisionID`, `WorkflowID` entities with zero external deps
- **core**: `IWorkflowRevisionRepository`, 7 use cases, YAML parser, validators (depends only on model)
- **api**: REST resources, DTOs, JSON Problem mappers, Jackson config (depends only on core)
- **plugins/postgres-repository**: JDBI implementation (new module, implements core interfaces)
- **ui**: Independent React app with Monaco Editor

No cross-module violations. Plugin module correctly implements repository interface from core.

### II. YAML-First Workflow Definition
✅ **PASS - CONFIRMED (UPDATED)**: Design fully implements YAML preservation:
- WorkflowRevision entity has `yamlSource: String` field (preserves formatting/comments)
- WorkflowRevision entity has `steps: Step` field (parsed tree for execution)
- PostgreSQL schema stores complete WorkflowRevision as JSONB including both yamlSource and steps
- Jackson YAML parser converts YAML ↔ Step tree at API boundary
- API accepts/returns YAML (`Content-Type: application/x-yaml`)

Constitutional principle fully satisfied. JSONB-only storage with computed columns does not affect YAML preservation - yamlSource field is stored within the JSONB.

### III. Immutable Versioning
✅ **PASS - CONFIRMED**: Design enforces immutability:
- `WorkflowRevision` data class fields: `namespace`, `id`, `version`, `createdAt` never modified
- Repository layer: UPDATE statements check `active = false` before allowing changes
- Use cases: `UpdateRevisionUseCase` validates revision is inactive
- PostgreSQL constraints: Composite PK prevents duplicate versions
- Domain exceptions: `ActiveRevisionConflictException` for violations

Inactive revision updates allowed per user requirements while maintaining version immutability.

### IV. Technology Consistency
✅ **PASS - CONFIRMED (UPDATED)**: Final design uses constitutional stack with architectural refinements:
- **Language**: Kotlin 2.2.0+ for model/core/api/plugins
- **Build**: Maven multi-module with frontend-maven-plugin for React
- **Runtime**: Java 21 JVM
- **API Framework**: Quarkus 3.29.3+ (REST, CDI, Kotlin, YAML config)
- **Serialization**: Jackson (jackson-dataformat-yaml, jackson-module-kotlin) with runtime polymorphism via ObjectMapper.registerSubtypes()
- **Database**: PostgreSQL 18 with JSONB-only storage + GENERATED ALWAYS computed columns
- **Database Access**: JDBI 3.x (research decision #1, lightweight, SQL control for JSONB operations)

Additional dependencies justified in research:
- Zalando Problem (RFC 7807 - user requirement)
- Monaco Editor (user requirement for YAML editor)
- Cypress (user requirement for UI testing)
- frontend-maven-plugin (Maven integration for React)

**Architectural Updates**: JSONB-only schema and runtime Step registration improve plugin extensibility while maintaining constitutional standards.

### V. Test-Driven Development
✅ **PASS - CONFIRMED**: Design includes comprehensive test strategy:
- **Unit**: Use case tests, validator tests, parser tests (core module, JUnit 5)
- **Integration**: Repository contract tests (PostgreSQL plugin)
- **API**: REST endpoint tests (REST Assured in tests/integration)
- **UI**: Component tests (Cypress for YamlEditor, WorkflowList, etc.)

quickstart.md documents TDD workflows and test commands. All constitutional test categories covered.

**FINAL GATE STATUS**: ✅ **ALL CONSTITUTIONAL PRINCIPLES SATISFIED**

Design is architecturally sound and implementation-ready. Proceed to `/speckit.tasks` for task generation.

---

## Next Steps

**Planning Phase Complete**: All Phase 0 and Phase 1 deliverables generated (FINAL UPDATE):
- ✅ research.md: UPDATED #7 (Dual storage: yaml_source TEXT + revision_data JSONB + computed columns)
- ✅ data-model.md: UPDATED - Two entity design (WorkflowRevision + WorkflowRevisionWithSource), dual storage, factory methods with ValidationException
- ✅ contracts/openapi.yaml: Complete OpenAPI 3.1 spec with JSON Problem schemas
- ✅ quickstart.md: NEEDS UPDATE - Tutorial with dual storage schema
- ✅ Agent context updated: CLAUDE.md reflects new technologies

**Final Architectural Design** (per user requirements):
1. **Two Data Classes**:
   - **WorkflowRevision**: Lightweight entity WITHOUT yamlSource field (for queries, activation, execution)
   - **WorkflowRevisionWithSource**: Extended entity WITH yamlSource field (for create, update, view operations)
   - Composition pattern: WorkflowRevisionWithSource wraps WorkflowRevision + yamlSource
2. **PostgreSQL Schema**: Dual storage with yaml_source TEXT + revision_data JSONB + GENERATED ALWAYS computed columns
   - TEXT column preserves YAML formatting/comments
   - JSONB stores complete WorkflowRevision (without yamlSource to avoid duplication)
   - Computed columns enable efficient indexing (namespace, id, version, active, timestamps)
3. **Repository Methods**: Dual API for performance optimization
   - Methods returning WorkflowRevision (findById, findByWorkflowId, findActiveRevisions)
   - Methods returning WorkflowRevisionWithSource (saveWithSource, findByIdWithSource, updateWithSource)
4. **Step Polymorphism**: Runtime registration via ObjectMapper.registerSubtypes() + ServiceLoader for plugin extensibility
5. **Domain Validation**: Factory methods (WorkflowRevision.create(), WorkflowRevisionWithSource.create()) throw ValidationException

**Ready for Task Generation**:
```bash
/speckit.tasks
```

This will generate `tasks.md` with:
- Dependency-ordered implementation tasks
- Parallel execution opportunities
- User story mapping
- Test-first approach per constitution

**Implementation Order Recommendation**:
1. **Phase 1 (Foundation)**: model entities, core interfaces, domain exceptions
2. **Phase 2 (User Story 1 - P1)**: Create/version workflows (YAML parsing, repository, API)
3. **Phase 3 (User Story 2 - P2)**: Activate/deactivate revisions
4. **Phase 4 (User Story 3 - P3)**: Update inactive revisions
5. **Phase 5 (User Story 4 - P3)**: Delete revisions/workflows
6. **Phase 6 (UI)**: React components with Monaco Editor

Each user story is independently testable per specification.

