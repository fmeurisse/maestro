# Quickstart Guide: Workflow Execution

**Feature**: 002-workflow-execution
**Date**: 2025-11-25
**Audience**: Developers implementing this feature

This guide provides a quick overview of how to implement and use the workflow execution feature.

## Overview

The Workflow Execution feature enables users to:
1. Trigger workflow execution via REST API with typed input parameters
2. Monitor execution progress in real-time
3. View execution history for debugging and auditing
4. Access detailed step-by-step execution logs

## Architecture at a Glance

```
┌─────────────┐
│  API Layer  │  POST /workflows/{ns}/{id}/{v}/execute
│             │  GET  /executions/{executionId}
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   UseCase   │  ExecuteWorkflowUseCase
│   Layer     │  - Validate parameters
│             │  - Execute workflow steps
│             │  - Persist state per-step
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Execution  │  WorkflowExecutor (Visitor pattern)
│   Engine    │  - StepExecutor strategies
│             │  - ExecutionContext propagation
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Repository  │  IWorkflowExecutionRepository
│   Layer     │  - PostgreSQL persistence
│             │  - Per-step commits
└─────────────┘
```

## Implementation Checklist

### Phase 1: Domain Model (model module)

- [ ] Create `WorkflowExecution` entity with UUID v7 ID
- [ ] Create `ExecutionStepResult` entity
- [ ] Create `ExecutionStatus` enum (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)
- [ ] Create `StepStatus` enum (PENDING, RUNNING, COMPLETED, FAILED, SKIPPED)
- [ ] Create `ExecutionContext` value object
- [ ] Create `ErrorInfo` value object
- [ ] Add `ParameterDefinition` to `WorkflowRevision` (extends 001-workflow-management)
- [ ] Add `execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext>` method to `Step` interface
- [ ] Implement `execute()` in `Sequence` - iterate child steps, handle failures
- [ ] Implement `execute()` in `If` - evaluate condition, execute appropriate branch
- [ ] Implement `execute()` in `LogTask` - perform logging, return success
- [ ] Implement `execute()` in `WorkTask` - delegate to task logic, return result

### Phase 2: Core Logic (core module)

**Use Cases**:
- [ ] `ExecuteWorkflowUseCase` - Main execution orchestrator (calls step.execute() on workflow steps)
- [ ] `GetExecutionStatusUseCase` - Query by execution ID
- [ ] `GetExecutionHistoryUseCase` - Query execution history

**Validation**:
- [ ] `ParameterValidator` - Validate input parameters against schema
- [ ] `ParameterTypeValidator` - Type checking and coercion logic

**Repository Interface**:
- [ ] `IWorkflowExecutionRepository` - Interface for execution persistence

### Phase 3: Database (plugins/postgres module)

- [ ] Create Liquibase changeset `20251125-1400-add-workflow-execution-tables.xml`
  - `workflow_executions` table
  - `execution_step_results` table
  - Indexes for queries
- [ ] Implement `PostgresWorkflowExecutionRepository`
  - `createExecution()`
  - `saveStepResult()` - Per-step commit
  - `updateExecutionStatus()`
  - `findById()`
  - `findByWorkflowRevision()`

### Phase 4: API (api module)

**REST Resource**:
- [ ] `ExecutionResource` with JAX-RS endpoints
  - `POST /workflows/{ns}/{id}/{v}/execute`
  - `GET /executions/{executionId}`
  - `GET /workflows/{ns}/{id}/executions`

**DTOs**:
- [ ] `ExecutionRequestDTO` - Request body for execution
- [ ] `ExecutionResponseDTO` - Response with execution details
- [ ] `ExecutionDetailResponseDTO` - Full execution with step results
- [ ] `ExecutionHistoryResponseDTO` - Paginated execution list

**Error Handling**:
- [ ] `ExecutionProblemTypes` - RFC 7807 problem types
- [ ] `ParameterValidationException` - Maps to 400 with invalid-params
- [ ] `WorkflowNotFoundException` - Maps to 404
- [ ] Exception mapper integration with existing `MaestroException`

### Phase 5: Testing

**Unit Tests** (*UnitTest.kt):
- [ ] `ExecuteWorkflowUseCaseUnitTest` - Mock repository, verify execution flow
- [ ] `ParameterValidatorUnitTest` - All validation rules
- [ ] `SequenceUnitTest` - Test Sequence.execute() logic (child iteration, failure handling)
- [ ] `IfUnitTest` - Test If.execute() logic (condition evaluation, branch selection)
- [ ] `LogTaskUnitTest` - Test LogTask.execute() logic
- [ ] `WorkTaskUnitTest` - Test WorkTask.execute() logic

