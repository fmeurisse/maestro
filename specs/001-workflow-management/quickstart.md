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

## Start the API Server

```bash
# From repository root
mvn quarkus:dev -pl api

# API will be available at http://localhost:8080
```

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

## Resources

- **OpenAPI Spec**: `specs/001-workflow-management/contracts/openapi.yaml`
- **Data Model**: `specs/001-workflow-management/data-model.md`
- **Research**: `specs/001-workflow-management/research.md`
- **CLAUDE.md**: Project architecture and conventions
- **Constitution**: `.specify/memory/constitution.md` (project principles)
