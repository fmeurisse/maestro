# Quickstart: Workflow Management

**Date**: 2025-11-21
**Feature**: Workflow Management
**Purpose**: Get started quickly with workflow creation, versioning, and management

## Prerequisites

- PostgreSQL 18 running locally on port 5432
- Java 21 JDK installed
- Maven 3.8+ installed
- Node.js 20+ and npm 10+ (for UI development)

## Database Setup

```bash
# Create database
createdb maestro

# Run schema migration
psql maestro < plugins/postgres-repository/src/main/resources/schema/workflow_revisions.sql
```

### Understanding the Dual Storage Schema

The workflow_revisions table uses a **dual storage design** for optimal performance and flexibility:

1. **yaml_source (TEXT)**: Preserves the original YAML with formatting and comments
2. **revision_data (JSONB)**: Stores the complete WorkflowRevision as JSON for efficient querying
3. **Computed columns**: Auto-generated from JSONB for indexing (namespace, id, version, active, timestamps)

**Schema Structure**:
```sql
CREATE TABLE workflow_revisions (
    -- Original YAML (preserves formatting/comments)
    yaml_source TEXT NOT NULL,

    -- Parsed structure (efficient querying)
    revision_data JSONB NOT NULL,

    -- Computed columns (auto-generated from JSONB)
    namespace VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'namespace') STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'id') STORED,
    version BIGINT GENERATED ALWAYS AS ((revision_data->>'version')::BIGINT) STORED,
    active BOOLEAN GENERATED ALWAYS AS ((revision_data->>'active')::BOOLEAN) STORED,
    created_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS
        ((revision_data->>'createdAt')::TIMESTAMP WITH TIME ZONE) STORED,
    updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS
        ((revision_data->>'updatedAt')::TIMESTAMP WITH TIME ZONE) STORED,

    PRIMARY KEY (namespace, id, version)
);
```

**Benefits**:
- **YAML Preservation**: Original formatting, comments, and whitespace preserved in TEXT column
- **Query Performance**: JSONB with computed columns enables fast filtering (WHERE active = true)
- **Schema Flexibility**: Add fields to JSONB without ALTER TABLE migrations
- **Consistency**: Computed columns auto-sync with JSONB content

**Example Row**:
```sql
-- yaml_source column (TEXT):
namespace: production
id: payment-processing
name: Payment Processing
description: Handles payment processing
steps:
  - type: LogTask
    message: "Starting"  # This comment is preserved!
  - type: WorkTask
    name: process-payment

-- revision_data column (JSONB):
{
  "namespace": "production",
  "id": "payment-processing",
  "version": 1,
  "name": "Payment Processing",
  "description": "Handles payment processing",
  "steps": [
    {"type": "LogTask", "message": "Starting"},
    {"type": "WorkTask", "name": "process-payment", "parameters": {}}
  ],
  "active": false,
  "createdAt": "2025-11-21T10:30:00Z",
  "updatedAt": "2025-11-21T10:30:00Z"
}

-- Computed columns (auto-extracted):
namespace: production  (indexed)
id: payment-processing  (indexed)
version: 1  (indexed)
active: false  (indexed)
created_at: 2025-11-21 10:30:00+00  (indexed)
updated_at: 2025-11-21 10:30:00+00  (indexed)
```

**Query Examples**:
```sql
-- Find active revisions (uses computed column index)
SELECT yaml_source FROM workflow_revisions
WHERE namespace = 'production'
  AND id = 'payment-processing'
  AND active = true;

-- Query step types (uses JSONB GIN index)
SELECT namespace, id, version FROM workflow_revisions
WHERE revision_data @> '{"steps": [{"type": "LogTask"}]}';

-- Find workflows modified recently (uses computed column index)
SELECT DISTINCT namespace, id FROM workflow_revisions
WHERE updated_at > NOW() - INTERVAL '7 days'
ORDER BY updated_at DESC;
```

## Start the API Server

```bash
# From repository root
mvn quarkus:dev -pl api

# API will be available at http://localhost:8080
# OpenAPI spec: http://localhost:8080/openapi
# Swagger UI: http://localhost:8080/swagger-ui
```

### Explore the API with Swagger UI

Open http://localhost:8080/swagger-ui in your browser to access the interactive API documentation where you can:
- View all available endpoints with request/response schemas
- Try out API calls directly from the browser
- See RFC 7807 JSON Problem error responses
- Download the OpenAPI 3.1 specification

## Start the UI (Development Mode)

```bash
# Navigate to UI module
cd ui/src/main/frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# UI will be available at http://localhost:5173
```

## Quick Tutorial

### 1. Create Your First Workflow

