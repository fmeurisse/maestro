# Feature Specification: Workflow Execution with Input Parameters

**Feature Branch**: `002-workflow-execution`
**Created**: 2025-11-25
**Status**: Draft
**Input**: User description: "Workflow execution from api call with input parameters. The input parameters must be defined in workflow revision and are type. In this first version, the workflow is executed in the orchestrator (there is no async workers, future improvment). A state of the execution must be kept in database to provide execution logs to the user in the ui"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Execute Workflow with Valid Parameters (Priority: P1)

A workflow administrator wants to execute a previously defined workflow revision by providing the required input parameters via an API call and receive immediate feedback on execution success or failure.

**Why this priority**: This is the core functionality - the ability to execute a workflow. Without this, no other features provide value. It represents the minimum viable execution capability.

**Independent Test**: Can be fully tested by making a POST request to execute a workflow with valid parameters and verifying the response indicates execution started and completes successfully. Delivers immediate value by making workflows actionable.

**Acceptance Scenarios**:

1. **Given** a workflow revision with defined input parameters (name: "userName", type: "string"), **When** an API call is made with valid parameters matching the definition, **Then** the workflow executes successfully and returns an execution ID
2. **Given** a workflow execution is in progress, **When** the workflow completes all steps, **Then** the execution state is marked as "completed" and all step results are persisted
3. **Given** a completed workflow execution, **When** querying the execution status via API, **Then** the complete execution log showing all steps and their outcomes is returned

---

### User Story 2 - Handle Invalid Input Parameters (Priority: P2)

A workflow administrator submits an API request with invalid or missing input parameters and receives clear validation errors before execution begins.

**Why this priority**: Input validation prevents wasted resources and provides immediate feedback. While important, basic execution (P1) must work first. This enhances reliability and user experience.

**Independent Test**: Can be tested by submitting various invalid parameter combinations (wrong types, missing required fields, extra fields) and verifying appropriate error messages are returned without starting execution.

**Acceptance Scenarios**:

1. **Given** a workflow revision requiring a "count" parameter of type "integer", **When** an API call provides "count" as a string, **Then** the request is rejected with a clear validation error before execution starts
2. **Given** a workflow revision requiring parameters ["name", "age"], **When** an API call omits the "age" parameter, **Then** the request is rejected indicating the missing required parameter
3. **Given** a workflow revision with no parameters defined, **When** an API call includes extra parameters, **Then** the request is rejected indicating unexpected parameters

---

### User Story 3 - Track Execution Progress (Priority: P3)

A workflow administrator monitors the real-time progress of a running workflow execution by querying its current state and viewing completed steps.

**Why this priority**: Progress tracking enhances observability but requires basic execution (P1) and state persistence to work. It's valuable for long-running workflows but not essential for MVP.

**Independent Test**: Can be tested by starting a workflow execution with multiple steps, querying the status while in progress, and verifying the returned state shows which steps are completed vs pending.

**Acceptance Scenarios**:

1. **Given** a workflow execution with 5 sequential steps, **When** querying execution status after step 3 completes, **Then** the response shows steps 1-3 as "completed", step 4 as "running", and step 5 as "pending"
2. **Given** a workflow execution encounters an error on step 3, **When** querying execution status, **Then** the response shows steps 1-2 as "completed", step 3 as "failed" with error details, and remaining steps as "cancelled"

---

### User Story 4 - View Execution History (Priority: P4)

A workflow administrator retrieves historical execution logs for a specific workflow to analyze past runs, troubleshoot issues, or audit workflow behavior.

**Why this priority**: Historical analysis is valuable for debugging and auditing but depends on execution and state persistence. It's an enhancement that can be added after core execution works.

**Independent Test**: Can be tested by executing a workflow multiple times with different parameters, then querying the execution history and verifying all runs are listed with their parameters, status, and timestamps.

**Acceptance Scenarios**:

1. **Given** a workflow has been executed 10 times, **When** requesting execution history for that workflow, **Then** all 10 executions are returned sorted by most recent first
2. **Given** multiple workflow revisions have been executed, **When** filtering execution history by revision ID, **Then** only executions for that specific revision are returned
3. **Given** an execution completed 30 days ago, **When** requesting execution details by execution ID, **Then** the full execution log including all step results is still available

---

### Edge Cases

