# Data Model: Workflow Management

**Date**: 2025-11-21 (Updated: 2025-11-21)
**Feature**: Workflow Management
**Purpose**: Define domain entities, value objects, and data structures

## Domain Entities

### WorkflowRevision

**Purpose**: Core domain entity representing a workflow revision WITHOUT the original YAML source. Used for most operations where YAML source is not needed (queries, activation, execution).

**Module**: `model`

**Design Rationale**: Separating YAML source from core entity reduces memory overhead and network transfer for operations that don't need it (e.g., listing workflows, checking active state, execution planning). The `yamlSource` field can be large (up to 1MB) and is only needed for specific use cases like editing or viewing the original definition.

**Kotlin Definition**:
```kotlin
package io.maestro.model.workflow

import io.maestro.model.steps.Step
import java.time.Instant

/**
 * Core workflow revision entity WITHOUT YAML source.
 * Use this for most operations: queries, activation, execution planning.
 *
 * @property namespace Logical isolation boundary (e.g., "production", "staging")
 * @property id Workflow identifier within namespace
 * @property version Sequential version number (1, 2, 3...)
 * @property name Human-readable workflow name
 * @property description Workflow purpose and behavior description (max 1000 chars)
 * @property steps List of steps to execute sequentially
 * @property active Whether this revision is active for execution
 * @property createdAt UTC timestamp when revision was created (immutable)
 * @property updatedAt UTC timestamp of last modification (activation, deactivation, update)
 */
data class WorkflowRevision(
    val namespace: String,
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val steps: List<Step>,
    val active: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        private val NAMESPACE_REGEX = Regex("^[a-zA-Z0-9_-]+$")
        private val ID_REGEX = Regex("^[a-zA-Z0-9_-]+$")
        private const val MAX_DESCRIPTION_LENGTH = 1000

        /**
         * Factory method with domain validation that throws domain exceptions.
         * Use this instead of constructor to get proper domain exception handling.
         */
        @Throws(ValidationException::class)
        fun create(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevision {
            validate(namespace, id, version, name, description, steps)
            return WorkflowRevision(
                namespace, id, version, name, description,
                steps, active, createdAt, updatedAt
            )
        }

        private fun validate(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            steps: List<Step>
        ) {
            if (namespace.isBlank()) {
                throw ValidationException("Namespace must not be blank")
            }
            if (!namespace.matches(NAMESPACE_REGEX)) {
                throw ValidationException(
                    "Namespace must contain only alphanumeric characters, hyphens, and underscores"
                )
            }
            if (id.isBlank()) {
                throw ValidationException("ID must not be blank")
            }
            if (!id.matches(ID_REGEX)) {
                throw ValidationException(
                    "ID must contain only alphanumeric characters, hyphens, and underscores"
                )
            }
            if (version <= 0) {
                throw ValidationException("Version must be positive")
            }
            if (name.isBlank()) {
                throw ValidationException("Name must not be blank")
            }
            if (description.length > MAX_DESCRIPTION_LENGTH) {
                throw ValidationException(
                    "Description must not exceed $MAX_DESCRIPTION_LENGTH characters"
                )
            }
            if (steps.isEmpty()) {
                throw ValidationException("Steps list must not be empty")
            }
        }
    }

    /**
     * Create a copy with updated timestamp (for activation/deactivation/update operations)
     */
    fun withUpdatedTimestamp(now: Instant = Instant.now()): WorkflowRevision =
        copy(updatedAt = now)

    /**
     * Activate this revision
     */
    fun activate(now: Instant = Instant.now()): WorkflowRevision =
        copy(active = true, updatedAt = now)

    /**
     * Deactivate this revision
     */
    fun deactivate(now: Instant = Instant.now()): WorkflowRevision =
        copy(active = false, updatedAt = now)
}
```

### WorkflowRevisionWithSource

**Purpose**: Extended entity that includes the original YAML source. Used only when YAML source is explicitly needed (create, update, view original definition).

**Module**: `model`

**Design Rationale**: This composition pattern allows:
- Repository to return WorkflowRevision for most queries (lighter weight)
- Repository to return WorkflowRevisionWithSource only when yamlSource is requested
- API layer to choose which representation to use based on endpoint needs