Create a file `payment-workflow.yaml`:
```yaml
namespace: production
id: payment-processing
name: Payment Processing
description: Handles payment processing with approval for high-value transactions
steps:
  type: Sequence
  steps:
    - type: LogTask
      message: "Starting payment processing"
    - type: If
      condition: "amount > 1000"
      then:
        type: WorkTask
        name: "manual-approval"
        parameters:
          approver: "finance-team"
      else:
        type: WorkTask
        name: "auto-process"
        parameters:
          provider: "stripe"
    - type: LogTask
      message: "Payment processing completed"
```

Submit to API:
```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/x-yaml" \
  --data-binary @payment-workflow.yaml
```

Response:
```yaml
namespace: production
id: payment-processing
version: 1
name: Payment Processing
description: Handles payment processing with approval for high-value transactions
active: false
yamlSource: |
  namespace: production
  id: payment-processing
  ...
steps:
  type: Sequence
  steps: [...]
createdAt: "2025-11-21T10:30:00Z"
updatedAt: "2025-11-21T10:30:00Z"
```

### 2. View Workflow Revisions

```bash
# Get all revisions of a workflow
curl http://localhost:8080/api/workflows/production/payment-processing

# Get specific revision
curl http://localhost:8080/api/workflows/production/payment-processing/1
```

### 3. Create a New Revision

Create `payment-workflow-v2.yaml`:
```yaml
name: Payment Processing
description: Updated with retry logic for failed payments
steps:
  type: Sequence
  steps:
    - type: LogTask
      message: "Starting payment processing with retry"
    - type: WorkTask
      name: "process-with-retry"
      parameters:
        provider: "stripe"
        maxRetries: "3"
```

Submit new revision:
```bash
curl -X POST http://localhost:8080/api/workflows/production/payment-processing \
  -H "Content-Type: application/x-yaml" \
  --data-binary @payment-workflow-v2.yaml
```

Response shows `version: 2`.

### 4. Activate a Revision

```bash
# Activate version 1
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/1/activate

# Activate version 2 (both will be active - canary deployment)
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/2/activate

# List active revisions
curl "http://localhost:8080/api/workflows/production/payment-processing?active=true"
```

### 5. Update an Inactive Revision

```bash
# Create update file
cat > update-description.yaml <<EOF
description: "Updated description with more details about retry behavior"
EOF

# Update revision 2 (must be inactive first)
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/2/deactivate

curl -X PUT http://localhost:8080/api/workflows/production/payment-processing/2 \
  -H "Content-Type: application/x-yaml" \
  --data-binary @update-description.yaml
```

### 6. Delete a Revision

```bash
# Deactivate first (if active)
curl -X POST http://localhost:8080/api/workflows/production/payment-processing/1/deactivate

# Delete the revision
curl -X DELETE http://localhost:8080/api/workflows/production/payment-processing/1
```

### 7. Delete Entire Workflow

```bash
curl -X DELETE http://localhost:8080/api/workflows/production/payment-processing
```

Response:
```json
{
  "message": "Workflow deleted successfully",
  "namespace": "production",
  "id": "payment-processing",
  "deletedRevisions": 2
}
```

## Using the React UI

### Create Workflow

1. Open http://localhost:5173
2. Click "New Workflow"
3. Enter namespace and ID
4. Use the YAML editor to define your workflow
5. Click "Create"

The Monaco Editor provides:
- YAML syntax highlighting
- Auto-indentation
- Error detection
- Code folding

### Manage Revisions

1. Select a workflow from the list
2. View all revisions with their version numbers and states
3. Click "Activate" / "Deactivate" to control revision state
4. Click "Edit" to update inactive revisions
5. Click "New Revision" to create a new version

### View Active Revisions

The UI highlights active revisions with a green badge. Multiple revisions can be active simultaneously for canary deployments.

## Common Workflows

### Canary Deployment Pattern

1. **Create workflow** with initial implementation (v1)
2. **Activate v1** for production traffic
3. **Create v2** with new implementation
4. **Activate v2** alongside v1 (now both active)
5. **Monitor v2** behavior with subset of traffic
6. **Deactivate v1** when v2 is stable
7. **Delete v1** if no longer needed

### A/B Testing Pattern

1. **Create two revisions** with different implementations
2. **Activate both revisions** simultaneously
3. **Route traffic** to different versions based on criteria (handled by execution engine)
4. **Analyze results** and choose winning version
5. **Deactivate** losing version
6. **Keep active version** for production

### Development Workflow

1. **Create draft revision** (inactive)
2. **Iteratively update** description and steps
3. **Test locally** with draft revision
4. **Activate** when ready for production
5. **Create new revision** for next iteration

## Error Handling

All API errors follow RFC 7807 JSON Problem format:

