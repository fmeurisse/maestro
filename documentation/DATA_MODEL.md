# Maestro Data Model

Last updated: 2025-11-27

This document describes the domain model and data structures used in the Maestro workflow orchestration system. It covers entities, value objects, and their relationships.

## Table of Contents
- Overview
- Workflow Domain
- Execution Domain
- Task Model
- Value Objects and Enums
- Database Schema
- Validation Rules
- Model Diagrams

---

## Overview

The Maestro data model follows domain-driven design principles with clear separation between:
- **Entities**: Objects with unique identity (WorkflowRevision, WorkflowExecution)
- **Value Objects**: Immutable objects defined by their attributes (ParameterSchema, ExecutionStatus)
- **Aggregates**: Clusters of entities and value objects (WorkflowRevision + Steps)

All domain models are defined in the `model` module with no external dependencies.

---

## Workflow Domain

### WorkflowRevisionID (Value Object)

Composite identifier for a workflow revision.

**Properties:**
- `namespace: String` - Logical grouping (e.g., "payments", "notifications")
- `workflowId: String` - Workflow identifier within namespace
- `version: Int` - Revision version number (starts at 1)

**Validation Rules:**
- namespace must not be blank
- workflowId must not be blank
- version must be >= 1

**String Representation:**
- Format: `{namespace}/{workflowId}/{version}`
- Example: `payments/process-payment/3`

**Kotlin Example:**
```kotlin
data class WorkflowRevisionID(
    val namespace: String,
    val workflowId: String,
    val version: Int
) {
    init {
        require(namespace.isNotBlank()) { "Namespace must not be blank" }
        require(workflowId.isNotBlank()) { "Workflow ID must not be blank" }
        require(version >= 1) { "Version must be >= 1" }
    }

    override fun toString(): String = "$namespace/$workflowId/$version"
}
```

---

### WorkflowRevision (Entity)

Immutable workflow definition with versioned steps and parameters.

**Properties:**
- `revisionId: WorkflowRevisionID` - Unique identifier
- `name: String` - Display name
- `description: String?` - Optional description
- `parameters: List<ParameterSchema>` - Input parameter definitions
- `steps: Step` - Root step (typically a Sequence containing all workflow steps)
- `active: Boolean` - Activation status
- `createdAt: Instant` - Creation timestamp
- `updatedAt: Instant` - Last update timestamp (changes when active flag changes)

**Validation Rules:**
- name must not be blank
- parameters must have unique names
- steps must be a valid Step implementation
- updatedAt >= createdAt

**Lifecycle:**
1. Created with version 1 (inactive by default)
2. Can be activated/deactivated (changes updatedAt)
3. New versions created via POST to existing workflow
4. Cannot be deleted if active
5. Cannot modify definition once created (only active flag changes)

**Kotlin Example:**
```kotlin
data class WorkflowRevision(
    val revisionId: WorkflowRevisionID,
    val name: String,
    val description: String?,
    val parameters: List<ParameterSchema>,
    val steps: Step,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(parameters.map { it.name }.distinct().size == parameters.size) {
            "Parameter names must be unique"
        }
        require(updatedAt >= createdAt) { "updatedAt must be >= createdAt" }
    }
}
```

---

### ParameterSchema (Value Object)

Defines an input parameter with name and type.

**Properties:**
- `name: String` - Parameter name
- `type: ParameterType` - Parameter type (string, integer, boolean, number)
- `required: Boolean` - Whether parameter is required (default: true)
- `defaultValue: Any?` - Default value if not provided (optional)

**Validation Rules:**
- name must not be blank
- defaultValue type must match declared type

**Supported Types:**
- `STRING` - Text values
- `INTEGER` - Whole numbers
- `BOOLEAN` - true/false
- `NUMBER` - Decimal numbers

**Kotlin Example:**
```kotlin
data class ParameterSchema(
    val name: String,
    val type: ParameterType,
    val required: Boolean = true,
    val defaultValue: Any? = null
) {
    init {
        require(name.isNotBlank()) { "Parameter name must not be blank" }
        if (defaultValue != null) {
            require(type.isValidValue(defaultValue)) {
                "Default value type must match declared type"
            }
        }
    }
}

enum class ParameterType {
    STRING, INTEGER, BOOLEAN, NUMBER;

    fun isValidValue(value: Any): Boolean = when (this) {
        STRING -> value is String
        INTEGER -> value is Int || value is Long
        BOOLEAN -> value is Boolean
        NUMBER -> value is Number
    }
}
```

