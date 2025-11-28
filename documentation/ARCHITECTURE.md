# Maestro Architecture

Last updated: 2025-11-27

This document describes the architecture and design of the Maestro workflow orchestration system, including module organization, execution flow, and key design patterns.

## Table of Contents
- System Overview
- Module Architecture
- Workflow Execution Flow
- Domain Model
- Step Execution Engine
- Persistence Strategy
- Error Handling
- Design Patterns
- Performance Characteristics

---

## System Overview

Maestro is a workflow orchestration system that executes hierarchical task workflows with typed input parameters. The system follows domain-driven design principles with clear separation between domain logic, business logic, and infrastructure concerns.

### Key Characteristics

- **Synchronous Execution**: Workflows execute synchronously in the orchestrator (v1)
- **Hierarchical Tasks**: Tasks can be composed of other tasks (Sequence, If, etc.)
- **Type-Safe Parameters**: Input parameters are validated against defined schemas
- **Step-Level Tracking**: Every step execution is tracked with inputs, outputs, and errors
- **Crash Recovery**: Per-step commits enable recovery from failures
- **Immutable Revisions**: Workflow definitions are versioned and immutable

---

## Module Architecture

### Dependency Flow

```
api → core → model
ui (independent, WIP)
plugins/postgres → core
```

### Module Responsibilities

**model** (`/model`)
- Pure Kotlin domain model with no external dependencies (kotlin-stdlib only)
- Contains:
  - Workflow domain entities (`WorkflowRevision`, `WorkflowRevisionID`)
  - Execution domain entities (`WorkflowExecution`, `ExecutionStepResult`, `ExecutionContext`)
  - Task abstractions (`Step`, `OrchestrationTask`)
  - Concrete task implementations (`Sequence`, `If`, `LogTask`, `WorkTask`)
  - Step executor interface (`IStepExecutor`)
  - Value objects (`ParameterSchema`, `ExecutionStatus`, `StepStatus`)
- Design principle: Keep domain logic pure and framework-agnostic

**core** (`/core`)
- Business logic layer depending on `model`
- Contains:
  - Use case implementations (Command pattern)
    - Workflow management: `CreateRevisionUseCase`, `ActivateRevisionUseCase`, etc.
    - Execution: `ExecuteWorkflowUseCase`, `GetExecutionStatusUseCase`, etc.
  - Repository interfaces (Port pattern)
    - `IWorkflowRevisionRepository`
    - `IWorkflowExecutionRepository`
  - Domain services
    - `StepExecutor`: Wraps step execution with persistence
    - `ParameterValidator`: Validates input parameters against schemas
  - Exception definitions (domain exceptions)
- Design principle: Orchestrate domain objects, define contracts for adapters

**api** (`/api`)
- REST API layer built with Quarkus
- Contains:
  - JAX-RS REST resources (`WorkflowResource`, `ExecutionResource`)
  - Request/Response DTOs (`ExecutionRequest`, `ExecutionResponse`, etc.)
  - Exception mappers for RFC 7807 Problem Details
  - Configuration (`application.properties`)
- Design principle: Thin adapter layer, delegate to use cases

**plugins/postgres** (`/plugins/postgres`)
- PostgreSQL persistence adapter
- Contains:
  - Repository implementations using JDBI 3.x
  - Database schema migrations
  - JSONB serialization for complex types
- Design principle: Implement repository interfaces, isolate DB concerns

---

## Workflow Execution Flow

### High-Level Flow

```
1. API receives POST /api/executions with parameters
2. ExecutionResource validates request format
3. ExecuteWorkflowUseCase orchestrates execution:
   a. Retrieve workflow revision from repository
   b. Validate input parameters against schema
   c. Generate unique execution ID (NanoID)
   d. Create WorkflowExecution record (status=RUNNING)
   e. Build ExecutionContext with parameters
   f. Execute workflow steps via StepExecutor
   g. Update execution status to COMPLETED/FAILED
   h. Return execution ID
4. ExecutionResource returns ExecutionResponse with links
```

