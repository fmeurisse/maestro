# Feature Specification: Workflow Management

**Feature Branch**: `001-workflow-management`
**Created**: 2025-11-21
**Status**: Draft
**Input**: User description: "Specify the workflow management feature. A first specification has been write in file documentation/specs/workflow-management/01-functional-spec.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Version Workflows (Priority: P1)

As a workflow designer, I want to create new workflows with YAML definitions and manage multiple revisions, so that I can evolve my orchestration logic while maintaining complete version history.

**Why this priority**: This is the foundational capability that enables all workflow management. Without the ability to create and version workflows, no other features can function.

**Independent Test**: Can be fully tested by creating a workflow via API with YAML definition, verifying version 1 is created as inactive, then creating additional revisions and verifying sequential versioning (v2, v3, etc.) without any other features enabled.

**Acceptance Scenarios**:

1. **Given** I provide a YAML workflow definition with unique namespace and ID, **When** I submit it to the create workflow endpoint, **Then** the system creates version 1 as inactive and returns the complete workflow revision with all metadata
2. **Given** an existing workflow with one or more revisions, **When** I create a new revision with updated YAML definition, **Then** the system creates the next sequential version (N+1) as inactive and preserves all existing revisions
3. **Given** I attempt to create a workflow with namespace/ID that already exists, **When** I submit the request, **Then** the system rejects it with a 409 Conflict error
4. **Given** I provide malformed YAML or invalid step types, **When** I submit the workflow, **Then** the system rejects it with a 400 Bad Request error indicating the specific parsing or validation issue

---

### User Story 2 - Activate and Manage Revision State (Priority: P2)

As a workflow operator, I want to activate and deactivate specific workflow revisions, so that I can control which versions are available for execution including supporting parallel active versions for canary deployments and A/B testing.

**Why this priority**: Activation control is essential for safely deploying workflow changes and supporting advanced deployment patterns like gradual rollouts.

**Independent Test**: Can be fully tested by creating inactive workflows (from Story 1), activating them, verifying multiple revisions can be active simultaneously, then deactivating them and verifying state changes persist correctly.

**Acceptance Scenarios**:

1. **Given** an inactive workflow revision, **When** I activate it, **Then** the system marks it as active and other active revisions remain active (supporting multiple active revisions per workflow)
2. **Given** an active workflow revision, **When** I deactivate it, **Then** the system marks it as inactive while preserving it for historical reference
3. **Given** multiple revisions of the same workflow, **When** I activate different versions, **Then** all activated versions remain active simultaneously to support canary deployments and A/B testing
4. **Given** I attempt to activate a non-existent revision, **When** I submit the request, **Then** the system returns a 404 Not Found error

---

### User Story 3 - Update Inactive Revisions (Priority: P3)

As a workflow designer, I want to update inactive workflow revisions before activating them, so that I can refine my definitions iteratively without creating unnecessary versions.

**Why this priority**: This improves the workflow authoring experience by allowing iterative refinement, but workflows can function without it by creating new revisions instead.

**Independent Test**: Can be fully tested by creating an inactive revision, updating its description or YAML steps, verifying changes persist and timestamps update, and verifying active revisions cannot be updated.

**Acceptance Scenarios**:

1. **Given** an inactive workflow revision, **When** I update its description or YAML steps, **Then** the system updates it in place without changing the version number and updates the updatedAt timestamp
2. **Given** an active workflow revision, **When** I attempt to update it, **Then** the system rejects the request with a 409 Conflict error requiring deactivation first
3. **Given** I update only the description field, **When** I submit the partial update, **Then** the system updates only the description and leaves the steps unchanged

---

### User Story 4 - Delete Revisions and Workflows (Priority: P3)

As a workflow administrator, I want to delete individual inactive revisions or entire workflows, so that I can remove obsolete or experimental versions and manage storage.

**Why this priority**: Deletion is important for maintenance but not critical for core workflow functionality. Workflows can accumulate over time and be cleaned up later.

**Independent Test**: Can be fully tested by creating workflows and revisions, deleting individual inactive revisions (verifying active ones cannot be deleted), and deleting entire workflows (verifying all revisions are removed regardless of state).

**Acceptance Scenarios**:

1. **Given** an inactive workflow revision, **When** I delete it, **Then** the system permanently removes it and deleted version numbers are not reused
2. **Given** an active workflow revision, **When** I attempt to delete it, **Then** the system rejects the request with a 409 Conflict error requiring deactivation first
3. **Given** a workflow identified by namespace and ID, **When** I delete the entire workflow, **Then** the system removes all revisions including active ones and returns the count of deleted revisions
4. **Given** I attempt to delete a non-existent revision or workflow, **When** I submit the request, **Then** the system returns a 404 Not Found error

---

### Edge Cases

