package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.errors.WorkflowNotFoundException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionWithSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CreateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    feature("Create new revision for existing workflow") {

        scenario("should create revision with sequential version number") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = CreateRevisionUseCase(repository, yamlParser, fixedClock)

            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: Test message
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val savedSlot = slot<WorkflowRevisionWithSource>()

            every { repository.exists(WorkflowID("test-ns", "workflow-1")) } returns true
            every { repository.findMaxVersion(WorkflowID("test-ns", "workflow-1")) } returns 1
            every { yamlParser.parseRevision(yaml, false) } returns parsedRevision
            every { repository.saveWithSource(capture(savedSlot)) } answers {
                savedSlot.captured
            }

            // When
            val result = useCase.execute("test-ns", "workflow-1", yaml)

            // Then
            result shouldNotBe null
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 2 // Next version after max (1)

            verify(exactly = 1) { repository.exists(WorkflowID("test-ns", "workflow-1")) }
            verify(exactly = 1) { repository.findMaxVersion(WorkflowID("test-ns", "workflow-1")) }
            verify(exactly = 1) { repository.saveWithSource(any()) }

            savedSlot.captured.revision.version shouldBe 2
            savedSlot.captured.revision.active shouldBe false
            savedSlot.captured.revision.createdAt shouldBe fixedInstant
            savedSlot.captured.revision.updatedAt shouldBe fixedInstant
        }

        scenario("should throw exception when workflow doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = CreateRevisionUseCase(repository, yamlParser, fixedClock)

            val yaml = """
                namespace: test-ns
                id: non-existent
                version: 1
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: Test message
            """.trimIndent()

            every { repository.exists(WorkflowID("test-ns", "non-existent")) } returns false

            // When/Then
            shouldThrow<WorkflowNotFoundException> {
                useCase.execute("test-ns", "non-existent", yaml)
            }

            verify(exactly = 1) { repository.exists(WorkflowID("test-ns", "non-existent")) }
            verify(exactly = 0) { repository.findMaxVersion(any()) }
            verify(exactly = 0) { repository.saveWithSource(any()) }
        }

        scenario("should create version 1 when workflow exists but has no revisions") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = CreateRevisionUseCase(repository, yamlParser, fixedClock)

            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: Test message
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val savedSlot = slot<WorkflowRevisionWithSource>()

            every { repository.exists(WorkflowID("test-ns", "workflow-1")) } returns true
            every { repository.findMaxVersion(WorkflowID("test-ns", "workflow-1")) } returns null
            every { yamlParser.parseRevision(yaml, false) } returns parsedRevision
            every { repository.saveWithSource(capture(savedSlot)) } answers {
                savedSlot.captured
            }

            // When
            val result = useCase.execute("test-ns", "workflow-1", yaml)

            // Then
            result.version shouldBe 1 // First version when max is null

            savedSlot.captured.revision.version shouldBe 1
        }

        scenario("should override namespace/id from YAML with path parameters") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = CreateRevisionUseCase(repository, yamlParser, fixedClock)

            // YAML has different namespace/id than path parameters
            val yaml = """
                namespace: different-ns
                id: different-id
                version: 1
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: Test message
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "different-ns",
                id = "different-id",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val savedSlot = slot<WorkflowRevisionWithSource>()

            every { repository.exists(WorkflowID("test-ns", "workflow-1")) } returns true
            every { repository.findMaxVersion(WorkflowID("test-ns", "workflow-1")) } returns 2
            every { yamlParser.parseRevision(yaml, false) } returns parsedRevision
            every { repository.saveWithSource(capture(savedSlot)) } answers {
                savedSlot.captured
            }

            // When
            val result = useCase.execute("test-ns", "workflow-1", yaml)

            // Then
            result.namespace shouldBe "test-ns" // From path, not YAML
            result.id shouldBe "workflow-1" // From path, not YAML

            savedSlot.captured.revision.namespace shouldBe "test-ns"
            savedSlot.captured.revision.id shouldBe "workflow-1"
        }

        scenario("should always create revision with active=false") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = CreateRevisionUseCase(repository, yamlParser, fixedClock)

            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Test Workflow
                description: Test description
                steps:
                  - type: LogTask
                    message: Test message
                active: true
            """.trimIndent()

            val parsedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = true, // YAML says active=true
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val savedSlot = slot<WorkflowRevisionWithSource>()

            every { repository.exists(WorkflowID("test-ns", "workflow-1")) } returns true
            every { repository.findMaxVersion(WorkflowID("test-ns", "workflow-1")) } returns 3
            every { yamlParser.parseRevision(yaml, false) } returns parsedRevision
            every { repository.saveWithSource(capture(savedSlot)) } answers {
                savedSlot.captured
            }

            // When
            useCase.execute("test-ns", "workflow-1", yaml)

            // Then
            savedSlot.captured.revision.active shouldBe false // Always false for new revisions
        }
    }
})