**Kotlin Definition**:
```kotlin
package io.maestro.model.workflow

/**
 * Workflow revision WITH original YAML source.
 * Use this only when YAML source is explicitly needed: create, update, GET with source.
 *
 * @property revision The core workflow revision entity
 * @property yamlSource Original YAML definition with preserved formatting and comments
 */
data class WorkflowRevisionWithSource(
    val revision: WorkflowRevision,
    val yamlSource: String
) {
    // Convenience accessors that delegate to revision
    val namespace: String get() = revision.namespace
    val id: String get() = revision.id
    val version: Long get() = revision.version
    val name: String get() = revision.name
    val description: String get() = revision.description
    val steps: List<Step> get() = revision.steps
    val active: Boolean get() = revision.active
    val createdAt: Instant get() = revision.createdAt
    val updatedAt: Instant get() = revision.updatedAt

    companion object {
        /**
         * Factory method with domain validation that throws domain exceptions.
         * Validates both revision data and YAML source.
         */
        @Throws(ValidationException::class)
        fun create(
            namespace: String,
            id: String,
            version: Int,
            name: String,
            description: String,
            yamlSource: String,
            steps: List<Step>,
            active: Boolean = false,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = createdAt
        ): WorkflowRevisionWithSource {
            if (yamlSource.isBlank()) {
                throw ValidationException("YAML source must not be blank")
            }
            val revision = WorkflowRevision.create(
                namespace, id, version, name, description,
                steps, active, createdAt, updatedAt
            )
            return WorkflowRevisionWithSource(revision, yamlSource)
        }
    }

    /**
     * Convert to core WorkflowRevision (dropping YAML source)
     */
    fun toRevision(): WorkflowRevision = revision

    /**
     * Create a copy with updated revision and timestamp
     */
    fun withUpdatedTimestamp(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.withUpdatedTimestamp(now))

    /**
     * Activate this revision with source
     */
    fun activate(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.activate(now))

    /**
     * Deactivate this revision with source
     */
    fun deactivate(now: Instant = Instant.now()): WorkflowRevisionWithSource =
        copy(revision = revision.deactivate(now))
}
```

**Usage Examples**:
```kotlin
// Create workflow - needs YAML source
val withSource = WorkflowRevisionWithSource.create(
    namespace = "prod",
    id = "workflow-1",
    version = 1,
    name = "My Workflow",
    description = "Description",
    yamlSource = "namespace: prod\n...",
    steps = listOf(
        LogTask("Starting workflow"),
        WorkTask("process-payment", emptyMap())
    )
)

// List workflows - YAML source not needed
val revisions: List<WorkflowRevision> = repository.findByWorkflowId(workflowId)

// Get specific workflow for editing - needs YAML source
val withSource: WorkflowRevisionWithSource? = repository.findByIdWithSource(revisionId)

// Activate workflow - YAML source not needed
val revision: WorkflowRevision = repository.findById(revisionId)
val activated = revision.activate()
repository.update(activated)
```

**Validation Rules**:
- `namespace`: NOT NULL, NOT BLANK, regex `^[a-zA-Z0-9_-]+$`, max 100 chars
- `id`: NOT NULL, NOT BLANK, regex `^[a-zA-Z0-9_-]+$`, max 100 chars
- `version`: Positive long integer (> 0)
- `name`: NOT NULL, NOT BLANK, max 255 chars
- `description`: NOT NULL, max 1000 chars
- `yamlSource`: NOT NULL, NOT BLANK
- `steps`: NOT NULL, NOT EMPTY, list of valid Step implementations
- `createdAt`: NOT NULL, immutable after creation
- `updatedAt`: NOT NULL, updated on every modification

**Immutability Contract**:
- Fields that CANNOT change after creation: `namespace`, `id`, `version`, `createdAt`
- Fields that CAN change (inactive only): `description`, `yamlSource`, `steps` (add/remove/reorder steps in list)
- Fields that CAN change (any state): `active`, `updatedAt`

