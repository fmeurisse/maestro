# Research Findings: Workflow Execution with Input Parameters

**Feature**: 002-workflow-execution
**Date**: 2025-11-25
**Phase**: 0 - Research

This document consolidates research findings for all architectural unknowns identified in the Technical Context section of the implementation plan.

## 1. Execution Engine Design Pattern

### Decision: Self-Executing Step Pattern (Strategy Pattern)

**Rationale**: Each Step implementation contains its own execution logic via an `execute()` method on the Step interface. This aligns with the existing architecture where `Task` already has an `execute()` method, and simplifies the execution model by avoiding external visitors. The Step interface acts as both the strategy abstraction and execution contract.

**Implementation Approach**:
- The `Step` interface (in `model` module) defines `execute(context: ExecutionContext): StepResult` method
- Each concrete step type implements `execute()` with type-specific logic:
  - `Sequence.execute()`: Iterates through child steps, calling `execute()` recursively, accumulating results
  - `If.execute()`: Evaluates condition against context, executes appropriate branch recursively
  - `LogTask.execute()`: Performs logging action, returns result with output
  - `WorkTask.execute()`: Delegates to task-specific work logic, returns result
- For recursive traversal of `OrchestrationStep` children, each step calls `child.execute(context)` on its children
- Matches Composite pattern where each node knows how to execute itself and its children

**Extensibility via Plugin Step Types**:
- New step types implement the `Step` interface with custom `execute()` logic
- ServiceLoader pattern (existing in codebase) registers custom step types for YAML deserialization
- Plugin-provided steps are discovered and executed polymorphically via the `Step.execute()` contract
- Supports Open/Closed principle—new step types just implement the interface, no registry changes needed

**Context Propagation**:
- Use immutable `ExecutionContext` data class containing input parameters and map of step outputs keyed by step ID
- Create child contexts: `context.withStepOutput(stepId, output)` returns new context with accumulated results
- Each step's `execute()` method receives context and returns updated context with its output
- Method signature: `fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext>`
- For `If` steps, evaluate condition against current context before deciding which branch to execute
- Orchestration steps (Sequence, If) pass context through child steps sequentially or conditionally

**Alternatives Considered**:
- External Visitor Pattern: Adds complexity with separate visitor interface, doesn't leverage existing Task.execute() pattern
- Interpreter Pattern: Too heavyweight, designed for language parsing not workflow execution
- Command Pattern with external invoker: Separates execution from steps, violates existing design where Task has execute()

## 2. Parameter Validation Strategy

### Decision: Custom Domain Validator with Bean Validation for API Augmentation

**Rationale**: Parameter validation is domain logic, not infrastructure concern. A custom `ParameterValidator` in `core` module keeps business rules aligned with DDD principles, consistent with existing patterns (e.g., `WorkflowYamlParser` in core, not api).

**Implementation Approach**:
```kotlin
// core/src/main/kotlin/io/maestro/core/execution/validation/
class ParameterValidator {
    fun validate(
        parameters: Map<String, Any?>,
        schema: List<ParameterDefinition>,
        workflowRevisionId: WorkflowRevisionID
    ): ValidationResult // Returns all errors at once
}

data class ParameterDefinition(
    val name: String,
    val type: ParameterType, // enum: STRING, INTEGER, FLOAT, BOOLEAN
    val required: Boolean = true,
    val default: Any? = null,
    val description: String? = null
)
```

**Type Coercion Strategy**: Moderately strict with explicit, limited coercion
- **Accept**: Numeric strings for integer/float (`"123"` → `123`), `"true"`/`"false"` for boolean, whitespace trimming
- **Reject**: Floats for integers (precision loss), integers for booleans (ambiguous), type mismatches losing data
- **Rationale**: Workflow parameters often come from environment variables/CI/CD systems that represent everything as strings, but maintain strictness for ambiguous conversions

**Error Message Format**: RFC 7807 with `invalid-params` extension
```json
{
  "type": "https://maestro.io/problems/workflow-parameter-validation-error",
  "title": "Workflow Parameter Validation Failed",
  "status": 400,
  "detail": "2 parameter validation errors occurred",
  "instance": "/workflows/production/payment-processing/v3/execute",
  "invalid-params": [
    {
      "name": "age",
      "reason": "must be a positive integer",
      "provided": "twenty-five"
    },
    {
      "name": "retryCount",
      "reason": "required parameter missing",
      "provided": null
    }
  ]
}
```