**Integration Tests** (*IntegTest.kt):
- [ ] `PostgresWorkflowExecutionRepositoryIntegTest` - Testcontainers PostgreSQL
  - Test per-step commits
  - Test execution queries
  - Test concurrent executions
- [ ] `ExecutionResourceIntegTest` - REST Assured contract tests
  - Test valid execution (200)
  - Test parameter validation errors (400)
  - Test workflow not found (404)
  - Test execution status query
  - Test execution history pagination

## Quick API Examples

### 1. Execute a Workflow

```bash
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/3/execute \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "userName": "alice",
      "retryCount": 3,
      "enableDebug": true
    }
  }'
```

**Response (200 OK)**:
```json
{
  "executionId": "018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d",
  "status": "RUNNING",
  "revisionId": {
    "namespace": "production",
    "id": "payment-processing",
    "version": 3
  },
  "inputParameters": {
    "userName": "alice",
    "retryCount": 3,
    "enableDebug": true
  },
  "startedAt": "2025-11-25T10:30:00Z",
  "_links": {
    "self": {
      "href": "/api/executions/018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d"
    },
    "workflow": {
      "href": "/api/workflows/production/payment-processing/3"
    }
  }
}
```

### 2. Get Execution Status

```bash
curl http://localhost:8080/api/executions/018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d
```

**Response (200 OK)** - Completed execution:
```json
{
  "executionId": "018c9c6c8c7a7b3e9f4a2d1e5f8b3c4d",
  "status": "COMPLETED",
  "revisionId": {
    "namespace": "production",
    "id": "payment-processing",
    "version": 3
  },
  "inputParameters": {
    "userName": "alice",
    "retryCount": 3
  },
  "startedAt": "2025-11-25T10:30:00Z",
  "completedAt": "2025-11-25T10:30:15Z",
  "steps": [
    {
      "stepIndex": 0,
      "stepId": "validate-user",
      "stepType": "WorkTask",
      "status": "COMPLETED",
      "outputData": {
        "userId": "12345",
        "valid": true
      },
      "startedAt": "2025-11-25T10:30:00Z",
      "completedAt": "2025-11-25T10:30:05Z"
    },
    {
      "stepIndex": 1,
      "stepId": "process-payment",
      "stepType": "WorkTask",
      "status": "COMPLETED",
      "outputData": {
        "transactionId": "txn_67890"
      },
      "startedAt": "2025-11-25T10:30:05Z",
      "completedAt": "2025-11-25T10:30:15Z"
    }
  ]
}
```

### 3. Get Execution History

```bash
curl "http://localhost:8080/api/workflows/production/payment-processing/executions?status=FAILED&limit=10"
```

**Response (200 OK)**:
```json
{
  "executions": [
    {
      "executionId": "018c9c6c8c7a7b3e9f4a2d1e5f8b3c3b",
      "status": "FAILED",
      "errorMessage": "Payment gateway timeout",
      "revisionVersion": 3,
      "startedAt": "2025-11-25T10:25:00Z",
      "completedAt": "2025-11-25T10:25:10Z",
      "stepCount": 5,
      "completedSteps": 2,
      "failedSteps": 1
    }
  ],
  "pagination": {
    "total": 1,
    "limit": 10,
    "offset": 0,
    "hasMore": false
  }
}
```

### 4. Parameter Validation Error

```bash
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/3/execute \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "retryCount": "invalid"
    }
  }'
```

**Response (400 Bad Request)**:
```json
{
  "type": "https://maestro.io/problems/workflow-parameter-validation-error",
  "title": "Workflow Parameter Validation Failed",
  "status": 400,
  "detail": "2 parameter validation errors occurred",
  "instance": "/api/workflows/production/payment-processing/3/execute",
  "timestamp": "2025-11-25T10:30:00Z",
  "invalidParams": [
    {
      "name": "retryCount",
      "reason": "must be a positive integer",
      "provided": "invalid"
    },
    {
      "name": "userName",
      "reason": "required parameter missing",
      "provided": null
    }
  ]
}
```

## Key Architectural Patterns

### 1. Self-Executing Step Pattern (Strategy Pattern)