- What happens when two requests attempt to create a new revision simultaneously? System handles concurrency with one succeeding and the other receiving a 409 Conflict with retry guidance
- What happens when YAML contains deeply nested orchestration steps (10+ levels)? System validates entire tree structure and may enforce maximum nesting depth to prevent stack overflow
- How does the system handle workflows with hundreds or thousands of revisions? Version numbering continues normally (Int type supports large values) with linear query scaling
- What happens when a workflow is deleted while one of its active revisions is executing? Deletion fails; only workflow with no active revisions can be deleted
- How does the system handle concurrent activation requests for different revisions? Both activations succeed; multiple revisions become active simultaneously as designed
- What happens when YAML contains unknown step types or invalid configurations? System rejects with 400 Bad Request error identifying the unknown type or invalid configuration

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept workflow definitions in YAML format and parse them into the internal Step model (Sequence, If, Task, LogTask, etc.)
- **FR-002**: System MUST preserve the original YAML including comments and formatting for later retrieval
- **FR-003**: System MUST store both the original YAML and the parsed JSON representation in the database
- **FR-004**: System MUST validate YAML syntax and reject malformed YAML with clear error messages indicating line/column of parsing errors
- **FR-005**: System MUST validate that all step types in YAML are known and valid (reject unknown step types)
- **FR-006**: System MUST create new workflows with version number 1 and isActive set to false by default
- **FR-007**: System MUST enforce uniqueness of namespace + id combination for version 1 workflows
- **FR-008**: System MUST create new revisions with sequential version numbers (maxVersion + 1) with no gaps
- **FR-009**: System MUST allow multiple revisions of the same workflow (namespace + id) to be active simultaneously
- **FR-010**: System MUST allow updating inactive revisions in place without changing version numbers
- **FR-011**: System MUST prevent updating or deleting active revisions without deactivation first
- **FR-012**: System MUST update the updatedAt timestamp on all modification operations including activation/deactivation
- **FR-013**: System MUST maintain immutability of version numbers, namespace, id, and createdAt fields
- **FR-014**: System MUST permanently delete inactive revisions without reusing version numbers
- **FR-015**: System MUST delete entire workflows by removing all revisions regardless of active state
- **FR-016**: System MUST validate that namespace and id follow naming conventions (alphanumeric, hyphens, underscores, max 100 characters)
- **FR-017**: System MUST validate that description does not exceed 1000 characters
- **FR-018**: System MUST validate nested orchestration steps (Sequence, If) contain valid step implementations
- **FR-019**: System MUST set both createdAt and updatedAt timestamps to current UTC time upon creation
- **FR-020**: System MUST provide RESTful API endpoints for all CRUD operations following standard HTTP conventions

### Key Entities

- **WorkflowRevision**: Represents a specific version of a workflow with namespace, id, version, description, YAML source, parsed steps, active state, and timestamps
- **Step**: Base interface for all executable workflow steps (includes Task, OrchestrationStep)
- **OrchestrationStep**: Interface for steps that orchestrate other steps (Sequence, If, ...)
- **Task**: Interface for concrete work tasks (LogTask, ...)
- **Namespace**: Logical isolation boundary for workflows (supports multi-tenancy and environment separation)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create workflows with YAML definitions and receive confirmation within 500 milliseconds (p95 latency)
- **SC-002**: Users can create new revisions and the system correctly assigns sequential version numbers with 100% accuracy
- **SC-003**: System supports at least 10,000 unique workflows across all namespaces without performance degradation
- **SC-004**: System handles workflows with up to 1,000 revisions per workflow and query operations complete within 200ms (p95)
- **SC-005**: System successfully validates and rejects 100% of malformed YAML with clear error messages identifying the issue
- **SC-006**: System supports at least 100 concurrent workflow management operations without errors
- **SC-007**: Activation and deactivation operations complete within 300ms (p95 latency)
- **SC-008**: Multiple active revisions per workflow function correctly with zero conflicts or race conditions
- **SC-009**: All workflow management operations maintain 99.9% success rate under normal conditions
- **SC-010**: Zero data corruption incidents across all CRUD operations (referential integrity maintained)

## Assumptions

- **ASM-001**: Users understand workflow versioning concepts (revisions, sequential versions, activation states)
- **ASM-002**: A database persistence layer is available for storing workflow revisions and supports transactions
- **ASM-003**: Network latency between API and database is minimal (< 50ms p95)
- **ASM-004**: Workflow definitions are relatively small (YAML < 1MB) for most use cases
- **ASM-005**: Concurrent modifications to the same workflow are rare enough that optimistic locking is acceptable
- **ASM-006**: All timestamps operate in UTC timezone
- **ASM-007**: Deleted revisions do not need to be recoverable (hard delete is acceptable, no soft delete requirement)
- **ASM-008**: Task definition validation occurs at submission time, not at execution time
- **ASM-009**: API clients can handle standard HTTP response codes (200, 201, 400, 404, 409, 500) and JSON error formats
- **ASM-010**: YAML parsing uses industry-standard libraries (Jackson YAML or SnakeYAML) with standard parsing behavior