**State Transitions**:
```
Created (inactive) ? Active ? Inactive ? [can activate again]
Created (inactive) ? Updated (inactive) ? Active
Active ? [CANNOT update description/yamlSource/steps] ? must deactivate first
```

### WorkflowRevisionID

**Purpose**: Value object that uniquely identifies a workflow revision (composite key).

**Module**: `model`

**Kotlin Definition**:
```kotlin
package io.maestro.model.workflow

/**
 * Composite identifier for a workflow revision.
 * Combination of namespace + id + version uniquely identifies a revision.
 */
data class WorkflowRevisionID(
    val namespace: String,
    val id: String,
    val version: Long
) {
    init {
        require(namespace.isNotBlank()) { "Namespace must not be blank" }
        require(id.isNotBlank()) { "ID must not be blank" }
        require(version > 0) { "Version must be positive" }
    }

    override fun toString(): String = "$namespace/$id/$version"
}
```

**Usage**:
- Repository findById operations
- Delete operations
- Activation/deactivation operations

### WorkflowID

**Purpose**: Value object that identifies a workflow across all its revisions (namespace + id).

**Module**: `model`

**Kotlin Definition**:
```kotlin
package io.maestro.model.workflow

/**
 * Identifies a workflow across all its revisions.
 * Combination of namespace + id.
 */
data class WorkflowID(
    val namespace: String,
    val id: String
) {
    init {
        require(namespace.isNotBlank()) { "Namespace must not be blank" }
        require(id.isNotBlank()) { "ID must not be blank" }
    }

    /**
     * Create a revision ID for a specific version
     */
    fun withVersion(version: Int): WorkflowRevisionID =
        WorkflowRevisionID(namespace, id, version)

    override fun toString(): String = "$namespace/$id"
}
```

**Usage**:
- List all revisions of a workflow
- Delete entire workflow
- Create new revision (find max version first)

## Step Model (Existing)

The Step model already exists in the `model` module. This feature leverages the existing Step hierarchy:

**Base Interface**:
```kotlin
sealed interface Step
```

**Orchestration Steps**:
```kotlin
interface OrchestrationStep : Step

data class Sequence(val steps: List<Step>) : OrchestrationStep
data class If(val condition: String, val then: Step, val else_: Step?) : OrchestrationStep
```

**Task Steps**:
```kotlin
interface Task : Step

data class LogTask(val message: String) : Task
data class WorkTask(val name: String, val parameters: Map<String, String>) : Task
```

**Polymorphic JSON**:
Step polymorphism is configured at runtime via `ObjectMapper.registerSubtypes()` to support plugin step types. NO compile-time annotations on Step interface. See research.md #7 for full implementation details including ServiceLoader pattern.

## Repository Interface

**Purpose**: Abstract persistence operations for workflow revisions.

**Module**: `core`