---

## Execution Domain

### WorkflowExecution (Entity)

Represents a single workflow run with its current state.

**Properties:**
- `executionId: String` - Unique NanoID (21 chars, URL-safe)
- `revisionId: WorkflowRevisionID` - Reference to workflow revision
- `inputParameters: Map<String, Any>` - Validated input parameters
- `status: ExecutionStatus` - Current execution status
- `errorMessage: String?` - Error description if FAILED
- `startedAt: Instant` - Execution start timestamp
- `completedAt: Instant?` - Execution completion timestamp (null if not completed)
- `lastUpdatedAt: Instant` - Last status update timestamp

**Immutable Fields:**
- executionId (set at creation)
- revisionId (set at creation)
- inputParameters (set at creation)
- startedAt (set at creation)

**Mutable Fields:**
- status (updated as execution progresses)
- errorMessage (set when execution fails)
- completedAt (set when execution completes or fails)
- lastUpdatedAt (updated on every status change)

**Validation Rules:**
- executionId must be 21-character NanoID format
- errorMessage required when status=FAILED, null otherwise
- completedAt required for terminal states (COMPLETED/FAILED/CANCELLED), null otherwise
- completedAt >= startedAt when set
- lastUpdatedAt >= startedAt

**State Transitions:**
```
PENDING → RUNNING → COMPLETED
               ↓
            FAILED
```

**Factory Methods:**
```kotlin
companion object {
    fun create(executionId: String, revisionId: WorkflowRevisionID,
               inputParameters: Map<String, Any>): WorkflowExecution

    fun start(execution: WorkflowExecution): WorkflowExecution
}
```

**Update Methods:**
```kotlin
fun withStatus(newStatus: ExecutionStatus): WorkflowExecution
fun withFailure(errorMessage: String): WorkflowExecution
fun withCompletion(): WorkflowExecution
```

**Kotlin Example:**
```kotlin
data class WorkflowExecution(
    val executionId: String,
    val revisionId: WorkflowRevisionID,
    val inputParameters: Map<String, Any>,
    val status: ExecutionStatus,
    val errorMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val lastUpdatedAt: Instant
) {
    init {
        require(executionId.length == 21) { "Execution ID must be 21 chars (NanoID)" }
        require(status == ExecutionStatus.FAILED || errorMessage == null) {
            "errorMessage only allowed for FAILED status"
        }
        require(!status.isTerminal() || completedAt != null) {
            "completedAt required for terminal states"
        }
        if (completedAt != null) {
            require(completedAt >= startedAt) { "completedAt must be >= startedAt" }
        }
        require(lastUpdatedAt >= startedAt) { "lastUpdatedAt must be >= startedAt" }
    }
}
```

---

### ExecutionStepResult (Entity)

Represents the outcome of executing a single step within a workflow.

**Properties:**
- `resultId: String` - Unique NanoID for this result
- `executionId: String` - Parent execution reference
- `stepIndex: Int` - 0-based ordinal position in execution sequence
- `stepId: String` - Step identifier from workflow definition
- `stepType: String` - Step type name (for display/filtering)
- `status: StepStatus` - Step execution outcome
- `inputData: Map<String, Any>` - Step input context at execution time
- `outputData: Any?` - Step output/return value (null if failed)
- `errorMessage: String?` - Error description if failed
- `errorDetails: ErrorInfo?` - Structured error information if failed
- `startedAt: Instant` - Step start timestamp
- `completedAt: Instant` - Step completion timestamp

**Validation Rules:**
- All fields immutable (append-only pattern)
- stepIndex must be >= 0 and unique within execution
- stepIndex values must be contiguous (0, 1, 2, ..., no gaps)
- errorMessage and errorDetails required when status=FAILED
- errorMessage and errorDetails must be null when status != FAILED
- outputData discarded (set to null) for failed steps
- completedAt >= startedAt