### Detailed Execution Flow

```
ExecuteWorkflowUseCase.execute()
├─ Load WorkflowRevision from repository
│  └─ Throws WorkflowNotFoundException if not found
├─ Validate parameters with ParameterValidator
│  └─ Throws ParameterValidationException if invalid
├─ Generate executionId (NanoID 21 chars)
├─ Create WorkflowExecution (status=RUNNING)
│  └─ Save to repository (transactional)
├─ Build ExecutionContext
│  ├─ inputParameters: Map<String, Any>
│  ├─ stepOutputs: empty map
│  └─ stepExecutor: StepExecutor instance
├─ Execute workflow steps
│  └─ StepExecutor.executeSequence(steps, context)
│     ├─ For each step in sequence:
│     │  ├─ Increment stepIndex
│     │  ├─ Call step.execute(context)
│     │  │  └─ Step-specific logic
│     │  ├─ Create ExecutionStepResult
│     │  ├─ Save step result to repository (per-step commit)
│     │  ├─ Update context with step output
│     │  └─ If step fails: mark remaining steps as SKIPPED
│     └─ Return final context
├─ Update execution status
│  ├─ If all steps completed: COMPLETED
│  ├─ If any step failed: FAILED with errorMessage
│  └─ Save to repository (transactional)
└─ Return executionId
```

### Step Execution Details

Each step type implements the `Step` interface:

```kotlin
interface Step {
    fun execute(context: ExecutionContext): Any?
}
```

**Sequence** (Orchestration)
- Executes child steps sequentially
- Delegates to `context.stepExecutor.executeSequence()`
- Returns null (orchestration step)

**If** (Orchestration)
- Evaluates condition expression
- Executes `thenSteps` or `elseSteps` based on condition
- Returns null (orchestration step)

**LogTask** (Work)
- Logs message to stdout/logger
- Returns null

**WorkTask** (Work)
- Executes arbitrary work (placeholder in v1)
- Returns work result

---

## Domain Model

### Core Entities

**WorkflowRevision**
- Immutable workflow definition
- Properties:
  - `revisionId`: Composite ID (namespace, workflowId, version)
  - `name`: Display name
  - `description`: Description
  - `parameters`: List of `ParameterSchema`
  - `steps`: Root step (typically a Sequence)
  - `active`: Activation status
  - `createdAt`, `updatedAt`: Timestamps
- Invariants:
  - Version starts at 1 and increments
  - Parameters must have unique names
  - Cannot modify once created (only active flag changes)

**WorkflowExecution**
- Represents a single workflow run
- Properties:
  - `executionId`: Unique NanoID (21 chars, URL-safe)
  - `revisionId`: Reference to WorkflowRevision
  - `inputParameters`: Validated input map
  - `status`: ExecutionStatus (PENDING/RUNNING/COMPLETED/FAILED)
  - `errorMessage`: Error description if FAILED
  - `startedAt`, `completedAt`, `lastUpdatedAt`: Timestamps
- Invariants:
  - executionId, revisionId, inputParameters are immutable
  - errorMessage required when status=FAILED, null otherwise
  - completedAt required for terminal states, null otherwise
  - completedAt >= startedAt when set

**ExecutionStepResult**
- Represents outcome of a single step execution
- Properties:
  - `resultId`: Unique NanoID
  - `executionId`: Parent execution reference
  - `stepIndex`: 0-based ordinal position
  - `stepId`: Step identifier from workflow
  - `stepType`: Step type name
  - `status`: StepStatus (COMPLETED/FAILED/SKIPPED)
  - `inputData`: Step input context (JSONB)
  - `outputData`: Step output (JSONB, null if failed)
  - `errorMessage`, `errorDetails`: Error info if failed
  - `startedAt`, `completedAt`: Step timing
- Invariants:
  - All fields immutable (append-only)
  - stepIndex unique per execution and contiguous
  - errorMessage/errorDetails required when status=FAILED
  - outputData discarded for failed steps

