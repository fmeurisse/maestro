# PostgreSQL Plugin - Database Migrations with Liquibase

This module uses [Liquibase](https://www.liquibase.org/) for database schema migrations.

## Configuration

Database connection properties are configured in `src/main/resources/liquibase.properties`. 
These can be overridden using environment variables:

- `DB_URL` - Database JDBC URL (default: `jdbc:postgresql://localhost:5432/maestro`)
- `DB_USERNAME` - Database username (default: `maestro`)
- `DB_PASSWORD` - Database password (default: `maestro`)

## Running Migrations

### Automatic Migration via Quarkus (Recommended for Development)

The API module is configured to automatically run migrations on startup using the Liquibase Quarkus extension:

- Migrations run automatically when the Quarkus application starts
- Configured in `api/src/main/resources/application.properties`
- Uses the same changelog files from this module
- Set `quarkus.liquibase.migrate-at-start=false` to disable automatic migrations (e.g., in production)

### Using Maven

From the `plugins/postgres` directory:

```bash
# Update database schema
mvn liquibase:update

# Check migration status
mvn liquibase:status

# Generate SQL without executing (dry-run)
mvn liquibase:updateSQL

# Rollback last changeSet
mvn liquibase:rollback -Dliquibase.rollbackCount=1

# Rollback to a specific tag
mvn liquibase:rollback -Dliquibase.rollbackTag=tag_name

# Generate rollback SQL without executing (dry-run)
mvn liquibase:rollbackSQL -Dliquibase.rollbackCount=1
```

### Overriding Database Connection

You can override the database connection using environment variables:

```bash
DB_URL=jdbc:postgresql://localhost:5432/mydb \
DB_USERNAME=myuser \
DB_PASSWORD=mypass \
mvn liquibase:update
```

Or by editing `src/main/resources/liquibase.properties` directly.

## Changelog Structure

- **Master changelog**: `src/main/resources/db/changelog/db.changelog-master.xml`
- **Individual changesets**: `src/main/resources/db/changelog/changes/`

Each changeset file follows the naming convention: `V{version}__{description}.sql`

Changesets use Liquibase formatted SQL with `--liquibase formatted sql` header and `--changeset` directives.

## Creating New Migrations

1. Create a new SQL changelog file in `src/main/resources/db/changelog/changes/`:
   - Example: `V002__add_workflow_executions_table.sql`
   - Start with `--liquibase formatted sql` header
   - Use `--changeset author:id` and `--comment: description` directives

2. Add the new changelog to `db.changelog-master.xml`:
   ```xml
   <include file="db/changelog/changes/V002__add_workflow_executions_table.sql"/>
   ```

3. Example SQL changeset format with rollback:
   ```sql
   --liquibase formatted sql
   
   --changeset maestro:V002-001
   --comment: Add workflow_executions table
   CREATE TABLE workflow_executions (
       id UUID PRIMARY KEY,
       workflow_id VARCHAR(100) NOT NULL
   );
   --rollback DROP TABLE workflow_executions;
   ```

4. Run the migration:
   ```bash
   mvn liquibase:update
   ```

## Best Practices

- Each changeset should be idempotent (safe to run multiple times)
- Use meaningful changeset IDs and comments
- **Always include rollback instructions** using `--rollback` directives
- Test migrations on a development database before applying to production
- Test rollbacks on a development database to ensure they work correctly
- Keep changesets small and focused on a single logical change
- Use `preConditions` to make changesets more robust
- For DROP operations in rollbacks, use `IF EXISTS` to avoid errors if the object doesn't exist
