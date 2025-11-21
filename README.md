1. Implement the Repository - Create a PostgreSQL plugin module with:
    - PostgreSQL 18 database schema with JSONB storage
    - PostgresWorkflowRevisionRepository implementation
    - JDBI configuration
2. Create an In-Memory Repository (for testing):
   - Simple InMemoryWorkflowRevisionRepository using a Map
   - Mark it as @ApplicationScoped for CDI
3. Write Tests:
   - Unit tests for use case
   - Unit tests for validation
   - Integration tests for API endpoint