**Kotlin Definition**:
```kotlin
package io.maestro.core.workflow.repository

import io.maestro.model.workflow.*

/**
 * Repository interface for workflow revision persistence.
 * Provides two sets of methods:
 * - Methods returning WorkflowRevision (without YAML source) - for most operations
 * - Methods returning WorkflowRevisionWithSource (with YAML source) - when explicitly needed
 *
 * Implementations must enforce:
 * - Uniqueness of (namespace, id, version) composite key
 * - Sequential versioning with no gaps
 * - Immutability of namespace, id, version, createdAt
 */
interface IWorkflowRevisionRepository {
    // ===== Methods with YAML source (WorkflowRevisionWithSource) =====

    /**
     * Save a new workflow revision WITH YAML source.
     * Throws exception if (namespace, id, version) already exists.
     */
    fun saveWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource

    /**
     * Update an existing workflow revision WITH YAML source.
     * Throws exception if revision doesn't exist or is active.
     */
    fun updateWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource

    /**
     * Find revision by composite ID WITH YAML source.
     * Returns null if not found.
     * Use this when caller needs the original YAML (e.g., for editing, viewing source).
     */
    fun findByIdWithSource(id: WorkflowRevisionID): WorkflowRevisionWithSource?

    // ===== Methods without YAML source (WorkflowRevision) =====

    /**
     * Find revision by composite ID WITHOUT YAML source.
     * Returns null if not found.
     * Use this for most operations where YAML source is not needed (activation, execution).
     */
    fun findById(id: WorkflowRevisionID): WorkflowRevision?

    /**
     * Find all revisions of a workflow (namespace + id) WITHOUT YAML source.
     * Returns empty list if workflow doesn't exist.
     * Sorted by version ascending.
     * Use this for listing revisions where YAML source is not displayed.
     */
    fun findByWorkflowId(workflowId: WorkflowID): List<WorkflowRevision>

    /**
     * Find all active revisions of a workflow WITHOUT YAML source.
     * Returns empty list if no active revisions.
     * Use this for execution planning, routing decisions.
     */
    fun findActiveRevisions(workflowId: WorkflowID): List<WorkflowRevision>

    // ===== Utility methods (no YAML source needed) =====

    /**
     * Find maximum version number for a workflow.
     * Returns null if workflow doesn't exist.
     */
    fun findMaxVersion(workflowId: WorkflowID): Long?

    /**
     * Check if a workflow exists (has at least one revision).
     */
    fun exists(workflowId: WorkflowID): Boolean

    /**
     * Delete a specific revision.
     * Throws exception if revision is active or doesn't exist.
     */
    fun deleteById(id: WorkflowRevisionID)

    /**
     * Delete all revisions of a workflow.
     * Returns count of deleted revisions.
     */
    fun deleteByWorkflowId(workflowId: WorkflowID): Int

    /**
     * List all workflows in a namespace.
     * Returns list of WorkflowID (unique namespace + id combinations).
     */
    fun listWorkflows(namespace: String): List<WorkflowID>

    /**
     * Activate a workflow revision (updates active flag only).
     * Returns the updated revision WITHOUT YAML source.
     */
    fun activate(id: WorkflowRevisionID): WorkflowRevision

    /**
     * Deactivate a workflow revision (updates active flag only).
     * Returns the updated revision WITHOUT YAML source.
     */
    fun deactivate(id: WorkflowRevisionID): WorkflowRevision
}
```

## PostgreSQL Schema

**Table**: `workflow_revisions`

**Schema Design**: JSONB-only storage with GENERATED ALWAYS computed columns for efficient querying. See research.md #8 for full rationale.

**DDL**:
```sql
CREATE TABLE workflow_revisions (
    -- Primary JSONB storage (single source of truth)
    revision_data JSONB NOT NULL,

    -- Computed columns from JSONB for efficient querying and indexing
    namespace VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'namespace') STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'id') STORED,
    version BIGINT GENERATED ALWAYS AS ((revision_data->>'version')::BIGINT) STORED,
    name VARCHAR(255) GENERATED ALWAYS AS (revision_data->>'name') STORED,
    active BOOLEAN GENERATED ALWAYS AS ((revision_data->>'active')::BOOLEAN) STORED,
    created_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS (
        (revision_data->>'createdAt')::TIMESTAMP WITH TIME ZONE
    ) STORED,
    updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS (
        (revision_data->>'updatedAt')::TIMESTAMP WITH TIME ZONE
    ) STORED,

    -- Constraints on computed columns
    PRIMARY KEY (namespace, id, version),
    CONSTRAINT valid_namespace CHECK (namespace ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT valid_id CHECK (id ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT positive_version CHECK (version > 0)
);

-- Index for finding active revisions (common query: "get active revisions for workflow X")
CREATE INDEX idx_workflow_active
ON workflow_revisions(namespace, id, active)
WHERE active = TRUE;

-- Index for listing workflows in a namespace
CREATE INDEX idx_workflow_namespace
ON workflow_revisions(namespace);

-- Index for timestamp-based queries (audit, history)
CREATE INDEX idx_workflow_created_at
ON workflow_revisions(created_at DESC);

-- GIN index on JSONB for querying step types and nested properties
CREATE INDEX idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);
```