**ExecutionContext**
- Carries execution state through workflow
- Properties:
  - `inputParameters`: Original workflow inputs
  - `stepOutputs`: Map of stepId → output
  - `stepExecutor`: IStepExecutor for nested orchestration
- Immutability: New context created when adding step outputs
- Scope: Hierarchically scoped for nested steps

### Value Objects

**ParameterSchema**
- Defines input parameter with name and type
- Supported types: string, integer, boolean, number

**ExecutionStatus** (Enum)
- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
- `isTerminal()`: Returns true for COMPLETED/FAILED/CANCELLED

**StepStatus** (Enum)
- PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
- `isTerminal()`: Returns true for COMPLETED/FAILED/SKIPPED

---

## Step Execution Engine

### StepExecutor

The `StepExecutor` wraps step execution with automatic result persistence.

**Responsibilities:**
- Execute steps and capture results
- Generate unique step result IDs
- Persist step results immediately after execution (checkpoint pattern)
- Handle exceptions and convert to ExecutionStepResult.FAILED
- Track step index for ordering
- Implement fail-fast strategy (stop on first failure)

**Key Methods:**

```kotlin
fun executeAndPersist(step: Step, context: ExecutionContext): Pair<ExecutionContext, ExecutionStepResult>
fun executeSequence(steps: List<Step>, context: ExecutionContext): ExecutionContext
fun getCurrentStepIndex(): Int
```

**Fail-Fast Strategy:**
When a step fails:
1. Create FAILED result for the failing step
2. Mark all subsequent steps as SKIPPED
3. Stop execution immediately
4. Return context with failure information

**Per-Step Commits:**
Each step result is saved in its own transaction:
- Enables checkpoint recovery if orchestrator crashes
- Allows partial execution state to be visible
- Trade-off: More DB round-trips vs. durability

---

## Persistence Strategy

### Repository Pattern

All persistence goes through repository interfaces defined in `core`:
- `IWorkflowRevisionRepository`
- `IWorkflowExecutionRepository`

Implementations live in adapter modules (e.g., `plugins/postgres`).

### Execution Repository Operations

**Create Operations:**
- `createExecution()`: Create new execution record
- `saveStepResult()`: Append step result (per-step transaction)

**Query Operations:**
- `findById()`: Retrieve execution with all step results
- `findByWorkflowRevision()`: Query executions for specific revision
- `findByWorkflow()`: Query executions across all versions
- Count methods for pagination

**Update Operations:**
- `updateExecutionStatus()`: Atomic status update with timestamps

**Design Constraints:**
- Execution ID, revision ID, input parameters are immutable
- Step results are append-only (never updated)
- Status updates are atomic with timestamp updates
- Step results eagerly loaded with execution for status queries

### Database Schema (PostgreSQL 18)

**workflow_executions** table:
- `execution_id` (TEXT, PK): NanoID
- `namespace`, `workflow_id`, `version` (FK to workflows)
- `input_parameters` (JSONB): Validated inputs
- `status` (TEXT): Current status
- `error_message` (TEXT): Error if failed
- `started_at`, `completed_at`, `last_updated_at` (TIMESTAMP)

**execution_step_results** table:
- `result_id` (TEXT, PK): NanoID
- `execution_id` (TEXT, FK): Parent execution
- `step_index` (INTEGER): Ordinal position
- `step_id` (TEXT): Step identifier
- `step_type` (TEXT): Step type name
- `status` (TEXT): Step status
- `input_data` (JSONB): Step inputs
- `output_data` (JSONB): Step outputs
- `error_message` (TEXT): Error if failed
- `error_details` (JSONB): Structured error info
- `started_at`, `completed_at` (TIMESTAMP)

**Indexes:**
- `execution_step_results(execution_id, step_index)` for ordered retrieval
- `workflow_executions(namespace, workflow_id, version, started_at DESC)` for history queries

---

## Error Handling

### Exception Hierarchy

All exceptions follow domain-driven design:

**Domain Exceptions (core module):**
- `WorkflowRevisionNotFoundException`
- `WorkflowNotFoundException`
- `ExecutionNotFoundException`
- `ParameterValidationException`
- `RevisionNotActiveException`
- `ActiveRevisionException`