```bash
# Example: Try to create duplicate workflow
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/x-yaml" \
  --data-binary @payment-workflow.yaml

# Response (409 Conflict):
{
  "type": "https://maestro.io/problems/workflow-exists",
  "title": "Workflow Already Exists",
  "status": 409,
  "detail": "Workflow with namespace 'production' and id 'payment-processing' already exists"
}
```

Common error types:
- `workflow-not-found` (404): Revision doesn't exist
- `workflow-exists` (409): Duplicate namespace + id
- `invalid-yaml` (400): YAML parsing failed
- `invalid-step` (400): Unknown or invalid step type
- `active-revision-conflict` (409): Cannot update/delete active revision
- `validation-error` (400): Field validation failed

## Testing

### Run Unit Tests

```bash
mvn test -pl core
```

### Run Integration Tests

```bash
# Requires PostgreSQL running
mvn test -pl plugins/postgres-repository

# Run API integration tests
mvn test -pl tests/integration
```

### Run UI Component Tests

```bash
cd ui/src/main/frontend
npm run test:unit           # Cypress component tests
```

## Performance Tips

1. **Use active filter**: `?active=true` to reduce response size
2. **Index usage**: PostgreSQL GIN index on `steps_json` for step type queries
3. **Pagination**: For workflows with many revisions, implement pagination
4. **Caching**: Consider caching active revisions (invalidate on activation changes)

## Troubleshooting

### Database Connection Issues

```bash
# Check PostgreSQL is running
pg_isready

# Verify connection in application.properties
cat api/src/main/resources/application.properties
```

### YAML Parsing Errors

Common causes:
- Incorrect indentation (YAML is indent-sensitive)
- Missing required fields (`type`, `steps`, etc.)
- Unknown step types (check spelling: `LogTask`, not `LogStep`)

Validate YAML online: https://www.yamllint.com/

### UI Build Failures

```bash
# Clear node_modules and reinstall
cd ui/src/main/frontend
rm -rf node_modules package-lock.json
npm install
```

## Next Steps

- **Add custom step types**: Extend the Step model with new implementations
- **Implement execution engine**: Use active revisions to execute workflows
- **Add authentication**: Secure API endpoints with OAuth2/JWT
- **Add authorization**: Namespace-based access control
- **Monitoring**: Add metrics for revision activation, creation, deletion
- **Audit logging**: Track all workflow management operations

## API Endpoints Reference

### Workflow Creation
```bash
# POST /api/workflows - Create first revision (v1)
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/x-yaml" \
  --data-binary @workflow.yaml

# Response: 201 Created
# Location: /api/workflows/{namespace}/{id}/{version}
```

### Revision Management
```bash
# POST /api/workflows/{namespace}/{id} - Create new revision
curl -X POST http://localhost:8080/api/workflows/prod/my-workflow \
  -H "Content-Type: application/x-yaml" \
  --data-binary @workflow-v2.yaml

# GET /api/workflows/{namespace}/{id} - List all revisions
curl http://localhost:8080/api/workflows/prod/my-workflow

# GET /api/workflows/{namespace}/{id}?active=true - List active revisions only
curl "http://localhost:8080/api/workflows/prod/my-workflow?active=true"

# GET /api/workflows/{namespace}/{id}/{version} - Get specific revision with YAML
curl http://localhost:8080/api/workflows/prod/my-workflow/1
```

### Activation Control
```bash
# POST /api/workflows/{namespace}/{id}/{version}/activate
curl -X POST http://localhost:8080/api/workflows/prod/my-workflow/1/activate

# POST /api/workflows/{namespace}/{id}/{version}/deactivate
curl -X POST http://localhost:8080/api/workflows/prod/my-workflow/1/deactivate
```

### Updates and Deletion
```bash
# PUT /api/workflows/{namespace}/{id}/{version} - Update inactive revision
curl -X PUT http://localhost:8080/api/workflows/prod/my-workflow/2 \
  -H "Content-Type: application/x-yaml" \
  --data-binary @updated-workflow.yaml

# DELETE /api/workflows/{namespace}/{id}/{version} - Delete revision
curl -X DELETE http://localhost:8080/api/workflows/prod/my-workflow/2

# DELETE /api/workflows/{namespace}/{id} - Delete entire workflow
curl -X DELETE http://localhost:8080/api/workflows/prod/my-workflow
```

## Resources

- **OpenAPI Spec**: `specs/001-workflow-management/contracts/openapi.yaml`
- **Data Model**: `specs/001-workflow-management/data-model.md`
- **Research**: `specs/001-workflow-management/research.md`
- **CLAUDE.md**: Project architecture and conventions
- **Constitution**: `.specify/memory/constitution.md` (project principles)
- **Swagger UI**: http://localhost:8080/swagger-ui (when API is running)
- **OpenAPI JSON**: http://localhost:8080/openapi (when API is running)
