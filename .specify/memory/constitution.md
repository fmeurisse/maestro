<!--
Sync Impact Report:
Version change: 1.0.0 → 1.1.0 → 1.2.0
Modified principles:
  - Principle I: Domain-Driven Design → Expanded with Clean Architecture and UseCase pattern (v1.1.0)
  - Testing Strategy: Added test naming convention (UnitTest/IntegTest postfixes) (v1.2.0)
Added sections:
  - Principle VII: Performance and Data Transfer (DTO usage guidance) (v1.1.0)
  - Test Naming Convention section in Development Workflow (v1.2.0)
Templates requiring updates:
  ✅ plan-template.md - Constitution Check section aligns
  ✅ spec-template.md - No changes needed
  ✅ tasks-template.md - No changes needed
  ✅ CLAUDE.md - Updated with test naming convention
Follow-up TODOs: None
-->

# Maestro Constitution

## Core Principles

### I. Domain-Driven Design with Clean Architecture

Every module follows clear domain boundaries with explicit contracts:
- **model** module: Pure domain entities with no external dependencies beyond kotlin-stdlib
- **core** module: Business logic and use cases, depends only on model
- **api** module: REST endpoints and external interfaces, depends on core
- **plugins** module: Infrastructure implementations (databases, message queues), implements core interfaces

**Clean Architecture in Core Module**:
- Use the **UseCase Pattern** for all business logic operations
- Each use case is a single-responsibility class with explicit inputs/outputs
- Define **interfaces for all outbound dependencies** (repositories, external services, message queues)
- Use cases depend on interfaces, not concrete implementations
- Plugins provide concrete implementations of core interfaces
- Flow: API → UseCase → Domain Logic → Repository Interface → Plugin Implementation

**Structure Example**:
```
core/
├── usecases/
│   ├── CreateWorkflowUseCase.kt    # Business logic
│   └── ActivateRevisionUseCase.kt
├── repository/
│   └── IWorkflowRepository.kt      # Interface (outbound)
└── domain/
    └── exceptions/

plugins/postgres/
└── PostgresWorkflowRepository.kt   # Implementation (inbound to core)
```

**Rationale**: Clean Architecture ensures business logic independence from infrastructure, enables comprehensive testing with mocks, and allows infrastructure swapping without touching domain code. UseCase pattern provides clear entry points and testable business flows.

### II. Test-First Development (NON-NEGOTIABLE)

All code follows strict TDD discipline:
- Write tests FIRST, ensure they FAIL before implementation
- Unit tests for domain logic and use cases with mocked dependencies
- Integration tests for repository contracts using Testcontainers
- Contract tests for API endpoints with real database
- Red-Green-Refactor cycle strictly enforced

**Rationale**: Tests define the contract before implementation, catch regressions early, and serve as living documentation.

### III. Database Schema Evolution

All PostgreSQL schema modifications MUST be managed through Liquibase:
- Liquibase embedded within the postgres plugin module
- Each schema change requires a versioned changeset
- Changesets are immutable once applied to any environment
- Rollback strategies required for breaking changes
- Schema DDL files in resources for reference, but Liquibase is the source of truth

**Rationale**: Ensures schema versioning, enables controlled deployments, provides audit trail, and supports rollback scenarios.

### IV. Dual Storage Pattern for Flexibility

When storing structured data that users can edit:
- Store original format (YAML/JSON) in TEXT column to preserve formatting and comments
- Store parsed structure in JSONB column for efficient querying
- Use PostgreSQL generated columns to extract indexed fields from JSONB
- Repository provides dual API: methods with/without original source

**Rationale**: Balances user experience (preserve their formatting) with system performance (efficient queries and indexing).

### V. Plugin Architecture

Infrastructure concerns are isolated in plugin modules:
- Each plugin (postgres, kafka, etc.) is a separate Maven module
- Plugins implement interfaces defined in core module
- Plugins are discovered via CDI/dependency injection
- Multiple implementations can coexist (e.g., InMemory for tests, Postgres for production)

**Rationale**: Enables testing without infrastructure, supports multiple backends, and allows infrastructure changes without touching business logic.

### VI. Kotlin + Quarkus + Maven Standards

Technology stack follows enterprise Java conventions:
- Kotlin 2.2+ for all code (except where Java interop required)
- Quarkus 3.29+ for REST, CDI, and reactive capabilities
- Maven multi-module structure with clear parent/child relationships
- Java 21 JVM for latest language features and performance
- All modules use UTF-8 encoding consistently

**Rationale**: Leverages mature ecosystem, enables hot reload during development, provides CDI for clean dependency injection, and ensures tooling compatibility.

### VII. Performance and Data Transfer

**DTO Usage Policy**:
- DTOs (Data Transfer Objects) should ONLY be created when necessary for performance optimization
- Prefer using domain entities directly across module boundaries when possible
- Avoid unnecessary object copies and transformations

**When DTOs are justified**:
- **API boundary**: When REST responses need different structure than domain entities (e.g., hiding sensitive fields, aggregating data)
- **Performance critical paths**: When domain entity contains heavy lazy-loaded relationships that would cause N+1 queries
- **Protocol compatibility**: When external system requires specific data format incompatible with domain model
- **Serialization constraints**: When domain entity cannot be cleanly serialized (circular references, non-serializable fields)

**When DTOs are NOT needed**:
- Passing domain entities between use cases in core module (use entities directly)
- Passing entities from repository to use case (use entities directly)
- Simple CRUD operations where API structure matches domain structure
- Internal module communication

