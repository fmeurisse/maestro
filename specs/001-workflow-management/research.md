# Research: Workflow Management Implementation

**Date**: 2025-11-21
**Feature**: Workflow Management
**Purpose**: Resolve technical unknowns and establish best practices for implementation

## Research Areas

### 1. Database Access Library: JDBI vs Exposed

**Question**: Should we use JDBI or Exposed for PostgreSQL access in the postgres-repository plugin?

**Decision**: **Use JDBI 3.x**

**Rationale**:
- **Kotlin SQL DSL**: Exposed provides Kotlin-native DSL, but JDBI 3.45+ has excellent Kotlin support via jdbi3-kotlin module
- **SQL Control**: JDBI allows direct SQL control which is better for:
  - Complex JSONB queries (PostgreSQL-specific features)
  - Query optimization and performance tuning
  - Explicit transaction management
- **Lightweight**: JDBI is lighter weight than Exposed's DAO/DSL layers
- **Quarkus Integration**: Quarkus has `quarkus-jdbi` extension for seamless integration
- **Repository Pattern**: JDBI's SQL Object API maps well to repository interfaces
- **Testing**: JDBI works well with H2 or Testcontainers for testing

**Alternatives Considered**:
- **Exposed**: More Kotlin-native, but adds abstraction layer over SQL. For PostgreSQL-specific features (JSONB operators), raw SQL is clearer.
- **jOOQ**: Excellent type-safe SQL, but requires code generation and adds build complexity
- **Direct JDBC**: Too low-level, lots of boilerplate

**Implementation Notes**:
- Use `@RegisterKotlinMapper` for automatic data class mapping
- Use `@SqlQuery` and `@SqlUpdate` annotations for repository methods
- Leverage JDBI's `Handle` for transaction management

### 2. RFC 7807 JSON Problem Library

**Question**: Which Kotlin/Java library should we use for RFC 7807 JSON Problem responses?

**Decision**: **Use Zalando Problem (problem-spring-web or problem library)**

**Rationale**:
- **Industry Standard**: Zalando Problem is the most widely-used RFC 7807 implementation in JVM ecosystem
- **JAX-RS Support**: Works seamlessly with JAX-RS via exception mappers
- **Kotlin Support**: Pure Java library but works well with Kotlin
- **Extensible**: Easy to define custom problem types
- **Jackson Integration**: Integrates with Jackson for JSON serialization

**Alternatives Considered**:
- **Custom Implementation**: Would require significant effort to implement RFC 7807 spec correctly
- **Quarkus built-in**: Quarkus has some problem support but not as comprehensive

**Implementation Notes**:
```kotlin
// Define problem types
object WorkflowProblemTypes {
    val WORKFLOW_NOT_FOUND = URI.create("https://maestro.io/problems/workflow-not-found")
    val WORKFLOW_ALREADY_EXISTS = URI.create("https://maestro.io/problems/workflow-exists")
    val INVALID_YAML = URI.create("https://maestro.io/problems/invalid-yaml")
    val ACTIVE_REVISION_CONFLICT = URI.create("https://maestro.io/problems/active-revision")
}

// Exception mapper
@Provider
class JsonProblemExceptionMapper : ExceptionMapper<DomainException> {
    override fun toResponse(exception: DomainException): Response {
        val problem = Problem.builder()
            .withType(exception.problemType)
            .withTitle(exception.title)
            .withStatus(exception.status)
            .withDetail(exception.message)
            .build()
        return Response.status(exception.status)
            .entity(problem)
            .type("application/problem+json")
            .build()
    }
}
```

### 3. React YAML Editor Component

**Question**: Which React component should we use for YAML editing with syntax highlighting?

**Decision**: **Use Monaco Editor (monaco-editor-react)**

**Rationale**:
- **Industry Standard**: Monaco powers VS Code - battle-tested, feature-rich
- **YAML Support**: Built-in YAML language support with syntax highlighting
- **Features**: Auto-completion, error highlighting, code folding, diff view
- **React Integration**: `@monaco-editor/react` provides clean React wrapper
- **Customizable**: Themes, validation, custom language features
- **Performance**: Optimized for large documents (handles MB-sized YAML)