**Default Value Handling**:
- Apply defaults during validation phase (before execution starts)
- Ensures execution context receives complete, validated parameters
- Defaults visible in execution logs (FR-006: persist input parameters)
- Store applied defaults in execution record for audit trail

**Alternatives Considered**:
- JSON Schema: Heavyweight dependency for simple type checking
- Bean Validation only: Works for static schemas, not dynamic runtime schemas
- Lenient coercion: Too permissive, hides user errors

## 3. State Persistence Transaction Boundaries

### Decision: Per-Step Commits with Single-Handle Transactions

**Rationale**: Synchronous execution with up to 50 sequential steps (SC-005) requires incremental persistence to prevent data loss on crash. Committing after each step ensures completed work survives orchestrator failures while maintaining performance within 2-second execution window (SC-001).

**Transaction Strategy**:
- Each step execution is an atomic unit of work committed independently
- Use single JDBI handle across all steps to avoid connection pool exhaustion
- Checkpoint pattern: persist step result after completion before continuing
- ~20ms per commit × 50 steps = ~1 second overhead, well within SC-001 budget

**Implementation Pattern**:
```kotlin
fun execute(revisionId: WorkflowRevisionID, parameters: Map<String, Any>): WorkflowExecutionID {
    val executionId = generateExecutionID()

    // Initial state (first commit)
    executionRepo.createExecution(executionId, revisionId, parameters, status = RUNNING)

    try {
        workflow.steps.forEach { step ->
            val stepResult = executeStep(step, context)
            // Checkpoint (commit per step)
            executionRepo.saveStepResult(executionId, stepResult)
        }
        // Final state (final commit)
        executionRepo.updateExecutionStatus(executionId, COMPLETED)
    } catch (e: Exception) {
        // Failure state (recovery commit)
        executionRepo.updateExecutionStatus(executionId, FAILED, error = e.message)
        throw e
    }

    return executionId
}
```

**Crash Recovery**: Checkpoint-based without automatic resume
- Execution records remain in `RUNNING` status with `last_updated_at` timestamp
- Future cleanup job marks stale executions as `FAILED` after timeout
- Users manually re-trigger workflow for new execution attempt
- Automatic resume deferred (requires idempotent steps, compensation logic)

**Optimistic Locking**: NOT RECOMMENDED
- Execution records are single-writer (only creating orchestrator updates state)
- No concurrent modification by multiple processes
- Step results are immutable (append-only)
- Version columns add complexity without benefit
- Exception: Future distributed orchestration with competing workers would need locking

**Performance Implications**:
- Low latency: 5,000-10,000+ commits/sec on modern PostgreSQL
- Linear scaling: Total commit overhead ~1 second for 50 steps
- Connection efficiency: Single handle avoids pool exhaustion
- Query performance: Real-time status queries benefit from indexed committed data

**Alternatives Considered**:
- Single transaction for entire execution: Risk of deadlocks, connection timeouts, complete data loss on crash
- Two-phase commit: Adds complexity, not supported by JDBI without distributed transaction coordinator
- Eventual consistency: Violates SC-002 (100% persistence before response)

## 4. Error Handling and Step Failure Recovery

### Decision: Fail-Fast with Comprehensive Error Capture

**Rationale**: Synchronous execution model makes fail-fast the simplest and clearest strategy. Continuing after failures adds complexity without meaningful benefit. Aligns with User Story 3 acceptance criteria: "steps 1-2 as completed, step 3 as failed with error details, and remaining steps as cancelled."

**Failure Recovery Strategy**:
- When any step fails, immediately halt workflow execution
- Mark failed step as `FAILED` with error details
- Mark workflow overall status as `FAILED`
- Mark remaining steps (N+1 through end) as `SKIPPED`
- Persist all state changes atomically before returning API response

**Error Information Capture** (comprehensive context):
- **Step-level**: status (FAILED), error message, full stack trace, step input parameters, partial output if available, start/end timestamps
- **Workflow-level**: aggregate status (FAILED), execution duration, completed vs failed step counts, input parameters
- **Storage**: JSONB for error details (`error_message` TEXT + `error_details` JSONB containing stackTrace, errorType, stepInputs)
- **Rationale**: SC-008 requires sufficient detail for diagnosis; stack traces essential for debugging custom WorkTask implementations

**Status Transition Rules** (five-state model):
- **Execution states**: PENDING → RUNNING → COMPLETED|FAILED|CANCELLED
- **Step states**: PENDING → RUNNING → COMPLETED|FAILED|SKIPPED
- **CANCELLED**: Reserved for future user-initiated cancellation (out of scope but planned)
- **FAILED**: Abnormal termination due to step error, timeout, or system exception
- **SKIPPED**: Explicitly shows steps not attempted due to earlier failure