- What happens when a workflow execution is requested for a non-existent workflow revision?
- How does the system handle parameter type coercion (e.g., numeric string "123" for integer parameter)?
- What happens when the database connection fails during execution state persistence?
- How are long-running executions handled if the orchestrator process restarts mid-execution?
- What happens when a workflow step times out or hangs indefinitely?
- How does the system handle concurrent execution requests for the same workflow revision?
- What happens when workflow execution logs exceed storage capacity limits?
- How are sensitive parameter values (passwords, API keys) handled in execution logs?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow triggering workflow execution via API endpoint by providing workflow revision ID and input parameters
- **FR-002**: System MUST validate that all required input parameters defined in the workflow revision are provided before starting execution
- **FR-003**: System MUST validate that each input parameter matches its declared type (string, integer, boolean, etc.) before starting execution
- **FR-004**: System MUST reject execution requests with parameters not defined in the workflow revision
- **FR-005**: System MUST execute workflows synchronously within the orchestrator process (no asynchronous workers)
- **FR-006**: System MUST persist execution state to the database including: execution ID, workflow revision ID, start time, current status, input parameters
- **FR-007**: System MUST persist each step's execution result including: step ID, status (pending/running/completed/failed), start time, end time, output data, error details
- **FR-008**: System MUST update execution state in real-time as each step progresses
- **FR-009**: System MUST provide an API endpoint to query execution status by execution ID
- **FR-010**: System MUST return complete execution logs including all step results and timing information
- **FR-011**: System MUST assign a unique execution ID to each workflow execution for tracking and retrieval
- **FR-012**: System MUST handle workflow execution errors gracefully and persist error details in execution logs
- **FR-013**: System MUST support querying execution history for a specific workflow revision
- **FR-014**: System MUST persist execution logs durably to ensure they survive system restarts
- **FR-015**: System MUST return appropriate HTTP status codes for execution requests (200 for success, 400 for validation errors, 404 for non-existent workflows, 500 for execution failures)

### Key Entities

- **Workflow Execution**: Represents a single run of a workflow revision. Key attributes include execution ID, workflow revision reference, input parameters, overall status (pending/running/completed/failed/cancelled), start time, end time, created by user
- **Execution Step Result**: Represents the outcome of executing a single step within a workflow. Key attributes include step identifier, step type, status, start time, end time, output data, error message, error stack trace
- **Input Parameter Definition**: Part of workflow revision defining expected inputs. Key attributes include parameter name, parameter type (string/integer/boolean/float/etc.), required flag, default value, description
- **Execution Log**: Complete record of workflow execution including all step results, timing, and state transitions. Used by UI to display execution progress and history

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can execute a workflow with valid parameters and receive an execution ID in under 2 seconds
- **SC-002**: 100% of execution state changes are successfully persisted to the database before the API response is returned
- **SC-003**: Users can query execution status and retrieve complete logs for any execution within 1 second
- **SC-004**: Invalid parameter requests are rejected with clear validation messages within 500 milliseconds, before any execution processing occurs
- **SC-005**: System successfully executes workflows with up to 50 sequential steps without data loss
- **SC-006**: Execution logs remain accessible for at least 90 days after execution completion
- **SC-007**: 95% of workflow executions complete successfully when provided with valid parameters
- **SC-008**: Error messages for failed executions include sufficient detail for users to diagnose and resolve issues

## Assumptions

- Workflow revisions already exist with defined steps and input parameter schemas (from feature 001-workflow-management)
- Authentication and authorization are handled by existing middleware (user context is available)
- Database schema supports storing structured execution data and JSONB for step results
- Maximum workflow execution time is bounded (reasonable timeout exists, estimated at 5-10 minutes for synchronous execution)
- Parameter types supported include: string, integer, float, boolean (complex types like arrays/objects deferred)
- Execution logs are append-only (no modification after creation)
- Concurrent executions of the same workflow revision are allowed
- No workflow scheduling or recurring execution in this version (explicit API trigger only)
- Execution logs display in UI is a separate feature (this feature provides the data API)

## Out of Scope

- Asynchronous workflow execution with background workers (future enhancement)
- Workflow execution cancellation or pause/resume functionality
- Workflow scheduling or cron-based triggers
- Complex input parameter types (arrays, nested objects, file uploads)
- Workflow execution retries or automatic recovery from failures
- Rate limiting or throttling of execution requests
- Workflow execution notifications or webhooks
- Multi-tenant isolation (assumed single tenant or handled at infrastructure level)
- Execution log retention policies and automatic cleanup
- Export of execution logs to external systems
