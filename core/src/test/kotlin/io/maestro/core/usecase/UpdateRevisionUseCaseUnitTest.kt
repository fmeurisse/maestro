package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    feature("Update workflow revision") {

        scenario("should update an inactive revision successfully") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            // Existing inactive revision
            val existingRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Old Name",
                description = "Old description",
                steps = listOf(LogTask("Old message")),
                active = false,
                createdAt = Instant.parse("2024-01-01T10:00:00Z"),
                updatedAt = Instant.parse("2024-01-01T10:00:00Z")
            )

            // New YAML with updated content
            val newYaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Updated Name
                description: Updated description
                steps:
                  - type: LogTask
                    message: "Updated message"
            """.trimIndent()

            // Parsed new content
            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Updated Name",
                description = "Updated description",
                steps = listOf(LogTask("Updated message")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.findById(revisionId) } returns existingRevision
            every { yamlParser.parseRevision(newYaml, true) } returns parsedRevision

            val updatedSlot = slot<WorkflowRevisionWithSource>()
            every { repository.updateWithSource(capture(updatedSlot)) } answers { updatedSlot.captured }

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1, newYaml)

            // Then
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 1

            // Verify the updated revision has correct fields
            val capturedUpdate = updatedSlot.captured
            capturedUpdate.name shouldBe "Updated Name"
            capturedUpdate.description shouldBe "Updated description"
            capturedUpdate.steps.size shouldBe 1
            capturedUpdate.active shouldBe false  // Preserved
            capturedUpdate.createdAt shouldBe Instant.parse("2024-01-01T10:00:00Z")  // Immutable
            capturedUpdate.updatedAt shouldBe fixedInstant  // Updated
            // YAML source should contain updated metadata
            capturedUpdate.yamlSource shouldContain "version: 1"
            capturedUpdate.yamlSource shouldContain "createdAt:"
            capturedUpdate.yamlSource shouldContain "updatedAt:"

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { yamlParser.parseRevision(newYaml, true) }
            verify(exactly = 1) { repository.updateWithSource(any()) }
        }

        scenario("should throw exception when updating active revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            // Existing ACTIVE revision
            val activeRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Active Workflow",
                description = "Active description",
                steps = listOf(LogTask("Active message")),
                active = true,  // Active!
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val newYaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Trying to update
                description: This should fail
                steps:
                  - type: LogTask
                    message: "Should not work"
            """.trimIndent()

            every { repository.findById(revisionId) } returns activeRevision

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                useCase.execute("test-ns", "workflow-1", 1, newYaml)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 0) { yamlParser.parseRevision(any(), any()) }
            verify(exactly = 0) { repository.updateWithSource(any()) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            val newYaml = """
                namespace: test-ns
                id: non-existent
                version: 1
                name: Trying to update
                description: This should fail
                steps:
                  - type: LogTask
                    message: "Should not work"
            """.trimIndent()

            every { repository.findById(revisionId) } returns null

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1, newYaml)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 0) { yamlParser.parseRevision(any(), any()) }
            verify(exactly = 0) { repository.updateWithSource(any()) }
        }

        scenario("should validate namespace match between YAML and path") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val existingRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            // YAML with DIFFERENT namespace
            val newYaml = """
                namespace: different-ns
                id: workflow-1
                version: 1
                name: Test
                description: Test
                steps:
                  - type: LogTask
                    message: "Test"
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "different-ns",  // Mismatch!
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.findById(revisionId) } returns existingRevision
            every { yamlParser.parseRevision(newYaml, true) } returns parsedRevision

            // When/Then
            shouldThrow<InvalidWorkflowRevisionException> {
                useCase.execute("test-ns", "workflow-1", 1, newYaml)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { yamlParser.parseRevision(newYaml, true) }
            verify(exactly = 0) { repository.updateWithSource(any()) }
        }

        scenario("should validate workflow ID match between YAML and path") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val existingRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            // YAML with DIFFERENT id
            val newYaml = """
                namespace: test-ns
                id: different-workflow
                version: 1
                name: Test
                description: Test
                steps:
                  - type: LogTask
                    message: "Test"
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "different-workflow",  // Mismatch!
                version = 1,
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.findById(revisionId) } returns existingRevision
            every { yamlParser.parseRevision(newYaml, true) } returns parsedRevision

            // When/Then
            shouldThrow<InvalidWorkflowRevisionException> {
                useCase.execute("test-ns", "workflow-1", 1, newYaml)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { yamlParser.parseRevision(newYaml, true) }
            verify(exactly = 0) { repository.updateWithSource(any()) }
        }

        scenario("should validate version match between YAML and path") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val existingRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            // YAML with DIFFERENT version
            val newYaml = """
                namespace: test-ns
                id: workflow-1
                version: 2
                name: Test
                description: Test
                steps:
                  - type: LogTask
                    message: "Test"
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,  // Mismatch!
                name = "Test",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.findById(revisionId) } returns existingRevision
            every { yamlParser.parseRevision(newYaml, true) } returns parsedRevision

            // When/Then
            shouldThrow<InvalidWorkflowRevisionException> {
                useCase.execute("test-ns", "workflow-1", 1, newYaml)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { yamlParser.parseRevision(newYaml, true) }
            verify(exactly = 0) { repository.updateWithSource(any()) }
        }

        scenario("should update using WorkflowRevisionID overload") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)

            val existingRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Old Name",
                description = "Old description",
                steps = listOf(LogTask("Old")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val newYaml = """
                namespace: test-ns
                id: workflow-1
                version: 2
                name: Updated
                description: Updated
                steps:
                  - type: LogTask
                    message: "Updated"
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Updated",
                description = "Updated",
                steps = listOf(LogTask("Updated")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.findById(revisionId) } returns existingRevision
            every { yamlParser.parseRevision(newYaml, true) } returns parsedRevision
            every { repository.updateWithSource(any()) } answers { firstArg() }

            // When
            val result = useCase.execute(revisionId, newYaml)

            // Then
            result.toWorkflowRevisionID() shouldBe revisionId
            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { yamlParser.parseRevision(newYaml, true) }
            verify(exactly = 1) { repository.updateWithSource(any()) }
        }
    }
})