**Timeout Handling** (two-level strategy):
- **Workflow-level**: 5-10 minutes (per spec assumption), acts as safety net
- **Step-level**: Optional `timeoutSeconds: Int?` property (future enhancement), default 60s for Tasks
- **Timeout treatment**: Treat as step failure, capture TimeoutException in error details, follow fail-fast
- **Rationale**: Prevents resource exhaustion, hung connections, misbehaving custom tasks

**Compensating Transactions**: Explicitly out of scope for MVP (spec lists "automatic recovery" as out of scope)

**Alternatives Considered**:
- Continue execution after failure: Confusing partial states, unclear causality
- Saga pattern with compensation: Requires event sourcing, distributed transaction coordinator (too complex for v1)
- Automatic retry: Out of scope per spec, adds non-determinism

## 5. Execution ID Generation Strategy

### Decision: UUID v7 (Time-Ordered UUIDs)

**Rationale**: UUID v7 provides optimal balance of global uniqueness, time-ordered sortability, and zero coordination overhead. Aligns with existing application-generated ID pattern (WorkflowRevisionID) and supports high-concurrency distributed execution.

**Implementation**:
- **Generation**: Application code using UUID v7 library (e.g., java-uuid-generator with v7 extension)
- **Storage**: PostgreSQL `UUID` type (16 bytes, B-tree indexed)
- **API Format**: 32-character lowercase hex string without hyphens (e.g., `018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d`)
- **REST Examples**:
  - `GET /api/executions/018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d`
  - `POST /api/workflows/{namespace}/{id}/{version}/execute` returns `{"executionId": "018c9c6c..."}`

**Advantages**:
- **Globally unique**: Cryptographically strong, eliminates race conditions across distributed systems
- **Time-ordered**: Millisecond-precision timestamp in most significant bits enables natural chronological sorting
- **Database native**: PostgreSQL UUID type with 16-byte storage (vs 36-byte VARCHAR, 55% space saving)
- **Zero coordination**: No database roundtrips, sequences, or locks—scales horizontally
- **Index efficiency**: Time-ordered structure prevents fragmentation (common with UUID v4 random)
- **Future-proof**: Aligns with emerging RFC draft, adopted by GitHub, Stripe

**Performance Implications**:
- **Write**: 1000+ executions/sec with minimal overhead, no database contention
- **Index**: B-tree on time-ordered UUIDs performs comparably to BIGSERIAL
- **Query**: Efficient range queries (`WHERE created_at > timestamp`)
- **Scalability**: Linear scaling with application instances

**Alternatives Considered**:
- **UUID v4**: Random UUIDs cause index fragmentation, lack sortability
- **ULID**: Similar benefits but less standard, requires custom PostgreSQL extension
- **Database sequences**: Single point of contention, doesn't scale horizontally, requires roundtrip

## Summary of Architectural Decisions

All NEEDS CLARIFICATION items from Technical Context have been resolved:

| Area | Decision | Key Rationale |
|------|----------|---------------|
| **Execution Engine** | Self-Executing Step Pattern (Strategy) | Steps contain their own execute() logic, aligns with existing Task.execute(), supports plugin extensibility polymorphically |
| **Parameter Validation** | Custom domain validator in core module | Domain logic belongs in core, supports dynamic runtime schemas, integrates with RFC 7807 |
| **Execution Context** | Immutable ExecutionContext with hierarchical scopes | Prevents side effects, enables future parallelization, clear data flow |
| **State Persistence** | Per-step commits with checkpoint pattern | Balances durability with performance, enables crash recovery, aligns with existing JDBI patterns |
| **Error Handling** | Fail-fast with comprehensive capture | Simplest strategy for synchronous execution, clear causality, excellent observability |
| **Execution IDs** | UUID v7 (time-ordered) | Global uniqueness, sortability, zero coordination, aligns with application-generated ID pattern |

These decisions collectively support:
- **SC-001**: <2s execution initiation (minimal overhead from UUID generation, per-step commits)
- **SC-002**: 100% persistence before response (per-step commit strategy)
- **SC-003**: <1s status queries (indexed UUID, committed state)
- **SC-004**: <500ms validation (custom validator without heavyweight dependencies)
- **SC-007**: 95% success rate (fail-fast prevents cascading failures)
- **SC-008**: Sufficient error detail (comprehensive error capture with stack traces)

**Next Phase**: Proceed to Phase 1 (Design & Contracts) with these architectural foundations established.