**JSONB Structure** (complete WorkflowRevision as JSON):
```json
{
  "namespace": "production",
  "id": "payment-processing",
  "version": 1,
  "name": "Payment Processing",
  "description": "Handles payment processing workflow",
  "yamlSource": "namespace: production\nid: payment-processing\nsteps:\n  - type: LogTask\n    message: Starting\n  - type: WorkTask\n    name: process-payment\n    parameters: {}",
  "steps": [
    {"type": "LogTask", "message": "Starting"},
    {"type": "WorkTask", "name": "process-payment", "parameters": {}}
  ],
  "active": false,
  "createdAt": "2025-11-21T10:30:00Z",
  "updatedAt": "2025-11-21T10:30:00Z"
}
```

**Column Mapping**:
| Kotlin Field | PostgreSQL Storage | Type | Notes |
|--------------|-------------------|------|-------|
| *entire WorkflowRevision* | revision_data | JSONB | Single source of truth |
| namespace | computed column | VARCHAR(100) | Extracted from JSONB, part of PK |
| id | computed column | VARCHAR(100) | Extracted from JSONB, part of PK |
| version | computed column | BIGINT | Extracted from JSONB, part of PK |
| name | computed column | VARCHAR(255) | Extracted from JSONB for indexing |
| active | computed column | BOOLEAN | Extracted from JSONB for indexing |
| createdAt | computed column | TIMESTAMP | Extracted from JSONB for indexing |
| updatedAt | computed column | TIMESTAMP | Extracted from JSONB for indexing |

**Benefits**:
- **Single source of truth**: JSONB contains complete WorkflowRevision, computed columns auto-sync
- **Schema flexibility**: Add fields to JSONB without ALTER TABLE migrations
- **Query performance**: Indexed computed columns enable fast filtering/sorting
- **Consistency**: Computed columns always match JSONB content (no divergence risk)

## Domain Exceptions

**Module**: `core`

**Kotlin Definitions**:
```kotlin
package io.maestro.core.workflow

import io.maestro.model.workflow.*
import java.net.URI

sealed class WorkflowException(
    message: String,
    cause: Throwable? = null,
    val problemType: URI,
    val status: Int
) : RuntimeException(message, cause)

class WorkflowNotFoundException(id: WorkflowRevisionID) :
    WorkflowException(
        message = "Workflow revision not found: $id",
        problemType = URI.create("https://maestro.io/problems/workflow-not-found"),
        status = 404
    )

class WorkflowAlreadyExistsException(id: WorkflowRevisionID) :
    WorkflowException(
        message = "Workflow revision already exists: $id",
        problemType = URI.create("https://maestro.io/problems/workflow-exists"),
        status = 409
    )

class ActiveRevisionConflictException(id: WorkflowRevisionID, operation: String) :
    WorkflowException(
        message = "Cannot $operation active revision: $id. Deactivate it first.",
        problemType = URI.create("https://maestro.io/problems/active-revision-conflict"),
        status = 409
    )

class InvalidYamlException(message: String, cause: Throwable? = null) :
    WorkflowException(
        message = "Invalid YAML: $message",
        cause = cause,
        problemType = URI.create("https://maestro.io/problems/invalid-yaml"),
        status = 400
    )

class InvalidStepException(message: String) :
    WorkflowException(
        message = "Invalid workflow step: $message",
        problemType = URI.create("https://maestro.io/problems/invalid-step"),
        status = 400
    )

class ValidationException(message: String) :
    WorkflowException(
        message = "Validation failed: $message",
        problemType = URI.create("https://maestro.io/problems/validation-error"),
        status = 400
    )
```

## Data Flow

### Create Workflow (First Revision)
```
User YAML ? Jackson YAML Parser (with runtime-registered step types) ? Step tree
         ? WorkflowRevision.create() (domain validation throws ValidationException)
         ? WorkflowRevision(version=1, active=false)
         ? Jackson ObjectMapper ? complete JSONB
         ? Repository.save() ? PostgreSQL INSERT (revision_data=JSONB, computed columns auto-generated)
```

### Create New Revision
```
User YAML ? Jackson YAML Parser ? Step tree
         ? Repository.findMaxVersion(workflowId) ? maxVersion
         ? WorkflowRevision.create(version=maxVersion+1, active=false)
         ? Jackson ObjectMapper ? JSONB
         ? Repository.save() ? PostgreSQL INSERT
```