**Anti-pattern to avoid**:
```kotlin
// ❌ Unnecessary DTO mapping
fun getWorkflow(id: WorkflowID): WorkflowDTO {
    val workflow = repository.find(id)
    return workflow.toDTO()  // Wasteful copy when entity would work
}

// ✅ Prefer direct entity usage
fun getWorkflow(id: WorkflowID): Workflow {
    return repository.find(id)  // Use entity directly
}
```

**Rationale**: Reducing unnecessary data copies improves performance, reduces memory pressure, and simplifies code. DTOs add complexity and should be justified by concrete performance or architectural needs, not created by default.

## Technology Constraints

### Build and Dependencies

- **Build tool**: Maven (not Gradle) for consistency with Quarkus ecosystem
- **Dependency management**: Parent POM defines versions, children inherit
- **Plugin versions**: Locked in parent POM to ensure reproducible builds
- **Kotlin compilation**: kotlin-maven-plugin with all-open for CDI/JAX-RS
- **Test framework**: JUnit 5 + Kotest for Kotlin-idiomatic assertions

### Database and Persistence

- **Primary database**: PostgreSQL 18+ for JSONB and generated column support
- **Migration tool**: Liquibase (embedded in postgres plugin)
- **Data access**: JDBI 3.x for type-safe SQL (not JPA/Hibernate)
- **Test database**: Testcontainers with PostgreSQL 18-alpine image
- **Connection pooling**: HikariCP (included with Quarkus)

### API and Serialization

- **REST framework**: Quarkus REST (quarkus-rest, formerly RESTEasy Reactive)
- **JSON serialization**: Jackson with Kotlin module
- **YAML parsing**: Jackson YAML dataformat
- **Error handling**: RFC 7807 Problem JSON format via Zalando Problem library
- **API documentation**: OpenAPI/Swagger UI (quarkus-smallrye-openapi)

### Development Tools

- **Hot reload**: Quarkus Dev Mode (mvn quarkus:dev)
- **Code style**: Kotlin official style guide
- **IDE support**: IntelliJ IDEA preferred (but not required)
- **Logging**: JBoss Logging (built into Quarkus)

## Development Workflow

### Module Development Order

1. **model** module first: Define entities and value objects
2. **core** module second: Define interfaces and use cases
3. **plugins** module third: Implement infrastructure (repository, messaging)
4. **api** module last: Expose REST endpoints

**Exception**: When prototyping, InMemory plugin can be developed before full Postgres plugin.

### Testing Strategy

**Test Naming Convention**:
- **Unit test files** MUST end with `UnitTest.kt` (e.g., `WorkflowRevisionUnitTest.kt`)
- **Integration test files** MUST end with `IntegTest.kt` (e.g., `PostgresWorkflowRevisionRepositoryIntegTest.kt`)
- Test class names MUST match the file name

**Unit Tests** (in each module's src/test/kotlin):
- Test domain logic in isolation
- Mock external dependencies (use case tests mock repository interfaces)
- Fast execution (<100ms per test)
- No database, no network, no file I/O
- File naming: `*UnitTest.kt`

**Integration Tests** (in plugins modules):
- Test repository implementations with Testcontainers
- Test message queue interactions with embedded brokers
- Slower execution acceptable (setup overhead)
- File naming: `*IntegTest.kt`

**Contract Tests** (in api module):
- Test REST endpoints with real database (Testcontainers)
- Validate request/response formats
- Verify error handling and status codes
- File naming: `*IntegTest.kt` (contract tests are integration tests)

### Database Schema Workflow

1. Design schema change (DDL for reference)
2. Create Liquibase changeset with unique ID (timestamp-based recommended)
3. Add rollback strategy if applicable
4. Test changeset locally with Liquibase update/rollback
5. Commit changeset file (XML/YAML/SQL format)
6. Deploy via Liquibase during application startup or separate migration step

**Changeset ID Format**: `YYYYMMDD-HHMM-descriptive-name` (e.g., `20251121-1500-add-workflow-revisions-table`)

### Code Review Requirements

- All changes require PR review
- Tests must pass before merge
- Constitution compliance verified
- Breaking changes require migration plan
- Database schema changes reviewed by DBA or tech lead
- DTO introduction requires performance justification in PR description

## Governance

### Constitution Authority

This constitution supersedes all other development practices and guidelines. When conflicts arise between this document and other policies, this constitution takes precedence.

### Amendment Process

1. Propose change with rationale
2. Discuss impact on existing code and practices
3. Require approval from project maintainers
4. Update version following semantic versioning:
   - MAJOR: Backward incompatible principle removal or redefinition
   - MINOR: New principle added or material expansion
   - PATCH: Clarifications, wording, typo fixes
5. Update dependent templates and documentation
6. Announce change to team with migration timeline

### Compliance Review

- **Pre-implementation**: Verify plan aligns with principles
- **During development**: Tests enforce architecture boundaries
- **Code review**: Reviewer validates constitution adherence
- **Post-deployment**: Retrospective on principle effectiveness

### Complexity Justification

Any violation of these principles requires explicit justification:
- Document the specific need
- Explain why simpler alternative was rejected
- Obtain approval before implementation
- Add to technical debt log if temporary

**Version**: 1.2.0 | **Ratified**: 2025-11-21 | **Last Amended**: 2025-11-21
