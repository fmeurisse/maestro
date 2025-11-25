 # Maestro API User Guide

 Last updated: 2025-11-25

 This document describes the public REST API exposed by the Maestro API module. It is intended for API consumers integrating with Maestro to manage workflows and their revisions.

 - Base URL: `http://localhost:8080`
 - Base path: `/api/workflows`
 - Media types:
   - Requests: `application/yaml` for workflow definitions
   - Responses: `application/yaml` for workflow resources, `application/problem+json` for errors
 - Authentication: Not required in the current development version (subject to change)

 ---

 ## Concepts

 - Workflow: Identified by `namespace` and `id`. A workflow can have multiple revisions.
 - Revision: An immutable version of a workflow (`version` is an integer starting at 1).
 - Active flag: Revisions can be activated or deactivated. Multiple revisions of the same workflow may be active at the same time (multi-active supported).
 - Optimistic locking: Certain mutating operations require the `X-Current-Updated-At` header containing the server-side `updatedAt` value of the target revision.

 ---

 ## Error Handling

 Errors follow RFC 7807 Problem Details with media type `application/problem+json`.

 Example (404 Not Found):
 ```json
 {
   "type": "https://maestro/errors/workflow-revision-not-found",
   "title": "Workflow revision not found",
   "status": 404,
   "detail": "Revision 1 for test-ns/non-existent not found"
 }
 ```

 Common status codes:
 - 200 OK: Successful retrieval or state change
 - 201 Created: Successful creation of a new workflow or revision
 - 204 No Content: Successful deletion with no response body
 - 400 Bad Request: Missing required headers or invalid input
 - 404 Not Found: Resource not found
 - 409 Conflict: Operation violates state constraints (e.g., deleting an active revision)

 ---

 ## Workflow Definition Format (YAML)

 Requests that create or update workflow revisions must send YAML. Minimal example:

 ```yaml
 namespace: my-namespace
 id: my-workflow
 name: Example Workflow
 description: An example
 steps:
   - type: LogTask
     message: "Hello world"
 ```

 Server responses for workflow revisions are returned as YAML and include server-managed fields (e.g., `version`, `active`, `updatedAt`).

 ---

 ## Endpoints

 All endpoints below are relative to the base path `/api/workflows`.

 ### 1) Create first workflow revision
 - Method: `POST`
 - Path: `/api/workflows`
 - Request Content-Type: `application/yaml`
 - Response: `201 Created`, body is the created revision in YAML

 Example:
 ```bash
 curl -i \
   -H 'Content-Type: application/yaml' \
   --data-binary @workflow.yaml \
   http://localhost:8080/api/workflows
 ```

 Notes:
 - Creates version `1` of the workflow referenced by `namespace` and `id` from the YAML body.
 - Newly created revisions are inactive by default.

 ### 2) Create a new revision for an existing workflow
 - Method: `POST`
 - Path: `/{namespace}/{id}`
 - Request Content-Type: `application/yaml`
 - Response: `201 Created`, body is the created revision in YAML

 Example:
 ```bash
 curl -i \
   -H 'Content-Type: application/yaml' \
   --data-binary @workflow-v2.yaml \
   http://localhost:8080/api/workflows/my-namespace/my-workflow
 ```

 ### 3) Get a specific revision
 - Method: `GET`
 - Path: `/{namespace}/{id}/{version}`
 - Response: `200 OK`, YAML body of the revision

 Example:
 ```bash
 curl -s -H 'Accept: application/yaml' \
   http://localhost:8080/api/workflows/my-namespace/my-workflow/1
 ```

 ### 4) Activate a revision
 - Method: `POST`
 - Path: `/{namespace}/{id}/{version}/activate`
 - Headers:
   - `X-Current-Updated-At: <value>` (required)
 - Response: `200 OK`, YAML body of the activated revision

 Example:
 ```bash
 UPDATED_AT=$(curl -s -H 'Accept: application/yaml' \
   http://localhost:8080/api/workflows/my-namespace/my-workflow/1 | \
   awk '/updatedAt:/ {print $2}')

 curl -i -X POST \
   -H "X-Current-Updated-At: $UPDATED_AT" \
   http://localhost:8080/api/workflows/my-namespace/my-workflow/1/activate
 ```

 Notes:
 - Multiple revisions of the same workflow may be active concurrently.
 - Missing `X-Current-Updated-At` leads to `400 Bad Request`.
 - Non-existent revision yields `404 Not Found` with `application/problem+json` body.

 ### 5) Deactivate a revision
 - Method: `POST`
 - Path: `/{namespace}/{id}/{version}/deactivate`
 - Headers:
   - `X-Current-Updated-At: <value>` (required)
 - Response: `200 OK`, YAML body of the deactivated revision

 Notes:
 - Missing header results in `400 Bad Request`.
 - Non-existent revision yields `404 Not Found`.

 ### 6) List revisions with active filter
 - Method: `GET`
 - Path: `/{namespace}/{id}`
 - Query parameters:
   - `active=true|false` (optional) — filter revisions by active status
 - Response:
   - `200 OK` with a list representation
   - `404 Not Found` when filtering for `active=true` and no active revisions exist

 Examples:
 ```bash
 # List only active revisions
 curl -i "http://localhost:8080/api/workflows/ns-1/wf-1?active=true"

 # List only inactive revisions
 curl -i "http://localhost:8080/api/workflows/ns-1/wf-1?active=false"
 ```

 ### 7) Delete a specific revision
 - Method: `DELETE`
 - Path: `/{namespace}/{id}/{version}`
 - Response:
   - `204 No Content` on success
   - `404 Not Found` if the revision does not exist
   - `409 Conflict` if the revision is active (must deactivate first)

 Example:
 ```bash
 curl -i -X DELETE \
   http://localhost:8080/api/workflows/my-namespace/my-workflow/2
 ```

 ### 8) Delete an entire workflow (all revisions)
 - Method: `DELETE`
 - Path: `/{namespace}/{id}`
 - Response:
   - `204 No Content` on success
   - `204 No Content` if the workflow does not exist (idempotent delete)
   - `409 Conflict` if any revisions are active (must deactivate all first)

 Example:
 ```bash
 curl -i -X DELETE \
   http://localhost:8080/api/workflows/my-namespace/my-workflow
 ```

 ---

 ## Usage Flows

 ### Create, activate, and read
 1. Create first revision with `POST /api/workflows` (YAML body) → 201
 2. Read revision with `GET /{ns}/{id}/1` to obtain `updatedAt` or use the result of step 1
 3. Activate with `POST /{ns}/{id}/1/activate` and header `X-Current-Updated-At`
 4. Read back or list with `GET /{ns}/{id}?active=true`

 ### Create multiple revisions and handle deletion
 1. Create v1 with `POST /api/workflows`
 2. Create v2+ with `POST /{ns}/{id}`
 3. Delete an inactive revision with `DELETE /{ns}/{id}/{version}` → 204
 4. If active, `POST /{ns}/{id}/{version}/deactivate` first, then delete
 5. Delete entire workflow with `DELETE /{ns}/{id}` (ensure no active revisions)

 ---

 ## Headers Summary
 - `Content-Type: application/yaml` — for POST bodies
 - `Accept: application/yaml` — to request YAML responses for workflow resources
 - `X-Current-Updated-At: <timestamp>` — required for activate/deactivate operations

 ---

 ## Versioning & Stability
 - The API is under active development; endpoints and formats may change.
 - No explicit API version prefix is currently used beyond the base path.

 ---

 ## Changelog
 - 2025-11-25: Initial public documentation covering create/get/activate/deactivate/list/delete endpoints with optimistic locking and error handling.

 If you find inconsistencies or missing details, please open an issue or a PR updating `documentation/API_USER_GUIDE.md`.