### Activate Revision
```
Repository.findById(revisionId) ? PostgreSQL SELECT revision_data
         ? Jackson ObjectMapper ? WorkflowRevision object
         ? revision.activate() ? copy with active=true, updatedAt=now
         ? Jackson ObjectMapper ? updated JSONB
         ? Repository.update() ? PostgreSQL UPDATE revision_data (active computed column auto-updates)
```

### Retrieve Workflow
```
Repository.findById(revisionId) ? PostgreSQL SELECT revision_data
         ? Jackson ObjectMapper ? WorkflowRevision
         ? yamlSource preserved with formatting
         ? Step tree reconstructed via runtime-registered polymorphic types
```

**Key Changes from Original Design**:
1. **JSONB-only storage**: Single revision_data column with computed columns for indexing
2. **Runtime polymorphism**: Step types registered at startup via ServiceLoader (plugin-friendly)
3. **Domain exceptions**: WorkflowRevision.create() factory method uses ValidationException instead of require()

## Validation Rules Summary

| Entity | Field | Validation |
|--------|-------|------------|
| WorkflowRevision | namespace | NOT NULL, NOT BLANK, regex `^[a-zA-Z0-9_-]+$`, max 100 |
| WorkflowRevision | id | NOT NULL, NOT BLANK, regex `^[a-zA-Z0-9_-]+$`, max 100 |
| WorkflowRevision | version | > 0 |
| WorkflowRevision | name | NOT NULL, NOT BLANK, max 255 |
| WorkflowRevision | description | NOT NULL, max 1000 |
| WorkflowRevision | yamlSource | NOT NULL, NOT BLANK |
| WorkflowRevision | steps | NOT NULL, NOT EMPTY, list of valid Steps |
| WorkflowRevision | steps (list) | Must contain at least 1 step |
| Step Tree | Depth | Recommended max 10 levels for nested orchestration (prevent stack overflow) |
| Step Tree | Node Count | Reasonable limit (e.g., 1000 nodes total across all steps per workflow) |

**Immutability Enforcement**:
- `namespace`, `id`, `version`, `createdAt`: NEVER change after creation
- Active revisions: CANNOT update `description`, `yamlSource`, `steps`
- Repository layer: Enforce with `UPDATE` statements that check `active = false`

## Summary

The data model provides:
- **Two-entity design**: WorkflowRevision (lightweight, no YAML source) and WorkflowRevisionWithSource (includes YAML source)
- **Immutable versioning** with factory method pattern (WorkflowRevision.create() and WorkflowRevisionWithSource.create())
- **Dual storage**: PostgreSQL TEXT column for YAML source + JSONB for complete revision data + computed columns
- **Runtime Step polymorphism** via ObjectMapper.registerSubtypes() for plugin extensibility
- **Domain validation** throwing ValidationException (not IllegalArgumentException)
- **Clear validation rules** at entity and database levels
- **Repository abstraction** with methods for both entity types (with/without source)
- **Domain exceptions** mapping to RFC 7807 JSON Problem responses

**Key Design Decisions**:
1. **Two Data Classes**: Separate WorkflowRevision (no yamlSource) from WorkflowRevisionWithSource (with yamlSource) for memory efficiency
2. **PostgreSQL Schema**: yaml_source TEXT column + revision_data JSONB + GENERATED ALWAYS computed columns for indexing
3. **Repository Methods**: Dual API (findById vs findByIdWithSource) to control when YAML source is loaded
4. **Step Registration**: Runtime via ServiceLoader pattern, enabling plugin step types without recompilation
5. **Validation**: Factory method with domain exceptions instead of init block with require()

**Benefits**:
- **Memory Efficiency**: Most operations use WorkflowRevision (lighter weight without large YAML strings)
- **Query Performance**: Computed columns enable fast filtering/sorting, TEXT preserves YAML formatting
- **Flexibility**: Add fields to JSONB without schema migrations, plugins add step types without recompilation
- **Consistency**: Computed columns auto-sync with JSONB, single source of truth per entity

All entities follow constitutional principles: pure domain model in `model` module, repository interfaces in `core` module, infrastructure in `api` and `plugins` modules.