### RFC 7807 Problem Details

All API errors return `application/problem+json`:

```json
{
  "type": "https://maestro/errors/{error-type}",
  "title": "Human-readable error title",
  "status": 400,
  "detail": "Detailed error message"
}
```

**Exception Mappers (in api module):**
- Convert domain exceptions to Problem Details responses
- Map to appropriate HTTP status codes:
  - 400: Validation errors, bad input
  - 404: Resource not found
  - 409: State conflicts (e.g., deleting active revision)

---

## Design Patterns

### Patterns Used

**Repository Pattern**
- Abstracts persistence behind interfaces
- Enables testing with in-memory implementations
- Allows swapping persistence technologies

**Use Case Pattern (Command)**
- Each business operation is a separate use case class
- Single responsibility: orchestrate one business flow
- Easy to test in isolation
- Examples: `ExecuteWorkflowUseCase`, `ActivateRevisionUseCase`

**Dependency Injection**
- Quarkus CDI for API layer
- Constructor injection for all components
- Facilitates testing and modularity

**Immutable Value Objects**
- ExecutionContext, ParameterSchema, etc.
- Thread-safe by design
- Easier to reason about

**Checkpoint Pattern**
- Per-step commits for crash recovery
- Trade-off: Durability vs. performance
- Critical for long-running workflows

**Fail-Fast Strategy**
- Stop on first step failure
- Simplifies error handling
- Clear failure semantics

**HATEOAS (Hypermedia)**
- API responses include `_links` to related resources
- Enables API discoverability
- Decouples client from URL structure

---

## Performance Characteristics

### Target Metrics (from SC-001, SC-002, SC-003)

- **Execution Initiation**: < 2 seconds (SC-001)
- **State Persistence**: All state persisted before returning (SC-002)
- **Status Queries**: < 1 second (SC-003)

### Performance Considerations

**Synchronous Execution (v1):**
- Workflow executes in API request thread
- Suitable for short-lived workflows (< 30 seconds)
- Future: Async workers for long-running workflows

**Per-Step Commits:**
- Each step result is a separate DB transaction
- Trade-off: More DB round-trips vs. durability
- Impact: ~5-10ms per step for DB commit
- Acceptable for SC-001 target with typical workflow sizes

**Eager Loading:**
- Execution status queries load all step results
- Acceptable for typical workflows (< 100 steps)
- Future: Pagination for large workflows

**Database Indexes:**
- Execution ID lookups: O(1) via primary key
- Workflow history queries: Indexed by (namespace, workflow_id, version, started_at DESC)
- Step results: Indexed by (execution_id, step_index) for ordered retrieval

**NanoID Generation:**
- 21-character URL-safe IDs
- Cryptographically random
- Negligible performance impact (< 1ms)

---

## Future Enhancements

### Async Workers
- Decouple execution from API request
- Support long-running workflows (hours/days)
- Requires message queue (Kafka, RabbitMQ)

### Parallel Execution
- Execute independent steps concurrently
- Requires DAG-based workflow model
- Requires distributed coordination (locks, semaphores)

### Execution History Pagination
- Paginate step results for large workflows
- Requires cursor-based pagination

### Workflow Cancellation
- Support user-initiated cancellation
- Requires distributed cancellation signals
- Status: CANCELLED

### Retries and Circuit Breakers
- Automatic retry on transient failures
- Circuit breaker pattern for external services

### Execution Metrics
- Prometheus metrics for execution duration, step counts, failure rates
- Grafana dashboards for observability

---

## Related Documentation

- Developer Guide: `DEVELOPER_GUIDE.md`
- API User Guide: `API_USER_GUIDE.md`
- Data Model: `DATA_MODEL.md`
- Error Handling: `ERROR_HANDLING.md`
- Specs: `specs/001-workflow-management`, `specs/002-workflow-execution`

---

If you find inconsistencies or missing details, please open an issue or submit a PR updating `documentation/ARCHITECTURE.md`.