**Alternatives Considered**:
- **CodeMirror 6**: Lighter weight, good YAML support, but Monaco has better feature set
- **Ace Editor**: Older, less actively maintained than Monaco
- **react-syntax-highlighter**: Read-only, no editing capabilities

**Implementation Notes**:
```tsx
import Editor from '@monaco-editor/react';

function YamlEditor({ value, onChange }) {
  return (
    <Editor
      height="600px"
      language="yaml"
      theme="vs-dark"
      value={value}
      onChange={onChange}
      options={{
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        wordWrap: 'on'
      }}
    />
  );
}
```

Dependencies:
- `@monaco-editor/react` v4.6+
- `monaco-editor` v0.45+ (peer dependency)

### 4. Cypress Component Testing Setup

**Question**: How should we configure Cypress for React component testing in the ui module?

**Decision**: **Use Cypress Component Testing with Vite**

**Rationale**:
- **Cypress 13+**: Built-in component testing support (no need for separate tools)
- **Vite Integration**: Fast HMR, modern build tool for React
- **Isolation**: Test components in isolation without full app
- **Real Browser**: Tests run in actual Chrome/Firefox/Edge
- **Developer Experience**: Same Cypress Test Runner for e2e and component tests

**Configuration**:
```typescript
// cypress.config.ts
import { defineConfig } from 'cypress';

export default defineConfig({
  component: {
    devServer: {
      framework: 'react',
      bundler: 'vite',
    },
    specPattern: 'cypress/component/**/*.cy.{ts,tsx}',
    supportFile: 'cypress/support/component.ts',
  },
});
```

**Dependencies**:
- `cypress` v13.6+
- `vite` v5.0+
- `@vitejs/plugin-react` v4.2+

**Example Test**:
```tsx
// cypress/component/YamlEditor.cy.tsx
import YamlEditor from '../../src/components/YamlEditor';

describe('YamlEditor', () => {
  it('displays YAML content', () => {
    const yaml = 'namespace: test\nid: workflow-1';
    cy.mount(<YamlEditor value={yaml} onChange={cy.stub()} />);
    cy.contains('namespace: test');
  });

  it('calls onChange when content changes', () => {
    const onChange = cy.stub();
    cy.mount(<YamlEditor value="" onChange={onChange} />);
    cy.get('.monaco-editor').type('test: value');
    cy.wrap(onChange).should('have.been.called');
  });
});
```

### 5. Frontend-Maven-Plugin Configuration

**Question**: How should we integrate React build into Maven lifecycle for the ui module?

**Decision**: **Use frontend-maven-plugin with npm**

**Rationale**:
- **Maven Integration**: Seamlessly integrates npm/node into Maven lifecycle
- **Consistent Builds**: CI/CD can use `mvn clean install` for everything
- **Version Management**: Plugin downloads and manages specific Node/npm versions
- **Multi-Module Support**: Works well with Maven reactor builds
- **Production Builds**: Handles dev vs prod builds via Maven profiles

**Configuration**:
```xml
<!-- ui/pom.xml -->
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.0</version>
    <configuration>
        <nodeVersion>v20.11.0</nodeVersion>
        <npmVersion>10.2.4</npmVersion>
        <workingDirectory>src/main/frontend</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals><goal>npm</goal></goals>
        </execution>
        <execution>
            <id>npm-run-build</id>
            <goals><goal>npm</goal></goals>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-run-test</id>
            <goals><goal>npm</goal></goals>
            <phase>test</phase>
            <configuration>
                <arguments>run test:ci</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Build Output**: React build artifacts go to `ui/src/main/frontend/dist` and can be served by Quarkus static resources.

### 6. Step Polymorphic JSON Serialization with Runtime Registration

**Question**: How should Jackson handle polymorphic Step serialization/deserialization when new step types can be added by plugins at runtime?

**Decision**: **Use Jackson PolymorphicTypeValidator with runtime subtype registration**

**Rationale**:
- **Plugin Extensibility**: Plugins can register new Step implementations at runtime without modifying core code
- **Type Safety**: Preserves exact Step subtype during JSON round-trip
- **Jackson Native**: Uses Jackson's built-in polymorphic type handling
- **YAML Compatibility**: Works with both JSON and YAML ObjectMappers
- **No Compile-Time Coupling**: Core model doesn't need to know about plugin step types

**Alternatives Considered**:
- **Static @JsonSubTypes**: Requires recompiling core when adding new steps - not plugin-friendly
- **Custom Deserializer**: More complex, harder to maintain, loses Jackson's built-in features
- **Service Loader Pattern Only**: Doesn't integrate with Jackson serialization

**Implementation**:

```kotlin
// In model module - NO annotations on Step interface
sealed interface Step {
    // Pure domain interface
}

