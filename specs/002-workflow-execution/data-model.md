# Data Model: Workflow Execution

**Feature**: 002-workflow-execution
**Date**: 2025-11-25
**Phase**: 1 - Design

This document defines the domain entities, value objects, and their relationships for workflow execution tracking.

## Entity Relationship Diagram

```
┌─────────────────────────────┐
│   WorkflowRevision          │ (from 001-workflow-management)
│ ─────────────────────────── │
│ namespace: String            │
│ id: String                   │
│ version: Int                 │
│ steps: List<Step>           │
│ parameters: List<ParamDef>  │
└──────────────┬──────────────┘
               │ 1
               │ references
               │ N
┌──────────────▼──────────────┐
│   WorkflowExecution         │
│ ─────────────────────────── │
│ executionId: UUID            │◄───────┐
│ revisionId: WorkflowRevID   │        │ 1
│ inputParameters: Map        │        │
│ status: ExecutionStatus     │        │ parent
│ errorMessage: String?       │        │
│ startedAt: Instant          │        │ N
│ completedAt: Instant?       │        │
│ lastUpdatedAt: Instant      │        │
└─────────────────────────────┘        │
                                        │
                                        │
┌───────────────────────────────────────┘
│   ExecutionStepResult       │
│ ─────────────────────────── │
│ resultId: UUID               │
│ executionId: UUID            │ (FK)
│ stepIndex: Int               │
│ stepId: String               │
│ stepType: String             │
│ status: StepStatus           │
│ inputData: Map?              │
│ outputData: Map?             │
│ errorMessage: String?        │
│ errorDetails: ErrorInfo?     │
│ startedAt: Instant           │
│ completedAt: Instant         │
└─────────────────────────────┘
```

## Domain Entities

### WorkflowExecution

Represents a single run of a workflow revision with its overall execution state.

**Package**: `io.maestro.model.execution`

**Attributes**:
| Name | Type | Description | Constraints |
|------|------|-------------|-------------|
| `executionId` | `WorkflowExecutionID` | Unique identifier (UUID v7) | Primary key, immutable |
| `revisionId` | `WorkflowRevisionID` | Reference to executed workflow revision | Immutable, foreign key to workflow_revisions |
| `inputParameters` | `Map<String, Any>` | Input parameters provided for execution | Immutable, validated against revision schema |
| `status` | `ExecutionStatus` | Current execution state | Mutable: PENDING → RUNNING → COMPLETED\|FAILED\|CANCELLED |
| `errorMessage` | `String?` | Human-readable error message if failed | Null unless status = FAILED |
| `startedAt` | `Instant` | Execution start timestamp | Immutable, set on creation |
| `completedAt` | `Instant?` | Execution completion timestamp | Null while RUNNING/PENDING, set when terminal state reached |
| `lastUpdatedAt` | `Instant` | Last state change timestamp | Updated on every status change |

**Business Rules**:
- `executionId` generated using UUID v7 (time-ordered) for global uniqueness and sortability
- `inputParameters` must match the parameter schema defined in `revisionId` (validated before execution starts)
- `status` transitions are unidirectional: cannot go from terminal state (COMPLETED/FAILED/CANCELLED) back to RUNNING
- `completedAt` must be >= `startedAt` when set
- `lastUpdatedAt` updated atomically with any state change

**Immutability**:
- Immutable fields: `executionId`, `revisionId`, `inputParameters`, `startedAt`
- Mutable fields: `status`, `errorMessage`, `completedAt`, `lastUpdatedAt`

### ExecutionStepResult

Represents the outcome of executing a single step within a workflow execution.

**Package**: `io.maestro.model.execution`