**Factory Methods:**
```kotlin
companion object {
    fun createCompleted(
        executionId: String,
        stepIndex: Int,
        stepId: String,
        stepType: String,
        inputData: Map<String, Any>,
        outputData: Any?,
        startedAt: Instant,
        completedAt: Instant
    ): ExecutionStepResult

    fun createFailed(
        executionId: String,
        stepIndex: Int,
        stepId: String,
        stepType: String,
        inputData: Map<String, Any>,
        error: ErrorInfo,
        startedAt: Instant,
        completedAt: Instant
    ): ExecutionStepResult

    fun createSkipped(
        executionId: String,
        stepIndex: Int,
        stepId: String,
        stepType: String
    ): ExecutionStepResult
}
```

**Kotlin Example:**
```kotlin
data class ExecutionStepResult(
    val resultId: String,
    val executionId: String,
    val stepIndex: Int,
    val stepId: String,
    val stepType: String,
    val status: StepStatus,
    val inputData: Map<String, Any>,
    val outputData: Any?,
    val errorMessage: String?,
    val errorDetails: ErrorInfo?,
    val startedAt: Instant,
    val completedAt: Instant
) {
    init {
        require(stepIndex >= 0) { "stepIndex must be >= 0" }
        require(status != StepStatus.FAILED || (errorMessage != null && errorDetails != null)) {
            "errorMessage and errorDetails required for FAILED status"
        }
        require(status == StepStatus.FAILED || (errorMessage == null && errorDetails == null)) {
            "errorMessage and errorDetails only allowed for FAILED status"
        }
        require(completedAt >= startedAt) { "completedAt must be >= startedAt" }
    }
}
```

---

### ErrorInfo (Value Object)

Structured error information for failed steps.

**Properties:**
- `errorType: String` - Exception class name
- `stackTrace: String` - Full stack trace for debugging
- `stepInputs: Map<String, Any>` - Input values that caused error (for reproduction)

**Kotlin Example:**
```kotlin
data class ErrorInfo(
    val errorType: String,
    val stackTrace: String,
    val stepInputs: Map<String, Any>
)
```

**Database Storage:**
- Stored as JSONB in PostgreSQL for flexible querying
- Example JSON:
```json
{
  "errorType": "java.lang.IllegalArgumentException",
  "stackTrace": "java.lang.IllegalArgumentException: Invalid input\n\tat ...",
  "stepInputs": {
    "count": -1,
    "message": "test"
  }
}
```

---

### ExecutionContext (Value Object)

Carries execution state through the workflow step tree.

**Properties:**
- `inputParameters: Map<String, Any>` - Original workflow input parameters
- `stepOutputs: Map<String, Any>` - Accumulated outputs from completed steps (keyed by stepId)
- `stepExecutor: IStepExecutor` - Step executor for orchestration steps

**Immutability:**
- New context created when adding step outputs
- Uses copy-on-write pattern

**Methods:**
```kotlin
fun getParameter(name: String): Any?
fun getStepOutput(stepId: String): Any?
fun withStepOutput(stepId: String, output: Any?): ExecutionContext
```

**Hierarchical Scoping:**
- Parent context passed to child steps
- Child step outputs added to parent context
- Enables nested orchestration (Sequence within Sequence)

**Kotlin Example:**
```kotlin
data class ExecutionContext(
    val inputParameters: Map<String, Any>,
    val stepOutputs: Map<String, Any>,
    val stepExecutor: IStepExecutor
) {
    fun getParameter(name: String): Any? = inputParameters[name]

    fun getStepOutput(stepId: String): Any? = stepOutputs[stepId]

    fun withStepOutput(stepId: String, output: Any?): ExecutionContext =
        copy(stepOutputs = stepOutputs + (stepId to output))
}
```

---

## Task Model

### Step (Interface)

Base interface for all executable steps.

**Contract:**
```kotlin
interface Step {
    val stepId: String  // Unique identifier within workflow
    fun execute(context: ExecutionContext): Any?
}
```

**Implementations:**
- OrchestrationTask (abstract): Steps that orchestrate other steps
- Work tasks: Steps that perform actual work

---

### OrchestrationTask (Interface)

Interface for tasks that orchestrate other steps.

**Contract:**
```kotlin
interface OrchestrationTask : Step {
    // Inherits execute() from Step
    // Typically delegates to context.stepExecutor for child steps
}
```

**Implementations:**
- Sequence: Execute steps sequentially
- If: Conditional execution
- (Future: Parallel, While, Switch, etc.)

---

### Sequence (Orchestration)

Executes a list of steps sequentially.

**Properties:**
- `stepId: String` - Step identifier
- `steps: List<Step>` - Child steps to execute in order