// Core step types still exist but registration happens at runtime
data class Sequence(val steps: List<Step>) : OrchestrationStep
data class If(val condition: String, val then: Step, val else_: Step?) : OrchestrationStep
data class LogTask(val message: String) : Task
data class WorkTask(val name: String, val parameters: Map<String, String>) : Task
```

```kotlin
// In api module - runtime configuration
package io.maestro.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import io.maestro.model.steps.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

@ApplicationScoped
class JacksonConfig {

    @Produces
    @Singleton
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(YAMLFactory())

        // Register core step types at runtime
        mapper.registerSubtypes(
            NamedType(Sequence::class.java, "Sequence"),
            NamedType(If::class.java, "If"),
            NamedType(LogTask::class.java, "LogTask"),
            NamedType(WorkTask::class.java, "WorkTask")
        )

        // Discover and register plugin step types via ServiceLoader
        val stepTypeRegistry = StepTypeRegistry.discover()
        stepTypeRegistry.registeredTypes.forEach { (name, stepClass) ->
            mapper.registerSubtypes(NamedType(stepClass.java, name))
        }

        // Configure polymorphic type handling
        mapper.activateDefaultTyping(
            mapper.polymorphicTypeValidator,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        )

        return mapper
    }
}

// Service registry for plugin step types
interface StepTypeProvider {
    fun provideStepTypes(): Map<String, KClass<out Step>>
}

object StepTypeRegistry {
    fun discover(): StepTypeRegistry {
        val registry = mutableMapOf<String, KClass<out Step>>()

        // Use ServiceLoader to discover plugin step providers
        ServiceLoader.load(StepTypeProvider::class.java).forEach { provider ->
            registry.putAll(provider.provideStepTypes())
        }

        return StepTypeRegistry(registry)
    }

    class StepTypeRegistry(val registeredTypes: Map<String, KClass<out Step>>)
}
```

```kotlin
// Plugin example - registers custom step types
// In plugin module: META-INF/services/io.maestro.api.config.StepTypeProvider
package io.maestro.plugins.customsteps

class CustomStepTypeProvider : StepTypeProvider {
    override fun provideStepTypes(): Map<String, KClass<out Step>> = mapOf(
        "CustomHttpTask" to CustomHttpTask::class,
        "CustomEmailTask" to CustomEmailTask::class
    )
}

