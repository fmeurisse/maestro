package io.maestro.plugins.postgres

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.maestro.core.WorkflowJsonParser
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.maestro.core.steps.LogTask
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowAlreadyExistsException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.model.*
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jdbi.v3.core.Jdbi
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Instant

/**
 * Integration tests for PostgresWorkflowRevisionRepository using Kotest and Testcontainers.
 *
 * Tests cover:
 * - Save operations (with source)
 * - Update operations (with source, inactive only)
 * - Find operations (with and without source)
 * - Query operations (by workflow ID, active revisions)
 * - Delete operations (single revision, entire workflow)
 * - Activation/deactivation operations
 * - Utility operations (max version, exists, list workflows)
 */
class PostgresWorkflowRevisionRepositoryIntegTest : FeatureSpec({

    // PostgreSQL container using Kotest Testcontainers extension
    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        .withDatabaseName("maestro_test")
        .withUsername("test")
        .withPassword("test")

    // Register the container extension (per test lifecycle)
    extensions(postgres.perSpec())

    // Setup variables
    lateinit var jdbi: Jdbi
    lateinit var jsonParser: WorkflowJsonParser
    lateinit var repository: PostgresWorkflowRevisionRepository

    beforeSpec {
        // Configure JDBI
        jdbi = Jdbi.create(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )

        // Configure Json parser for JSONB serialization
        jsonParser = WorkflowJsonParser()

        // Run Liquibase migrations using CommandScope API
        val connection = DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
        try {
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            
            val commandScope = CommandScope("update")
            commandScope.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG.getName(), "db/changelog/db.changelog-master.xml")
            commandScope.addArgumentValue("database", database)
            commandScope.addArgumentValue("resourceAccessor", ClassLoaderResourceAccessor())
            commandScope.execute()
        } finally {
            connection.close()
        }

        // Create repository instance
        repository = PostgresWorkflowRevisionRepository(jdbi, jsonParser)
    }

    beforeEach {
        // Clean database before each test
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM workflow_revisions").execute()
        }
    }

    // ===== Helper Methods =====

    fun createTestRevision(
        namespace: String = "test-ns",
        id: String = "workflow-1",
        version: Int = 1,
        name: String = "Test Workflow",
        description: String = "Test description",
        active: Boolean = false,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt
    ): WorkflowRevision {
        return WorkflowRevision.validateAndCreate(
            namespace = namespace,
            id = id,
            version = version,
            name = name,
            description = description,
            steps = listOf(LogTask("Test log message")),
            active = active,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createTestRevisionWithSource(
        namespace: String = "test-ns",
        id: String = "workflow-1",
        version: Int = 1,
        name: String = "Test Workflow",
        description: String = "Test description",
        yamlSource: String = "namespace: test-ns\nid: workflow-1\nsteps:\n  type: LogTask\n  message: Test log message",
        active: Boolean = false,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt
    ): WorkflowRevisionWithSource {
        return WorkflowRevisionWithSource.create(
            namespace = namespace,
            id = id,
            version = version,
            name = name,
            description = description,
            yamlSource = yamlSource,
            steps = listOf(LogTask("Test log message")),
            active = active,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // ===== Save Operations =====

    feature("saveWithSource") {
        scenario("should persist new revision successfully") {
            // Given
            val revision = createTestRevisionWithSource()

            // When
            val saved = repository.saveWithSource(revision)

            // Then
            saved.namespace shouldBe revision.namespace
            saved.id shouldBe revision.id
            saved.version shouldBe revision.version
            saved.yamlSource shouldBe revision.yamlSource
        }

        scenario("should throw exception when revision already exists") {
            // Given
            val revision = createTestRevisionWithSource()
            repository.saveWithSource(revision)

            // When/Then
            shouldThrow<WorkflowAlreadyExistsException> {
                repository.saveWithSource(revision)
            }
        }

        scenario("should preserve YAML formatting and comments") {
            // Given
            val yamlWithComments = """
                # This is a comment
                namespace: test-ns
                id: workflow-1
                steps:
                  type: LogTask
                  message: Test message
            """.trimIndent()

            val revision = createTestRevisionWithSource(yamlSource = yamlWithComments)

            // When
            val saved = repository.saveWithSource(revision)

            // Then
            saved.yamlSource shouldBe yamlWithComments
        }
    }

    // ===== Update Operations =====

    feature("updateWithSource") {
        scenario("should update inactive revision successfully") {
            // Given
            val original = createTestRevisionWithSource(description = "Original description")
            repository.saveWithSource(original)

            val updated = createTestRevisionWithSource(
                description = "Updated description",
                yamlSource = "namespace: test-ns\nid: workflow-1\nsteps:\n  type: LogTask\n  message: Updated message"
            )

            // When
            val result = repository.updateWithSource(updated)

            // Then
            result.description shouldBe "Updated description"
            result.yamlSource stringShouldContain "Updated message"
        }

        scenario("should throw exception when updating active revision") {
            // Given
            val revision = createTestRevisionWithSource(active = true)
            repository.saveWithSource(revision)

            val updated = createTestRevisionWithSource(description = "Updated")

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                repository.updateWithSource(updated)
            }
        }

        scenario("should throw exception when revision does not exist") {
            // Given
            val revision = createTestRevisionWithSource()

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                repository.updateWithSource(revision)
            }
        }
    }

    // ===== Find Operations with Source =====

    feature("findByIdWithSource") {
        scenario("should return revision with YAML source") {
            // Given
            val revision = createTestRevisionWithSource()
            repository.saveWithSource(revision)

            // When
            val found = repository.findByIdWithSource(revision.revisionId())

            // Then
            found.shouldNotBeNull()
            found!!.namespace shouldBe revision.namespace
            found.id shouldBe revision.id
            found.version shouldBe revision.version
            found.yamlSource shouldBe revision.yamlSource
        }

        scenario("should return null when revision does not exist") {
            // Given
            val id = WorkflowRevisionID("test-ns", "non-existent", 1)

            // When
            val found = repository.findByIdWithSource(id)

            // Then
            found.shouldBeNull()
        }
    }

    // ===== Find Operations without Source =====

    feature("findById") {
        scenario("should return revision without YAML source") {
            // Given
            val revision = createTestRevisionWithSource()
            repository.saveWithSource(revision)

            // When
            val found = repository.findById(revision.revisionId())

            // Then
            found.shouldNotBeNull()
            found!!.namespace shouldBe revision.namespace
            found.id shouldBe revision.id
            found.version shouldBe revision.version
            found.name shouldBe revision.name
        }

        scenario("should return null when revision does not exist") {
            // Given
            val id = WorkflowRevisionID("test-ns", "non-existent", 1)

            // When
            val found = repository.findById(id)

            // Then
            found.shouldBeNull()
        }
    }

    feature("findByWorkflowId") {
        scenario("should return all revisions sorted by version") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1))
            repository.saveWithSource(createTestRevisionWithSource(version = 3))
            repository.saveWithSource(createTestRevisionWithSource(version = 2))

            // When
            val revisions = repository.findByWorkflowId(workflowId)

            // Then
            revisions shouldHaveSize 3
            revisions[0].version shouldBe 1
            revisions[1].version shouldBe 2
            revisions[2].version shouldBe 3
        }

        scenario("should return empty list when workflow does not exist") {
            // Given
            val workflowId = WorkflowID("test-ns", "non-existent")

            // When
            val revisions = repository.findByWorkflowId(workflowId)

            // Then
            revisions.shouldBeEmpty()
        }
    }

    feature("findActiveRevisions") {
        scenario("should return only active revisions") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1, active = true))
            repository.saveWithSource(createTestRevisionWithSource(version = 2, active = false))
            repository.saveWithSource(createTestRevisionWithSource(version = 3, active = true))

            // When
            val activeRevisions = repository.findActiveRevisions(workflowId)

            // Then
            activeRevisions shouldHaveSize 2
            activeRevisions.all { it.active } shouldBe true
            activeRevisions[0].version shouldBe 1
            activeRevisions[1].version shouldBe 3
        }

        scenario("should return empty list when no active revisions") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1, active = false))

            // When
            val activeRevisions = repository.findActiveRevisions(workflowId)

            // Then
            activeRevisions.shouldBeEmpty()
        }
    }

    // ===== Utility Operations =====

    feature("findMaxVersion") {
        scenario("should return highest version number") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1))
            repository.saveWithSource(createTestRevisionWithSource(version = 3))
            repository.saveWithSource(createTestRevisionWithSource(version = 2))

            // When
            val maxVersion = repository.findMaxVersion(workflowId)

            // Then
            maxVersion shouldBe 3
        }

        scenario("should return null when workflow does not exist") {
            // Given
            val workflowId = WorkflowID("test-ns", "non-existent")

            // When
            val maxVersion = repository.findMaxVersion(workflowId)

            // Then
            maxVersion.shouldBeNull()
        }
    }

    feature("exists") {
        scenario("should return true when workflow has at least one revision") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource())

            // When
            val exists = repository.exists(workflowId)

            // Then
            exists shouldBe true
        }

        scenario("should return false when workflow does not exist") {
            // Given
            val workflowId = WorkflowID("test-ns", "non-existent")

            // When
            val exists = repository.exists(workflowId)

            // Then
            exists shouldBe false
        }
    }

    feature("listWorkflows") {
        scenario("should return all unique workflows in namespace") {
            // Given
            repository.saveWithSource(createTestRevisionWithSource(namespace = "test-ns", id = "workflow-1", version = 1))
            repository.saveWithSource(createTestRevisionWithSource(namespace = "test-ns", id = "workflow-1", version = 2))
            repository.saveWithSource(createTestRevisionWithSource(namespace = "test-ns", id = "workflow-2", version = 1))
            repository.saveWithSource(createTestRevisionWithSource(namespace = "other-ns", id = "workflow-3", version = 1))

            // When
            val workflows = repository.listWorkflows("test-ns")

            // Then
            workflows shouldHaveSize 2
            workflows.any { it.id == "workflow-1" } shouldBe true
            workflows.any { it.id == "workflow-2" } shouldBe true
            workflows.any { it.id == "workflow-3" } shouldBe false
        }

        scenario("should return empty list when namespace has no workflows") {
            // When
            val workflows = repository.listWorkflows("empty-ns")

            // Then
            workflows.shouldBeEmpty()
        }
    }

    // ===== Delete Operations =====

    feature("deleteById") {
        scenario("should delete inactive revision successfully") {
            // Given
            val revision = createTestRevisionWithSource(active = false)
            repository.saveWithSource(revision)

            // When
            repository.deleteById(revision.revisionId())

            // Then
            val found = repository.findById(revision.revisionId())
            found.shouldBeNull()
        }

        scenario("should throw exception when deleting active revision") {
            // Given
            val revision = createTestRevisionWithSource(active = true)
            repository.saveWithSource(revision)

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                repository.deleteById(revision.revisionId())
            }
        }

        scenario("should throw exception when revision does not exist") {
            // Given
            val id = WorkflowRevisionID("test-ns", "non-existent", 1)

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                repository.deleteById(id)
            }
        }
    }

    feature("deleteByWorkflowId") {
        scenario("should delete all revisions of a workflow") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1, active = false))
            repository.saveWithSource(createTestRevisionWithSource(version = 2, active = false))
            repository.saveWithSource(createTestRevisionWithSource(version = 3, active = false))

            // When
            val deletedCount = repository.deleteByWorkflowId(workflowId)

            // Then
            deletedCount shouldBe 3
            val revisions = repository.findByWorkflowId(workflowId)
            revisions.shouldBeEmpty()
        }

        scenario("should delete active and inactive revisions") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1, active = true))
            repository.saveWithSource(createTestRevisionWithSource(version = 2, active = false))

            // When
            val deletedCount = repository.deleteByWorkflowId(workflowId)

            // Then
            deletedCount shouldBe 2
        }

        scenario("should return 0 when workflow does not exist") {
            // Given
            val workflowId = WorkflowID("test-ns", "non-existent")

            // When
            val deletedCount = repository.deleteByWorkflowId(workflowId)

            // Then
            deletedCount shouldBe 0
        }
    }

    // ===== Activation Operations =====

    feature("activate") {
        scenario("should set active flag to true") {
            // Given
            val revision = createTestRevisionWithSource(active = false)
            repository.saveWithSource(revision)

            // When
            val existing = repository.findByIdWithSource(revision.revisionId())!!
            val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, Instant.now())
            val activated = repository.activateWithSource(revision.revisionId(), updatedYaml)

            // Then
            activated.active shouldBe true

            // Verify persistence
            val found = repository.findById(revision.revisionId())
            found!!.active shouldBe true
        }

        scenario("should update updatedAt timestamp") {
            // Given
            val now = Instant.now()
            val revision = createTestRevisionWithSource(active = false, createdAt = now, updatedAt = now)
            repository.saveWithSource(revision)

            // Wait a bit to ensure timestamp difference
            Thread.sleep(10)

            // When
            val existing = repository.findByIdWithSource(revision.revisionId())!!
            val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, Instant.now())
            val activated = repository.activateWithSource(revision.revisionId(), updatedYaml)

            // Then
            activated.updatedAt.shouldNotBeNull().isAfter(now) shouldBe true
        }

        scenario("should throw exception when revision does not exist") {
            // Given
            val id = WorkflowRevisionID("test-ns", "non-existent", 1)

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                repository.activateWithSource(id, "namespace: test-ns\nid: non-existent\nversion: 1")
            }
        }

        scenario("should allow multiple active revisions for same workflow") {
            // Given
            val workflowId = WorkflowID("test-ns", "workflow-1")
            repository.saveWithSource(createTestRevisionWithSource(version = 1, active = false))
            repository.saveWithSource(createTestRevisionWithSource(version = 2, active = false))

            // When
            val rev1 = repository.findByIdWithSource(WorkflowRevisionID("test-ns", "workflow-1", 1))!!
            val rev2 = repository.findByIdWithSource(WorkflowRevisionID("test-ns", "workflow-1", 2))!!
            repository.activateWithSource(WorkflowRevisionID("test-ns", "workflow-1", 1), WorkflowYamlMetadataUpdater.updateTimestamp(rev1.yamlSource, Instant.now()))
            repository.activateWithSource(WorkflowRevisionID("test-ns", "workflow-1", 2), WorkflowYamlMetadataUpdater.updateTimestamp(rev2.yamlSource, Instant.now()))

            // Then
            val activeRevisions = repository.findActiveRevisions(workflowId)
            activeRevisions shouldHaveSize 2
        }
    }

    // ===== Deactivation Operations =====

    feature("deactivate") {
        scenario("should set active flag to false") {
            // Given
            val revision = createTestRevisionWithSource(active = true)
            repository.saveWithSource(revision)

            // When
            val existing = repository.findByIdWithSource(revision.revisionId())!!
            val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, Instant.now())
            val deactivated = repository.deactivateWithSource(revision.revisionId(), updatedYaml)

            // Then
            deactivated.active shouldBe false

            // Verify persistence
            val found = repository.findById(revision.revisionId())
            found!!.active shouldBe false
        }

        scenario("should update updatedAt timestamp") {
            // Given
            val now = Instant.now()
            val revision = createTestRevisionWithSource(active = true, createdAt = now, updatedAt = now)
            repository.saveWithSource(revision)

            // Wait a bit to ensure timestamp difference
            Thread.sleep(10)

            // When
            val existing = repository.findByIdWithSource(revision.revisionId())!!
            val updatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(existing.yamlSource, Instant.now())
            val deactivated = repository.deactivateWithSource(revision.revisionId(), updatedYaml)

            // Then
            deactivated.updatedAt.shouldNotBeNull().isAfter(now) shouldBe true
        }

        scenario("should throw exception when revision does not exist") {
            // Given
            val id = WorkflowRevisionID("test-ns", "non-existent", 1)

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                repository.deactivateWithSource(id, "namespace: test-ns\nid: non-existent\nversion: 1")
            }
        }
    }

    // ===== Edge Cases and Constraints =====

    feature("constraints and edge cases") {
        scenario("should enforce unique constraint on namespace-id-version") {
            // Given
            val revision = createTestRevisionWithSource()
            repository.saveWithSource(revision)

            // When/Then
            shouldThrow<WorkflowAlreadyExistsException> {
                repository.saveWithSource(revision)
            }
        }

        scenario("should allow same id in different namespaces") {
            // Given
            val revision1 = createTestRevisionWithSource(namespace = "ns1", id = "workflow-1")
            val revision2 = createTestRevisionWithSource(namespace = "ns2", id = "workflow-1")

            // When
            repository.saveWithSource(revision1)
            repository.saveWithSource(revision2)

            // Then
            val found1 = repository.findById(revision1.revisionId())
            val found2 = repository.findById(revision2.revisionId())
            found1.shouldNotBeNull()
            found2.shouldNotBeNull()
        }

        scenario("should handle large YAML source efficiently") {
            // Given
            val largeYaml = "namespace: test-ns\nid: workflow-1\nsteps:\n" +
                    "  type: Sequence\n" +
                    "  steps:\n" +
                    (1..100).joinToString("\n") { "    - type: LogTask\n      message: Step $it" }

            val revision = createTestRevisionWithSource(yamlSource = largeYaml)

            // When
            val saved = repository.saveWithSource(revision)

            // Then
            saved.yamlSource shouldBe largeYaml
        }

        scenario("should preserve timestamps across operations") {
            // Given
            val now = Instant.parse("2025-11-21T10:00:00Z")
            val revision = createTestRevisionWithSource(createdAt = now, updatedAt = now)
            repository.saveWithSource(revision)

            // When
            val found = repository.findById(revision.revisionId())

            // Then
            found!!.createdAt shouldBe now
            found.updatedAt shouldBe now
        }
    }
})