**Execution:**
1. Delegates to `context.stepExecutor.executeSequence(steps, context)`
2. Returns null (orchestration step)

**Kotlin Example:**
```kotlin
data class Sequence(
    override val stepId: String,
    val steps: List<Step>
) : OrchestrationTask {
    override fun execute(context: ExecutionContext): Any? {
        context.stepExecutor.executeSequence(steps, context)
        return null
    }
}
```

---

### If (Orchestration)

Conditional execution based on expression evaluation.

**Properties:**
- `stepId: String` - Step identifier
- `condition: String` - Condition expression (simple boolean or parameter reference)
- `thenSteps: List<Step>` - Steps to execute if condition is true
- `elseSteps: List<Step>` - Steps to execute if condition is false (optional)

**Execution:**
1. Evaluate condition expression
2. Execute `thenSteps` if true, `elseSteps` if false
3. Returns null (orchestration step)

**Kotlin Example:**
```kotlin
data class If(
    override val stepId: String,
    val condition: String,
    val thenSteps: List<Step>,
    val elseSteps: List<Step> = emptyList()
) : OrchestrationTask {
    override fun execute(context: ExecutionContext): Any? {
        val result = evaluateCondition(condition, context)
        val stepsToExecute = if (result) thenSteps else elseSteps
        context.stepExecutor.executeSequence(stepsToExecute, context)
        return null
    }

    private fun evaluateCondition(condition: String, context: ExecutionContext): Boolean {
        // Simple evaluation logic (v1)
        return when {
            condition == "true" -> true
            condition == "false" -> false
            condition.startsWith("params.") -> {
                val paramName = condition.substringAfter("params.")
                context.getParameter(paramName) as? Boolean ?: false
            }
            else -> false
        }
    }
}
```

---

### LogTask (Work Task)

Logs a message to stdout/logger.

**Properties:**
- `stepId: String` - Step identifier
- `message: String` - Message to log (can include parameter references)

**Execution:**
1. Resolve message template with parameter values
2. Log message
3. Returns null

**Kotlin Example:**
```kotlin
data class LogTask(
    override val stepId: String,
    val message: String
) : Step {
    override fun execute(context: ExecutionContext): Any? {
        val resolvedMessage = resolveMessage(message, context)
        println(resolvedMessage)  // Or use logger
        return null
    }

    private fun resolveMessage(template: String, context: ExecutionContext): String {
        // Simple template resolution: "Hello {userName}"
        var result = template
        context.inputParameters.forEach { (key, value) ->
            result = result.replace("{$key}", value.toString())
        }
        return result
    }
}
```

---

### WorkTask (Work Task)

Executes arbitrary work (placeholder in v1).

**Properties:**
- `stepId: String` - Step identifier
- `workType: String` - Type of work to perform

**Execution:**
1. Perform work based on workType
2. Return work result

**Kotlin Example:**
```kotlin
data class WorkTask(
    override val stepId: String,
    val workType: String
) : Step {
    override fun execute(context: ExecutionContext): Any? {
        // Placeholder implementation
        return "Work completed: $workType"
    }
}
```

---

## Value Objects and Enums

### ExecutionStatus (Enum)

Represents the current state of a workflow execution.

**Values:**
- `PENDING` - Created but not yet started
- `RUNNING` - Execution in progress
- `COMPLETED` - All steps completed successfully
- `FAILED` - Execution failed due to step error
- `CANCELLED` - Cancelled by user (future enhancement)

**Methods:**
```kotlin
fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, CANCELLED)
```

---

### StepStatus (Enum)

Represents the outcome of a single step execution.

**Values:**
- `PENDING` - Step not yet executed
- `RUNNING` - Step currently executing (transient, not persisted in v1)
- `COMPLETED` - Step completed successfully
- `FAILED` - Step failed with error
- `SKIPPED` - Not executed due to earlier failure (fail-fast strategy)

**Methods:**
```kotlin
fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, SKIPPED)
```

---

## Database Schema

### workflow_revisions Table

```sql
CREATE TABLE workflow_revisions (
    namespace TEXT NOT NULL,
    workflow_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    parameters JSONB NOT NULL,  -- Array of ParameterSchema
    steps JSONB NOT NULL,       -- Parsed step tree
    steps_yaml TEXT NOT NULL,   -- Original YAML definition
    active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (namespace, workflow_id, version)
);

CREATE INDEX idx_workflow_revisions_active
    ON workflow_revisions(namespace, workflow_id, active);
```