data class CustomHttpTask(val url: String, val method: String) : Task
data class CustomEmailTask(val to: String, val subject: String) : Task
```

**Benefits**:
1. **Zero recompilation**: Add new step types without touching core code
2. **Plugin isolation**: Plugins provide their own step implementations
3. **Type safety**: Jackson validates types at runtime
4. **Discoverability**: ServiceLoader pattern is standard Java mechanism
5. **Testing**: Easy to test with mock step type providers

**JSON Example** (same format, but supports plugin types):
```json
{
  "type": "Sequence",
  "steps": [
    { "type": "LogTask", "message": "Starting" },
    { "type": "CustomHttpTask", "url": "https://api.example.com", "method": "POST" },
    { "type": "CustomEmailTask", "to": "admin@example.com", "subject": "Workflow completed" }
  ]
}
```

### 7. PostgreSQL Schema Design: JSONB-Only Storage with Computed Columns

**Question**: How should we structure the PostgreSQL schema to optimize for JSONB storage and querying?

**Decision**: **Dual Storage (TEXT + JSONB): Store entire WorkflowRevision as JSONB with GENERATED ALWAYS computed columns and add a TEXT column for YAML source **

**Rationale**:
- **Single Source of Truth**: JSONB column contains complete workflow revision data
- **Query Performance**: Computed columns enable efficient indexing on frequently-queried fields
- **Schema Flexibility**: Adding new fields only requires updating the JSONB, not schema migrations
- **PostgreSQL Native**: GENERATED ALWAYS columns are computed from JSONB automatically
- **Consistency**: No risk of JSONB and columns diverging (columns are always derived)

**Alternatives Considered**:
- **Pure JSONB with no columns**: Queries require JSONB operators for everything, slower for common filters
- **Traditional columns only**: Loses flexibility, requires migrations for schema changes

**Implementation**:

```sql
CREATE TABLE workflow_revisions (
    -- Dual storage
    yaml_source TEXT NOT NULL,              -- Original YAML with comments/formatting
    revision_data JSONB NOT NULL,

    -- Computed columns from JSONB for efficient querying and indexing
    namespace VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'namespace') STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'id') STORED,
    version BIGINT GENERATED ALWAYS AS ((revision_data->>'version')::BIGINT) STORED,
    name VARCHAR(255) GENERATED ALWAYS AS (revision_data->>'name') STORED,
    active BOOLEAN GENERATED ALWAYS AS ((revision_data->>'active')::BOOLEAN) STORED,
    created_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'createdAt')::TIMESTAMP WITH TIME ZONE) STORED,
    updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'updatedAt')::TIMESTAMP WITH TIME ZONE) STORED,

    -- Constraints on computed columns
    PRIMARY KEY (namespace, id, version),
    CONSTRAINT valid_namespace CHECK (namespace ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT valid_id CHECK (id ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT positive_version CHECK (version > 0)
);

-- Indexes on computed columns for query performance
CREATE INDEX idx_workflow_active
ON workflow_revisions(namespace, id, active)
WHERE active = TRUE;

CREATE INDEX idx_workflow_namespace
ON workflow_revisions(namespace);

CREATE INDEX idx_workflow_created_at
ON workflow_revisions(created_at DESC);

-- GIN index on JSONB for querying step types and nested properties
CREATE INDEX idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);
```

**JSONB Structure**:
```json
{
  "namespace": "production",
  "id": "payment-processing",
  "version": 1,
  "name": "Payment Processing",
  "description": "Handles payment processing workflow",
  "steps": {
    "type": "Sequence",
    "steps": [
      {"type": "LogTask", "message": "Starting"},
      {"type": "WorkTask", "name": "process-payment", "parameters": {}}
    ]
  },
  "active": false,
  "createdAt": "2025-11-21T10:30:00Z",
  "updatedAt": "2025-11-21T10:30:00Z"
}
```

**Benefits**:
1. **Simplicity**: Single JSONB column is authoritative, computed columns auto-sync
2. **Flexibility**: Add fields like `tags`, `metadata` without schema migration
3. **Performance**: Indexed computed columns enable fast filtering/sorting
4. **PostgreSQL Features**: Leverage JSONB operators for complex queries (e.g., `revision_data @> '{"steps": {"type": "Sequence"}}'`)
5. **Atomicity**: Updates to JSONB automatically update all computed columns

**Query Examples**:
```sql
-- Fast query using computed column index
SELECT * FROM workflow_revisions
WHERE namespace = 'production' AND active = TRUE;

-- Deep JSONB query using GIN index
SELECT * FROM workflow_revisions
WHERE revision_data @> '{"steps": {"type": "If"}}';

-- Extract nested data
SELECT namespace, id, version,
       jsonb_path_query(revision_data, '$.steps.steps[*].type')
FROM workflow_revisions;
```

## Summary of Decisions

| Area | Decision | Key Benefit |
|------|----------|-------------|
| Database Access | JDBI 3.x | SQL control for PostgreSQL JSONB, lightweight, Quarkus integration |
| JSON Problem | Zalando Problem | Industry standard RFC 7807 implementation, JAX-RS support |
| YAML Editor | Monaco Editor | VS Code quality, built-in YAML support, feature-rich |
| Schema Design | JSONB-only with computed columns | Single source of truth, schema flexibility, query performance |
| Component Testing | Cypress + Vite | Fast, real browser, modern tooling |
| Maven Frontend | frontend-maven-plugin | Seamless npm/Maven integration, consistent builds |
| JSON Polymorphism | Runtime registration via ServiceLoader | Plugin extensibility, zero recompilation for new step types |

**All unknowns from Technical Context resolved with updated architecture. Ready for Phase 1 design.**