**Attributes**:
| Name | Type | Description | Constraints |
|------|------|-------------|-------------|
| `resultId` | `UUID` | Unique identifier for this result | Primary key, UUID v4 (order not important) |
| `executionId` | `WorkflowExecutionID` | Parent execution reference | Immutable, foreign key to workflow_executions |
| `stepIndex` | `Int` | Ordinal position in execution sequence | Immutable, 0-based index |
| `stepId` | `String` | Step identifier from workflow definition | Immutable, for correlation with workflow YAML |
| `stepType` | `String` | Step type name (e.g., "Sequence", "LogTask") | Immutable, for display/filtering |
| `status` | `StepStatus` | Step execution outcome | Immutable once set (append-only) |
| `inputData` | `Map<String, Any>?` | Step input context at execution time | Immutable, null for steps without inputs |
| `outputData` | `Map<String, Any>?` | Step output/return value | Immutable, null for steps without outputs or failed steps |
| `errorMessage` | `String?` | Human-readable error if failed | Null unless status = FAILED |
| `errorDetails` | `ErrorInfo?` | Detailed error information | Null unless status = FAILED |
| `startedAt` | `Instant` | Step execution start time | Immutable |
| `completedAt` | `Instant` | Step execution completion time | Immutable, must be >= startedAt |

**Business Rules**:
- `stepIndex` must be unique within the parent `executionId`
- `stepIndex` values form contiguous sequence starting at 0
- `status` values: PENDING (not yet executed), RUNNING (currently executing), COMPLETED (success), FAILED (error), SKIPPED (not executed due to earlier failure)
- `errorMessage` and `errorDetails` required when `status = FAILED`
- `outputData` should be null when `status = FAILED` (partial outputs discarded)
- `completedAt` - `startedAt` represents step execution duration

**Immutability**: All fields are immutable (append-only pattern). Results never updated after insertion.

## Value Objects

### WorkflowExecutionID

Wraps a UUID v7 identifier for type safety and domain semantics.

**Package**: `io.maestro.model.execution`

**Attributes**:
| Name | Type | Description |
|------|------|-------------|
| `value` | `UUID` | The UUID v7 value |

**Methods**:
- `toString(): String` - Returns lowercase hex string without hyphens (32 chars) for API serialization
- `companion object fromString(s: String): WorkflowExecutionID` - Parses from API format

**Characteristics**:
- Immutable value object
- UUID v7 format ensures time-ordered sortability
- Generated in application code (not database)
- Used in REST API paths: `/api/executions/{executionId}`

### ExecutionContext

Carries execution state through the workflow step tree.

**Package**: `io.maestro.model.execution`

**Attributes**:
| Name | Type | Description |
|------|------|-------------|
| `inputParameters` | `Map<String, Any>` | Original workflow input parameters |
| `stepOutputs` | `Map<String, Any>` | Accumulated outputs from completed steps, keyed by stepId |

**Methods**:
- `withStepOutput(stepId: String, output: Any): ExecutionContext` - Returns new context with added output
- `getParameter(name: String): Any?` - Retrieves input parameter value
- `getStepOutput(stepId: String): Any?` - Retrieves output from a previous step

**Characteristics**:
- Immutable (functional updates return new instances)
- Passed through execution visitor for context propagation
- Scoped hierarchically for nested orchestration steps

### ErrorInfo

Structured error details for failed steps.

**Package**: `io.maestro.model.execution`

**Attributes**:
| Name | Type | Description |
|------|------|-------------|
| `errorType` | `String` | Exception class name (e.g., "NullPointerException") |
| `stackTrace` | `String` | Full stack trace for debugging |
| `stepInputs` | `Map<String, Any>?` | Input values that caused the error (for reproduction) |

**Characteristics**:
- Immutable value object
- Stored as JSONB in database for flexible querying
- Filtered for sensitive data (passwords, API keys) before persistence

## Enumerations

### ExecutionStatus

Represents the overall state of a workflow execution.

**Package**: `io.maestro.model.execution`

**Values**:
| Value | Description | Terminal? |
|-------|-------------|-----------|
| `PENDING` | Execution created but not yet started | No |
| `RUNNING` | Execution in progress | No |
| `COMPLETED` | All steps completed successfully | Yes |
| `FAILED` | Execution failed due to step error or timeout | Yes |
| `CANCELLED` | Execution cancelled by user (future enhancement) | Yes |

**Transitions**:
```
PENDING → RUNNING → COMPLETED
              ↓
            FAILED
              ↓
          CANCELLED (future)
```

### StepStatus

Represents the outcome of a single step execution.

**Package**: `io.maestro.model.execution`