---

### workflow_executions Table

```sql
CREATE TABLE workflow_executions (
    execution_id TEXT PRIMARY KEY,  -- NanoID (21 chars)
    namespace TEXT NOT NULL,
    workflow_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    input_parameters JSONB NOT NULL,
    status TEXT NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (namespace, workflow_id, version)
        REFERENCES workflow_revisions(namespace, workflow_id, version)
);

CREATE INDEX idx_workflow_executions_workflow
    ON workflow_executions(namespace, workflow_id, version, started_at DESC);
```

---

### execution_step_results Table

```sql
CREATE TABLE execution_step_results (
    result_id TEXT PRIMARY KEY,  -- NanoID
    execution_id TEXT NOT NULL,
    step_index INTEGER NOT NULL,
    step_id TEXT NOT NULL,
    step_type TEXT NOT NULL,
    status TEXT NOT NULL,
    input_data JSONB NOT NULL,
    output_data JSONB,
    error_message TEXT,
    error_details JSONB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    FOREIGN KEY (execution_id) REFERENCES workflow_executions(execution_id),
    UNIQUE (execution_id, step_index)
);

CREATE INDEX idx_execution_step_results_execution
    ON execution_step_results(execution_id, step_index);
```

---

## Validation Rules

### Parameter Validation

When executing a workflow, input parameters are validated:

1. **Type Checking**: Each parameter value must match its declared type
2. **Required Parameters**: All required parameters must be provided
3. **No Extra Parameters**: Cannot provide parameters not defined in schema
4. **Default Values**: Missing optional parameters use default values

**Example Validation:**
```kotlin
Workflow parameters: [
    ParameterSchema("userName", STRING, required=true),
    ParameterSchema("age", INTEGER, required=false, defaultValue=0)
]

Valid input: {"userName": "Alice", "age": 30}
Valid input: {"userName": "Bob"}  // age uses default
Invalid: {"userName": "Alice", "invalid": "value"}  // Extra parameter
Invalid: {"age": 30}  // Missing required userName
Invalid: {"userName": 123}  // Wrong type for userName
```

---

### Execution Validation

**Workflow Revision Must Exist:**
- Cannot execute non-existent workflow
- Returns 404 if revision not found

**Parameters Must Be Valid:**
- Validated before execution starts
- Returns 400 with validation details if invalid

**State Constraints:**
- Execution ID must be unique
- Step indexes must be contiguous starting from 0
- Status transitions must follow state machine

---

## Model Diagrams

### Entity Relationship Diagram

```
WorkflowRevision (1) ──< (N) WorkflowExecution
       │
       └─ contains ─> Step (tree structure)
       └─ defines ──> ParameterSchema (list)

WorkflowExecution (1) ──< (N) ExecutionStepResult
       │
       └─ references ─> WorkflowRevisionID

ExecutionStepResult
       └─ may contain ─> ErrorInfo
```

### Execution Flow Diagram

```
WorkflowRevision
    ├─ parameters: [ParameterSchema, ...]
    └─ steps: Sequence
            ├─ step[0]: LogTask
            ├─ step[1]: If
            │   ├─ thenSteps: [WorkTask]
            │   └─ elseSteps: [LogTask]
            └─ step[2]: WorkTask

WorkflowExecution (executionId="xyz")
    ├─ inputParameters: {"userName": "Alice", "debug": true}
    ├─ status: COMPLETED
    └─ (references WorkflowRevision)

ExecutionStepResults for execution "xyz":
    ├─ [stepIndex=0, stepId="log-start", status=COMPLETED, ...]
    ├─ [stepIndex=1, stepId="if-debug", status=COMPLETED, ...]
    ├─ [stepIndex=2, stepId="work-debug", status=COMPLETED, ...]
    └─ [stepIndex=3, stepId="final-work", status=COMPLETED, ...]
```

---

## Related Documentation

- Architecture: `ARCHITECTURE.md`
- API User Guide: `API_USER_GUIDE.md`
- Developer Guide: `DEVELOPER_GUIDE.md`
- Error Handling: `ERROR_HANDLING.md`

---

If you find inconsistencies or missing details, please open an issue or submit a PR updating `documentation/DATA_MODEL.md`.