Each Step implementation contains its own execution logic directly on the interface:

```kotlin
// Step interface with execute method
interface Step {
    fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext>
}

// Each concrete step implements execute() with type-specific logic
class Sequence(val steps: List<Step>) : OrchestrationStep {
    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        var currentContext = context
        for (step in steps) {
            val (status, updatedContext) = step.execute(currentContext)
            if (status == StepStatus.FAILED) {
                return Pair(StepStatus.FAILED, updatedContext)
            }
            currentContext = updatedContext
        }
        return Pair(StepStatus.COMPLETED, currentContext)
    }
}

class LogTask(val message: String) : Task {
    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        println(message) // Log the message
        return Pair(StepStatus.COMPLETED, context)
    }
}
```

**Benefits**:
- Aligns with existing architecture where `Task` already has `execute()` method
- Simple polymorphic execution via Step interface contract
- Each step type encapsulates its own execution logic
- Plugin-provided steps just implement Step interface, no registry changes needed

### 2. Immutable Execution Context

Context data flows through the execution tree immutably:

```kotlin
data class ExecutionContext(
    val inputParameters: Map<String, Any>,
    val stepOutputs: Map<String, Any> = emptyMap()
) {
    fun withStepOutput(stepId: String, output: Any): ExecutionContext {
        return copy(stepOutputs = stepOutputs + (stepId to output))
    }
}
```

**Benefits**: Thread-safe, prevents side effects, enables future parallelization.

### 3. Per-Step Transaction Commits

State is persisted incrementally after each step:

```kotlin
fun execute(revisionId: WorkflowRevisionID, parameters: Map<String, Any>): WorkflowExecutionID {
    val executionId = generateExecutionID()

    executionRepo.createExecution(executionId, revisionId, parameters, RUNNING) // Commit 1

    workflow.steps.forEach { step ->
        val result = executeStep(step, context)
        executionRepo.saveStepResult(executionId, result) // Commit per step
    }

    executionRepo.updateExecutionStatus(executionId, COMPLETED) // Final commit
    return executionId
}
```

**Benefits**: Crash recovery, real-time progress visibility, SC-002 compliance.

## Performance Considerations

- **SC-001 (<2s execution initiation)**: UUID v7 generation is sub-millisecond, parameter validation <500ms target
- **SC-002 (100% persistence)**: Per-step commits ensure no data loss before response
- **SC-003 (<1s status queries)**: Indexed execution_id and status columns, optimized JSONB queries
- **SC-005 (50 sequential steps)**: Per-step commit overhead ~1s total (20ms × 50)

## Testing Strategy

Follow TDD discipline strictly:
1. Write test FIRST (ensure it fails with "not implemented")
2. Implement minimal code to pass test
3. Refactor while keeping tests green

**Test file naming**:
- Unit tests: `*UnitTest.kt`
- Integration tests: `*IntegTest.kt`

**Test execution**:
```bash
# All tests
mvn test

# Unit tests only (fast)
mvn test -DskipIntegTests=true

# Integration tests only
mvn test -Dtest="*IntegTest"
```

## Next Steps

1. Review the [implementation plan](./plan.md) for complete technical details
2. Review the [data model](./data-model.md) for entity specifications
3. Review the [API contracts](./contracts/openapi.yaml) for REST endpoint details
4. Generate implementation tasks using `/speckit.tasks` command
5. Begin TDD implementation starting with `model` module

## Common Pitfalls to Avoid

1. **Don't execute workflows in API layer** - Always delegate to `ExecuteWorkflowUseCase` in core
2. **Don't skip per-step commits** - Required for SC-002 and crash recovery
3. **Don't use UUID v4** - Use UUID v7 for time-ordered benefits
4. **Don't lenient type coercion** - Follow strict validation rules from research.md
5. **Don't continue after step failure** - Fail-fast is the documented strategy
6. **Don't create DTOs between core and plugins** - Only at API boundary per constitution
7. **Don't use optimistic locking** - Execution is single-writer, not needed

## References

- Feature Specification: [spec.md](./spec.md)
- Research Findings: [research.md](./research.md)
- Implementation Plan: [plan.md](./plan.md)
- Data Model: [data-model.md](./data-model.md)
- API Contracts: [contracts/openapi.yaml](./contracts/openapi.yaml)
- Project Constitution: [../../.specify/memory/constitution.md](../../.specify/memory/constitution.md)