**Values**:
| Value | Description |
|-------|-------------|
| `PENDING` | Step not yet executed (initial state) |
| `RUNNING` | Step currently executing |
| `COMPLETED` | Step completed successfully |
| `FAILED` | Step failed with error |
| `SKIPPED` | Step not executed due to earlier failure in workflow |

**Usage**:
- SKIPPED is set when fail-fast strategy halts execution after a step fails
- RUNNING is transient (exists only during active execution, not persisted in v1)
- All other statuses are persisted to database

## Relationship to Existing Model

### Dependencies on 001-workflow-management Entities

**WorkflowRevision** (existing):
- `WorkflowExecution.revisionId` references `WorkflowRevision` (namespace, id, version)
- Execution reads `WorkflowRevision.steps` to determine execution sequence
- Execution validates `inputParameters` against `WorkflowRevision.parameters` schema

**Step hierarchy** (existing):
- `ExecutionStepResult.stepType` corresponds to concrete Step implementations (Sequence, If, LogTask, WorkTask)
- Step execution visitor pattern traverses `Step` tree from `WorkflowRevision.steps`

**ParameterDefinition** (new addition to WorkflowRevision):
```kotlin
data class ParameterDefinition(
    val name: String,
    val type: ParameterType, // STRING, INTEGER, FLOAT, BOOLEAN
    val required: Boolean = true,
    val default: Any? = null,
    val description: String? = null
)
```
- Part of `WorkflowRevision` entity (added in this feature)
- Defines schema for `WorkflowExecution.inputParameters`

## Validation Rules

### Cross-Entity Validation

1. **Execution Creation**:
   - Referenced `WorkflowRevision` must exist
   - `inputParameters` must satisfy all required parameters from `WorkflowRevision.parameters`
   - `inputParameters` types must match schema definitions
   - No extraneous parameters allowed (reject undeclared parameters)

2. **Step Result Creation**:
   - Parent `WorkflowExecution` must exist
   - `stepIndex` must be unique within execution
   - `stepId` should match a step in the referenced `WorkflowRevision.steps` (validation not enforced, for flexibility)

3. **State Transitions**:
   - `WorkflowExecution.status` can only transition forward (no backward transitions)
   - `WorkflowExecution.completedAt` can only be set when transitioning to terminal state

## Database Schema Considerations

### workflow_executions Table

```sql
CREATE TABLE workflow_executions (
    execution_id UUID PRIMARY KEY,
    revision_namespace VARCHAR(255) NOT NULL,
    revision_id VARCHAR(255) NOT NULL,
    revision_version INT NOT NULL,
    input_parameters JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (revision_namespace, revision_id, revision_version)
        REFERENCES workflow_revisions(namespace, id, version)
);

CREATE INDEX idx_executions_status ON workflow_executions(status, last_updated_at);
CREATE INDEX idx_executions_revision ON workflow_executions(revision_namespace, revision_id, revision_version);
CREATE INDEX idx_executions_started ON workflow_executions(started_at DESC);
```

### execution_step_results Table

```sql
CREATE TABLE execution_step_results (
    result_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES workflow_executions(execution_id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    step_id VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    error_details JSONB,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (execution_id, step_index)
);

CREATE INDEX idx_step_results_execution ON execution_step_results(execution_id, step_index);
CREATE INDEX idx_step_results_status ON execution_step_results(execution_id, status);
```

### Liquibase Changeset

Changeset ID: `20251125-1400-add-workflow-execution-tables`
Location: `plugins/postgres/src/main/resources/db/changelog/`

## Summary

This data model supports:
- **FR-006, FR-007**: Complete execution state persistence (execution + step results)
- **FR-011**: Unique execution ID generation (UUID v7)
- **FR-012**: Error detail capture (ErrorInfo value object)
- **SC-002**: 100% state persistence before response (per-step commits to execution_step_results)
- **SC-006**: 90+ day retention (database storage with indexes)
- **SC-010**: Complete execution logs (all step results with timing)

The design follows DDD principles with clear entity boundaries, immutability where appropriate, and clean separation between domain logic (model module) and persistence (plugins/postgres module).
